#pragma once
#include <algorithm>
#include <array>
#include <cstring>

namespace geode::viz {

// One producer queue and one immutable GL-frame snapshot. Renderer owns the
// lock: push/beginFrame/reset are called under stateLock_, scene reads only on GL.
class FramePcm {
public:
    // Matches the largest Kotlin renderer provider chunk. Storage is allocated
    // with the renderer, never during a producer push or a display-frame read.
    static constexpr int kCapacity = 4096;

    void push(const float* samples, int count) {
        if (!samples || count <= 0) return;
        const int incoming = std::min(count, kCapacity);
        const int keep = std::min(pendingCount_, kCapacity - incoming);
        // Overflow intentionally drops the oldest audio, bounding visual lag.
        if (keep > 0) {
            std::memmove(pending_.data(), pending_.data() + pendingCount_ - keep,
                         static_cast<size_t>(keep) * sizeof(float));
        }
        std::copy_n(samples + count - incoming, incoming, pending_.data() + keep);
        pendingCount_ = keep + incoming;
    }

    void beginFrame() {
        frameCount_ = pendingCount_;
        std::copy_n(pending_.data(), frameCount_, frame_.data());
        pendingCount_ = 0;
    }

    void reset() {
        pendingCount_ = 0;
        frameCount_ = 0;
    }

    const float* samples() const { return frame_.data(); }
    int count() const { return frameCount_; }

private:
    std::array<float, kCapacity> pending_{};
    std::array<float, kCapacity> frame_{};
    int pendingCount_ = 0;
    int frameCount_ = 0;
};

}  // namespace geode::viz
