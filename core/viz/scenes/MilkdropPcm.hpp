#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>

#include "api/geode_api.h"

namespace geode::viz {

// Keeps raw live delivery separate from the feature-waveform export fallback.
// projectM's public per-call limit differs from its retained raw PCM history.
class MilkdropPcm {
public:
    static constexpr int kCapacity = 8192;
    // Pinned projectM 4.1.7 AudioConstants.hpp: AudioBufferSamples, not its
    // WaveformSamples (480) or the public maximum per add call.
    static constexpr int kHistorySamples = 576;

    void push(const float* samples, int count) {
        if (!samples || count <= 0) return;
        raw_ = true;
        cleared_ = false;
        const int incoming = std::min(count, kCapacity);
        const int keep = std::min(count_, kCapacity - incoming);
        if (keep > 0) {
            std::memmove(pcm_.data(), pcm_.data() + count_ - keep,
                         static_cast<size_t>(keep) * sizeof(float));
        }
        std::copy_n(samples + count - incoming, incoming, pcm_.data() + keep);
        count_ = keep + incoming;
    }

    template <typename Submit>
    void submit(const GeodeFeatureFrame& features, int maxCallSamples, Submit&& add) {
        const int chunkSize = std::max(maxCallSamples, 1);
        const auto feed = [&](const float* samples, int count) {
            for (int offset = 0; offset < count; offset += chunkSize) {
                add(samples + offset, std::min(chunkSize, count - offset));
            }
        };
        if (count_ > 0) {
            feed(pcm_.data(), count_);
            count_ = 0;
        } else if (!raw_) {
            // Offscreen export and wallpaper can supply features without a
            // raw PCM provider. Preserve that existing rendering contract.
            feed(features.waveform, GEODE_WAVEFORM_POINTS);
        } else if (!cleared_ && silent(features)) {
            // projectM retains its history across renders with no new input.
            // Once analysis expires the source, overwrite that history once.
            feed(silence_.data(), kHistorySamples);
            cleared_ = true;
        }
    }

    void reset() {
        count_ = 0;
        raw_ = false;
        cleared_ = false;
    }

private:
    static bool silent(const GeodeFeatureFrame& features) {
        if (features.rms > 1e-5f) return false;
        for (float sample : features.waveform) {
            if (std::fabs(sample) > 1e-5f) return false;
        }
        return true;
    }

    std::array<float, kCapacity> pcm_{};
    std::array<float, kHistorySamples> silence_{};
    int count_ = 0;
    bool raw_ = false;
    bool cleared_ = false;
};

}  // namespace geode::viz
