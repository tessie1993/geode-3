#include "analysis/LogBands.hpp"

#include <algorithm>
#include <cmath>

#include "analysis/AdaptiveRange.hpp"

namespace geode::analysis {

LogBands::LogBands(int bandCount, int fftSize, int sampleRateHz, float minHz, float maxHz, float tiltDbPerOctave)
    : bandCount_(bandCount),
      fftSize_(fftSize),
      sampleRateHz_(sampleRateHz),
      minHz_(minHz),
      maxHz_(maxHz),
      tiltDbPerOctave_(tiltDbPerOctave),
      binCount_(fftSize / 2 + 1),
      firstBin_(static_cast<size_t>(bandCount)),
      lastBin_(static_cast<size_t>(bandCount)),
      lowerEdgeHz_(static_cast<size_t>(bandCount)),
      upperEdgeHz_(static_cast<size_t>(bandCount)),
      tiltWeight_(static_cast<size_t>(binCount_)),
      magnitudeScale_(2.0f / fftSize) {
    rebuild();
}

void LogBands::setSampleRateHz(int value) {
    if (value != sampleRateHz_) {
        sampleRateHz_ = value;
        rebuild();
    }
}

void LogBands::energyDb(const float* magnitudes, float* out) const {
    energy(magnitudes, out);
    for (int b = 0; b < bandCount_; b++) {
        const float mean = out[b];
        out[b] = mean <= 0.0f ? AdaptiveRange::kSilenceDb : std::max(10.0f * std::log10(mean), AdaptiveRange::kSilenceDb);
    }
}

void LogBands::energy(const float* magnitudes, float* out) const {
    const float binHz = static_cast<float>(sampleRateHz_) / fftSize_;
    for (int b = 0; b < bandCount_; b++) {
        double power = 0.0;
        double weightSum = 0.0;
        const int from = firstBin_[b];
        const int to = lastBin_[b];
        for (int k = from; k <= to; k++) {
            const float m = magnitudes[k] * magnitudeScale_;
            // An FFT bin covers a frequency interval. Narrow low-frequency
            // log bands share its power instead of being shifted to later bins.
            const float overlap = std::max(0.0f,
                std::min(upperEdgeHz_[b], (k + 0.5f) * binHz) -
                std::max(lowerEdgeHz_[b], (k - 0.5f) * binHz));
            power += static_cast<double>(m) * m * tiltWeight_[k] * overlap;
            weightSum += overlap;
        }
        out[b] = weightSum > 0.0 ? static_cast<float>(power / weightSum) : 0.0f;
    }
}

void LogBands::rebuild() {
    const float nyquist = sampleRateHz_ / 2.0f;
    const float top = std::min(maxHz_, nyquist);
    const float bottom = std::min(minHz_, top * 0.5f);
    const float binHz = static_cast<float>(sampleRateHz_) / fftSize_;

    const double logBottom = std::log(static_cast<double>(bottom));
    const double logTop = std::log(static_cast<double>(top));
    for (int b = 0; b < bandCount_; b++) {
        const float lo = static_cast<float>(std::exp(logBottom + (logTop - logBottom) * b / bandCount_));
        const float hi = static_cast<float>(std::exp(logBottom + (logTop - logBottom) * (b + 1) / bandCount_));
        lowerEdgeHz_[b] = lo;
        upperEdgeHz_[b] = hi;

        const int first = std::clamp(static_cast<int>(std::floor(lo / binHz + 0.5f)), 1, binCount_ - 1);
        const int last = std::clamp(static_cast<int>(std::ceil(hi / binHz - 0.5f)), first, binCount_ - 1);
        firstBin_[b] = first;
        lastBin_[b] = last;
    }

    const float exponent = tiltDbPerOctave_ / kPinkTiltDbPerOctave;
    for (int k = 0; k < binCount_; k++) {
        const float hz = k * binHz;
        tiltWeight_[k] = (exponent == 0.0f || hz <= 0.0f)
            ? 1.0f
            : static_cast<float>(std::pow(static_cast<double>(hz / kTiltReferenceHz), static_cast<double>(exponent)));
    }
}

int LogBands::bandForHz(float hz, int bandCount, int sampleRateHz, float minHz, float maxHz) {
    const float top = std::min(maxHz, sampleRateHz / 2.0f);
    const float bottom = std::min(minHz, top * 0.5f);
    if (hz <= bottom) return 0;
    if (hz >= top) return bandCount - 1;
    const float fraction = std::log(hz / bottom) / std::log(top / bottom);
    return std::clamp(static_cast<int>(fraction * bandCount), 0, bandCount - 1);
}

}  // namespace geode::analysis
