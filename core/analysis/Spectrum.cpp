#include "analysis/Spectrum.hpp"

#include <cmath>

namespace geode::analysis {

Spectrum::Spectrum(int fftSize)
    : fftSize_(fftSize),
      cfg_(kiss_fftr_alloc(fftSize, 0, nullptr, nullptr)),
      bins_(static_cast<size_t>(fftSize / 2 + 1)),
      magnitudes_(static_cast<size_t>(fftSize / 2 + 1)) {}

void Spectrum::compute(const float* windowed) {
    kiss_fftr(cfg_.get(), windowed, bins_.data());
    magnitudes_[0] = std::fabs(bins_[0].r);
    magnitudes_[fftSize_ / 2] = std::fabs(bins_[fftSize_ / 2].r);
    for (int k = 1; k < fftSize_ / 2; k++) {
        const float re = bins_[k].r;
        const float im = bins_[k].i;
        magnitudes_[k] = std::sqrt(re * re + im * im);
    }
}

int Spectrum::peakBin() const {
    int best = 1;
    for (size_t k = 2; k < magnitudes_.size(); k++) {
        if (magnitudes_[k] > magnitudes_[best]) best = static_cast<int>(k);
    }
    return best;
}

void Spectrum::addPower(const float* windowed) {
    kiss_fftr(cfg_.get(), windowed, bins_.data());
    for (size_t k = 0; k < magnitudes_.size(); k++) {
        const float re = bins_[k].r;
        const float im = bins_[k].i;
        const float previous = magnitudes_[k];
        magnitudes_[k] = std::sqrt(previous * previous + re * re + im * im);
    }
}

}  // namespace geode::analysis
