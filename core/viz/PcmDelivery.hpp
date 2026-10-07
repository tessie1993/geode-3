#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstring>

namespace geode::viz {

struct PcmView {
    const float* data = nullptr;
    int count = 0;
};

// Bounded newest-audio queue. No allocation occurs on push or consume. The
// caller owns synchronization; Renderer uses its existing state mutex.
template <size_t Capacity>
class PcmBuffer {
public:
    static_assert(Capacity > 0);

    void push(const float* samples, int count) {
        if (!samples || count <= 0) return;
        const size_t incoming = std::min(static_cast<size_t>(count), Capacity);
        const size_t keep = std::min(count_, Capacity - incoming);
        if (keep < count_) {
            std::memmove(samples_.data(), samples_.data() + count_ - keep, keep * sizeof(float));
        }
        std::copy_n(samples + count - incoming, incoming, samples_.data() + keep);
        count_ = keep + incoming;
    }

    PcmView view() const { return {samples_.data(), static_cast<int>(count_)}; }
    void clear() { count_ = 0; }

private:
    std::array<float, Capacity> samples_{};
    size_t count_ = 0;
};

// One producer snapshot per rendered frame, shared without consuming it again
// by the active scene and its outgoing/layer scene. Pushes after beginFrame()
// affect the next frame only, even if they arrive between the two scene draws.
class FramePcm {
public:
    static constexpr size_t kCapacity = 4096;

    void push(const float* samples, int count) { pending_.push(samples, count); }

    void beginFrame() {
        frame_.clear();
        const auto pending = pending_.view();
        frame_.push(pending.data, pending.count);
        pending_.clear();
    }

    PcmView view(bool allowTimelineWaveform, const float* waveform, int waveformCount) const {
        const auto frame = frame_.view();
        if (frame.count > 0 || !allowTimelineWaveform || !waveform || waveformCount <= 0) return frame;
        return {waveform, waveformCount};
    }

    void clear() {
        pending_.clear();
        frame_.clear();
    }

private:
    PcmBuffer<kCapacity> pending_;
    PcmBuffer<kCapacity> frame_;
};

// projectM retains its last PCM window when no samples arrive. Wait through
// short producer/display cadence gaps, then request one complete silence
// window. This is a bounded no-input policy, not proof of source pause/epoch:
// the current native API carries neither. It must never resubmit old audio.
class MilkdropPcm {
public:
    static constexpr size_t kCapacity = 8192;
    static constexpr float kNoInputGraceSeconds = 0.1f;
    // projectM v4.1.7 Audio/AudioConstants.hpp retains 576 input samples,
    // but its C wrapper reports WaveformSamples (480) from get_max_samples().
    // Verify this compatibility invariant when updating the pinned engine.
    static constexpr unsigned int kRetainedSamples = 576;

    void push(const float* samples, int count) {
        if (!samples || count <= 0) return;
        pending_.push(samples, count);
        received_ = true;
        silenceSent_ = false;
    }

    void advance(float dt) {
        if (received_) {
            idleSeconds_ = 0.0f;
            received_ = false;
        } else if (std::isfinite(dt) && dt > 0.0f) {
            idleSeconds_ = std::min(kNoInputGraceSeconds, idleSeconds_ + dt);
        }
        // Also discard queued input if engine creation/rendering was unavailable
        // for the entire grace period. Recovery must not play an old chunk.
        if (idleSeconds_ >= kNoInputGraceSeconds) pending_.clear();
    }

    template <typename Sink>
    void submit(unsigned int maximumSamples, Sink&& sink) {
        if (maximumSamples == 0) return;
        const auto pcm = pending_.view();
        if (pcm.count > 0) {
            size_t offset = 0;
            while (offset < static_cast<size_t>(pcm.count)) {
                const auto count = static_cast<unsigned int>(
                    std::min(static_cast<size_t>(pcm.count) - offset, static_cast<size_t>(maximumSamples)));
                sink(pcm.data + offset, count);
                offset += count;
            }
            pending_.clear();
        } else if (idleSeconds_ >= kNoInputGraceSeconds && !silenceSent_) {
            // The C API's submission limit is smaller than v4.1.7's retained
            // input ring. Clear the whole ring in supported submission chunks.
            static constexpr std::array<float, 256> silence{};
            unsigned int left = std::max(maximumSamples, kRetainedSamples);
            while (left > 0) {
                const auto count = std::min({left, maximumSamples, static_cast<unsigned int>(silence.size())});
                sink(silence.data(), count);
                left -= count;
            }
            silenceSent_ = true;
        }
    }

    void clear() {
        pending_.clear();
        idleSeconds_ = 0.0f;
        received_ = false;
        silenceSent_ = false;
    }

private:
    PcmBuffer<kCapacity> pending_;
    float idleSeconds_ = 0.0f;
    bool received_ = false;
    bool silenceSent_ = false;
};

}  // namespace geode::viz
