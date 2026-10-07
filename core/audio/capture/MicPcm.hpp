#pragma once
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

// Pure sample-format and sizing helpers for the microphone stream. They include no Android header, so
// core/tests builds them on the host.
namespace geode::audio::capture {

// Stream reopen policy after a disconnect: the first attempt is immediate, the wait before each retry
// doubles from 100 ms up to 1 s, and the stream is given up on after this many failed attempts.
inline constexpr int kMaxReopenAttempts = 12;

// 16-bit PCM to float. The scale is 1/32768, so -32768 maps to exactly -1 and 32767 to just under 1.
inline void i16ToFloat(const int16_t* src, float* dst, size_t count) {
    constexpr float kScale = 1.0f / 32768.0f;
    for (size_t i = 0; i < count; ++i) dst[i] = static_cast<float>(src[i]) * kScale;
}

// Interleaved frames to mono: the mean of each frame's channels. One channel is a plain copy. `mono` may
// be the same buffer as `interleaved`: frame f is fully read before mono[f] is written, and mono[f] sits
// below the first sample of frame f + 1.
inline void downmixToMono(const float* interleaved, float* mono, size_t frames, int channels) {
    if (channels <= 1) {
        if (mono != interleaved) std::copy(interleaved, interleaved + frames, mono);
        return;
    }
    const size_t stride = static_cast<size_t>(channels);
    const float inverse = 1.0f / static_cast<float>(channels);
    for (size_t f = 0; f < frames; ++f) {
        const float* frame = interleaved + f * stride;
        float sum = 0.0f;
        for (size_t c = 0; c < stride; ++c) sum += frame[c];
        mono[f] = sum * inverse;
    }
}

// The largest absolute sample; a NaN sample is ignored.
inline float peakAbs(const float* samples, size_t count) {
    float peak = 0.0f;
    for (size_t i = 0; i < count; ++i) {
        const float magnitude = std::fabs(samples[i]);
        if (magnitude > peak) peak = magnitude;
    }
    return peak;
}

// The input buffer size to request: twice the burst, no more than the buffer capacity. Input data is
// delivered as it is captured, so the extra burst only absorbs the reader being late. 0 when the burst
// is unknown.
inline int bufferFramesFor(int framesPerBurst, int capacityFrames) {
    if (framesPerBurst <= 0) return 0;
    const int wanted = framesPerBurst * 2;
    return capacityFrames > 0 && capacityFrames < wanted ? capacityFrames : wanted;
}

// How long to wait before reopen attempt number failures + 1: 0, 100, 200, 400, 800, then 1000 ms.
inline int reopenBackoffMs(int failures) {
    if (failures <= 0) return 0;
    const int doublings = std::min(failures - 1, 4);
    return std::min(100 << doublings, 1000);
}

}  // namespace geode::audio::capture
