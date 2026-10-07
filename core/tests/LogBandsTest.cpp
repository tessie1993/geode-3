#include "analysis/LogBands.hpp"

#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>

int main() {
    constexpr int fftSize = 2048;
    constexpr int bands = 64;
    for (int rate : {44100, 48000, 96000, 192000}) {
        geode::analysis::LogBands filters(bands, fftSize, rate, 30.0f, 16000.0f, 0.0f);
        std::vector<float> spectrum(fftSize / 2 + 1), energy(bands);
        const float binHz = static_cast<float>(rate) / fftSize;
        for (int k = 1; k < fftSize / 2 && k * binHz < 16000.0f; ++k) {
            if (k * binHz < 30.0f) continue;
            std::fill(spectrum.begin(), spectrum.end(), 0.0f);
            spectrum[k] = 1.0f;
            filters.energy(spectrum.data(), energy.data());
            const int declaredBand = geode::analysis::LogBands::bandForHz(k * binHz, bands, rate);
            assert(energy[declaredBand] > 0.0f);
            for (int b = 0; b < bands; ++b) {
                const bool overlaps = filters.upperHz(b) > (k - 0.5f) * binHz &&
                                      filters.lowerHz(b) < (k + 0.5f) * binHz;
                if (!overlaps) assert(energy[b] == 0.0f);
                assert(std::isfinite(energy[b]));
            }
        }
        // Flat input remains flat across narrow and wide bands with tilt disabled.
        std::fill(spectrum.begin(), spectrum.end(), 1.0f);
        filters.energy(spectrum.data(), energy.data());
        const float expected = 4.0f / (fftSize * fftSize);
        for (int b = 0; b < bands; ++b) {
            if (filters.upperHz(b) > binHz * 0.5f) assert(std::abs(energy[b] - expected) < 1e-10f);
        }
    }
    std::cout << "Log filters match declared frequency edges at four sample rates\n";
}
