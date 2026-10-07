#include "viz/fluid/FluidMath.hpp"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include <limits>

namespace {
using namespace geode::viz::fluid;

GeodeFeatureFrame loudFrame() {
    GeodeFeatureFrame f{};
    f.rms = 0.8f;
    f.bass = 0.9f;
    f.mid = 0.6f;
    f.treble = 0.4f;
    f.beat = f.onset = f.downbeat = 1.0f;
    f.beatStrength = 0.8f;
    f.transient = 0.6f;
    f.kick = 0.8f;
    f.snare = 0.6f;
    f.hat = 0.4f;
    f.flux = f.macroEnergy = f.novelty = f.drop = f.arrival = 0.75f;
    f.centroid = f.stereoWidth = f.chromaConfidence = 0.5f;
    f.bpm = 120.0f;
    f.beatPhase = 0.25f;
    for (float& band : f.bands) band = 0.8f;
    for (float& sample : f.waveform) sample = -0.5f;
    for (float& chroma : f.chroma) chroma = 0.7f;
    return f;
}

void mutedAndInvalidControlsRemoveEverySignalAndEvent() {
    const auto source = loudFrame();
    const GeodeFeatureFrame empty{};
    for (float drive : {0.0f, -1.0f, std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::infinity()}) {
        const auto muted = scaledFeatures(source, drive);
        assert(std::memcmp(&muted, &empty, sizeof(empty)) == 0);
        assert(math::driven(1.0f, drive) == 0.0f);
    }
}

void halfGainReducesBodyAndAccentAmplitudes() {
    const auto source = loudFrame();
    const auto half = scaledFeatures(source, 0.5f);
    assert(half.rms == source.rms * 0.5f);
    assert(half.bass == source.bass * 0.5f && half.mid == source.mid * 0.5f && half.treble == source.treble * 0.5f);
    assert(half.beatStrength == source.beatStrength * 0.5f);
    assert(half.transient == source.transient * 0.5f && half.onset == source.onset * 0.5f);
    assert(half.kick == source.kick * 0.5f && half.snare == source.snare * 0.5f && half.hat == source.hat * 0.5f);
    for (float band : half.bands) assert(band == 0.4f);
    assert(half.beat == source.beat && half.bpm == source.bpm && half.beatPhase == source.beatPhase);
    auto legacy = source;
    legacy.beatStrength = 0.0f;
    assert(scaledFeatures(legacy, 0.5f).beatStrength == 0.5f);
}

void defaultGainPreservesTheFrameAndMutedFlowKeepsItsPhysicalBaseline() {
    const auto source = loudFrame();
    const auto unchanged = scaledFeatures(source, 1.0f);
    assert(std::memcmp(&unchanged, &source, sizeof(source)) == 0);
    assert(curl::fieldAmp(0.0f, 0.0f) == curl::kBaseAmp);
    assert(curl::fieldAmp(0.0f, 1.0f) == curl::kBaseAmp);
    const float half = curl::fieldAmp(0.5f, 0.4f);
    const float full = curl::fieldAmp(1.0f, 0.8f);
    assert(half > curl::kBaseAmp && half < full);
    assert(std::fabs(full - curl::kBaseAmp * (1.0f + 0.8f * curl::kBeatAmp)) < 1e-6f);
}
}  // namespace

int main() {
    mutedAndInvalidControlsRemoveEverySignalAndEvent();
    halfGainReducesBodyAndAccentAmplitudes();
    defaultGainPreservesTheFrameAndMutedFlowKeepsItsPhysicalBaseline();
    std::puts("Fluid audio drive tests passed");
}
