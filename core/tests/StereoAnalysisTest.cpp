#include "analysis/AnalysisSession.hpp"

#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>

int main() {
    constexpr int frames = 2048;
    constexpr int rate = 48000;
    constexpr float pi = 3.14159265358979323846f;
    std::vector<float> tone(frames), silence(frames), half(frames);
    for (int i = 0; i < frames; ++i) {
        tone[i] = 0.5f * std::sin(2.0f * pi * 8.0f * i / frames);
        half[i] = tone[i] * 0.5f;
    }
    geode::analysis::AnalysisSession inPhase(rate, frames, 62.5f);
    geode::analysis::AnalysisSession antiPhase(rate, frames, 62.5f);
    geode::analysis::AnalysisSession leftOnly(rate, frames, 62.5f);
    GeodeFeatureFrame reference{}, opposite{}, oneChannel{};
    for (int step = 0; step < 8; ++step) {
        inPhase.analyze(tone.data(), silence.data(), frames, 0.016f, reference);
        antiPhase.analyze(silence.data(), tone.data(), frames, 0.016f, opposite);
        leftOnly.analyze(half.data(), half.data(), frames, 0.016f, oneChannel);
    }
    assert(reference.rms > 0.3f);
    assert(std::abs(reference.rms - opposite.rms) < 1e-5f);
    assert(std::abs(oneChannel.rms * std::sqrt(2.0f) - reference.rms) < 1e-5f);
    assert(reference.stereoCorrelation > 0.99f);
    assert(opposite.stereoCorrelation < -0.99f);
    float peak = 0.0f;
    for (int b = 0; b < GEODE_BAND_COUNT; ++b) {
        assert(std::abs(reference.bands[b] - opposite.bands[b]) < 1e-5f);
        peak = std::max(peak, opposite.bands[b]);
    }
    assert(peak > 0.1f);
    // The signed mid waveform remains mid; it is not rectified to fake loudness.
    for (float sample : opposite.waveform) assert(sample == 0.0f);
    antiPhase.analyze(silence.data(), silence.data(), frames, 0.016f, opposite);
    assert(opposite.rms == 0.0f && opposite.transient == 0.0f);
    for (float band : opposite.bands) assert(band == 0.0f);
    std::cout << "Stereo analysis preserves channel energy and clears silence\n";
}
