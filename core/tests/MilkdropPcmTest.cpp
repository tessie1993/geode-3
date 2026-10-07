#include "viz/scenes/MilkdropPcm.hpp"

#include <array>
#include <cassert>
#include <cstdio>
#include <vector>

namespace {
using geode::viz::MilkdropPcm;
constexpr int kMaxCallSamples = 480;

void fullFreshBatchAndNoReplayOnGap() {
    MilkdropPcm pcm;
    std::array<float, 4096> source{};
    source[0] = 1.0f;
    source.back() = -0.5f;
    pcm.push(source.data(), static_cast<int>(source.size()));
    GeodeFeatureFrame features{};
    features.rms = 0.25f;
    std::vector<float> submitted;
    int calls = 0;
    const auto add = [&](const float* values, int count) {
        assert(count > 0 && count <= kMaxCallSamples);
        submitted.insert(submitted.end(), values, values + count);
        ++calls;
    };
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 9);
    assert(submitted.size() == source.size());
    assert(submitted.front() == 1.0f);
    assert(submitted.back() == -0.5f);
    features.waveform[0] = 0.75f;
    pcm.submit(features, kMaxCallSamples, add);
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 9);
    // Analysis marked a stopped source silent. Clear projectM's retained
    // history once, then leave it alone instead of feeding stale waveforms.
    features = {};
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 11);
    assert(submitted.size() == source.size() + MilkdropPcm::kHistorySamples);
    for (size_t i = source.size(); i < submitted.size(); ++i) assert(submitted[i] == 0.0f);
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 11);
    pcm.push(source.data(), 1);
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 12);
    assert(submitted.back() == 1.0f);
}

void preEngineQueueIsBoundedAndKeepsNewest() {
    MilkdropPcm pcm;
    std::array<float, MilkdropPcm::kCapacity> source{};
    for (size_t i = 0; i < source.size(); ++i) source[i] = static_cast<float>(i);
    pcm.push(source.data(), static_cast<int>(source.size()));
    const float tail[] = {-1.0f, -2.0f};
    pcm.push(tail, 2);
    GeodeFeatureFrame features{};
    std::vector<float> submitted;
    pcm.submit(features, kMaxCallSamples, [&](const float* samples, int count) {
        assert(count <= kMaxCallSamples);
        submitted.insert(submitted.end(), samples, samples + count);
    });
    assert(submitted.size() == MilkdropPcm::kCapacity);
    assert(submitted.front() == 2.0f);
    assert(submitted[submitted.size() - 2] == -1.0f);
    assert(submitted.back() == -2.0f);
}

void featureOnlyExportStillFeedsWaveform() {
    MilkdropPcm pcm;
    GeodeFeatureFrame features{};
    features.waveform[0] = 0.5f;
    int calls = 0;
    const auto add = [&](const float* samples, int count) {
        assert(count == GEODE_WAVEFORM_POINTS);
        assert(samples[0] == 0.5f);
        ++calls;
    };
    pcm.submit(features, kMaxCallSamples, add);
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 2);
    const float raw[] = {1.0f};
    pcm.push(raw, 1);
    pcm.reset();
    pcm.submit(features, kMaxCallSamples, add);
    assert(calls == 3);
}
}  // namespace

int main() {
    fullFreshBatchAndNoReplayOnGap();
    preEngineQueueIsBoundedAndKeepsNewest();
    featureOnlyExportStillFeedsWaveform();
    std::puts("MilkdropPcm tests passed");
}
