#include "viz/PcmDelivery.hpp"

#include <algorithm>
#include <array>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>

namespace {
using geode::viz::FramePcm;
using geode::viz::MilkdropPcm;
using geode::viz::PcmBuffer;
using geode::viz::PcmView;

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

std::vector<float> copy(PcmView pcm) {
    return pcm.count > 0 ? std::vector<float>(pcm.data, pcm.data + pcm.count) : std::vector<float>{};
}

void frameSnapshotIsConsumedOnceAndSharedByTransitionScenes() {
    FramePcm pcm;
    const float first[] = {0.1f, 0.2f, 0.3f};
    const float next[] = {0.4f, 0.5f};
    pcm.push(first, 3);
    pcm.beginFrame();
    const auto outgoing = copy(pcm.view(false, nullptr, 0));
    // A producer update between outgoing and active draw must affect neither
    // the active scene's current frame nor an already captured scene view.
    pcm.push(next, 2);
    const auto active = copy(pcm.view(false, nullptr, 0));
    require(outgoing == std::vector<float>(first, first + 3), "outgoing scene lost fresh PCM");
    require(active == outgoing, "transition scenes observed different audio in one frame");
    require(copy(pcm.view(false, nullptr, 0)) == active, "layer fanout consumed another scene's PCM");
    pcm.beginFrame();
    require(copy(pcm.view(false, nullptr, 0)) == std::vector<float>(next, next + 2),
            "producer input after snapshot did not reach the next frame");
    pcm.beginFrame();
    require(pcm.view(false, nullptr, 0).count == 0, "paused producer replayed its last chunk");
}

void boundedQueuePreservesOrderAndDropsOnlyOldestOverflow() {
    PcmBuffer<5> pcm;
    const float first[] = {1, 2, 3};
    const float second[] = {4, 5, 6};
    pcm.push(first, 3);
    pcm.push(second, 3);
    require(copy(pcm.view()) == std::vector<float>({2, 3, 4, 5, 6}),
            "overflow must retain newest contiguous samples");
    const float oversized[] = {7, 8, 9, 10, 11, 12, 13};
    pcm.push(oversized, 7);
    require(copy(pcm.view()) == std::vector<float>({9, 10, 11, 12, 13}),
            "oversized chunk did not retain its newest suffix");
    pcm.push(nullptr, 3);
    pcm.push(first, -1);
    require(copy(pcm.view()) == std::vector<float>({9, 10, 11, 12, 13}),
            "invalid empty input changed queued audio");
    pcm.clear();
    require(pcm.view().count == 0, "reset left queued samples visible");
    pcm.push(first, 3);
    require(copy(pcm.view()) == std::vector<float>({1, 2, 3}), "reset polluted the new session");
}

void timelineApproximationRequiresExplicitAdmissionAndYieldsToRealPcm() {
    FramePcm pcm;
    const float waveform[] = {0.7f, -0.7f};
    pcm.beginFrame();
    require(pcm.view(false, waveform, 2).count == 0, "live cached waveform became new PCM");
    require(copy(pcm.view(true, waveform, 2)) == std::vector<float>({0.7f, -0.7f}),
            "explicit export waveform approximation was lost");
    const float decoded[] = {0.2f};
    pcm.push(decoded, 1);
    pcm.beginFrame();
    require(copy(pcm.view(true, waveform, 2)) == std::vector<float>({0.2f}),
            "timeline approximation overrode decoded PCM");
    pcm.clear();
    pcm.beginFrame();
    require(pcm.view(false, waveform, 2).count == 0, "surface recovery replayed old PCM");
}

struct EngineSink {
    std::vector<float> samples;
    std::vector<unsigned int> chunkSizes;

    void operator()(const float* data, unsigned int count) {
        samples.insert(samples.end(), data, data + count);
        chunkSizes.push_back(count);
    }

    void clear() {
        samples.clear();
        chunkSizes.clear();
    }
};

void milkdropReceivesAllFreshSamplesInSupportedChunks() {
    MilkdropPcm pcm;
    std::vector<float> fresh(2400);
    for (size_t i = 0; i < fresh.size(); ++i) fresh[i] = static_cast<float>(i % 100) / 100.0f;
    pcm.push(fresh.data(), static_cast<int>(fresh.size()));
    pcm.advance(1.0f / 60.0f);
    EngineSink sink;
    pcm.submit(1024, sink);
    require(sink.samples == fresh, "MilkDrop truncated fresh input before submitting it");
    require(sink.chunkSizes == std::vector<unsigned int>({1024, 1024, 352}),
            "PCM exceeded the queried projectM input capacity");
    sink.clear();
    pcm.submit(1024, sink);
    require(sink.samples.empty(), "MilkDrop submitted a consumed chunk twice");
}

void shortCadenceGapsDoNotFlushAndAStallClearsTheWholeEngineWindow() {
    MilkdropPcm pcm;
    EngineSink sink;
    const float fresh[] = {0.5f, -0.5f};
    pcm.push(fresh, 2);
    pcm.advance(0.016f);
    pcm.submit(1024, sink);
    sink.clear();
    pcm.advance(0.04f);
    pcm.submit(1024, sink);
    require(sink.samples.empty(), "short display/producer cadence gap injected silence");
    pcm.advance(0.061f);
    pcm.submit(1024, sink);
    require(sink.samples.size() == 1024, "stall did not clear the entire retained FFT window");
    require(std::all_of(sink.samples.begin(), sink.samples.end(), [](float x) { return x == 0.0f; }),
            "stall replayed nonzero samples as silence");
    sink.clear();
    pcm.advance(1.0f);
    pcm.submit(1024, sink);
    require(sink.samples.empty(), "silence window was repeatedly queued while paused");
    pcm.push(fresh, 2);
    pcm.advance(0.016f);
    pcm.submit(1024, sink);
    require(sink.samples == std::vector<float>({0.5f, -0.5f}), "resume failed to deliver new audio");
}

void unavailableEngineDoesNotReplayExpiredAudioAndResetDropsPending() {
    MilkdropPcm pcm;
    EngineSink sink;
    const float old[] = {0.9f};
    pcm.push(old, 1);
    pcm.advance(0.016f);
    pcm.submit(0, sink);
    require(sink.samples.empty(), "zero engine capacity attempted a submission");
    pcm.advance(0.2f);
    pcm.submit(1536, sink);
    require(sink.samples.size() == 1536 &&
                std::all_of(sink.samples.begin(), sink.samples.end(), [](float x) { return x == 0.0f; }),
            "engine recovery submitted expired queued audio");
    sink.clear();
    pcm.push(old, 1);
    pcm.clear();
    pcm.advance(std::numeric_limits<float>::quiet_NaN());
    pcm.advance(-1.0f);
    pcm.submit(1024, sink);
    require(sink.samples.empty(), "reset or invalid elapsed time resurrected old audio");
}

void silenceFlushCoversPinnedProjectmRetentionBeyondItsReportedMaximum() {
    // projectM 4.1.7 reports 480 samples through the C API but retains 576 in
    // its input ring. A 480-sample flush alone leaves audible-era data behind.
    std::array<float, 576> retained{};
    size_t cursor = 0;
    auto sink = [&](const float* samples, unsigned int count) {
        require(count <= 480, "silence submission exceeded the C API's reported maximum");
        for (unsigned int i = 0; i < count; ++i) {
            retained[cursor] = samples[i];
            cursor = (cursor + 1) % retained.size();
        }
    };
    MilkdropPcm pcm;
    std::array<float, 576> fresh;
    fresh.fill(0.8f);
    pcm.push(fresh.data(), static_cast<int>(fresh.size()));
    pcm.advance(0.016f);
    pcm.submit(480, sink);
    require(std::all_of(retained.begin(), retained.end(), [](float x) { return x == 0.8f; }),
            "regression fixture did not fill the retained audio window");
    pcm.advance(0.2f);
    pcm.submit(480, sink);
    require(std::all_of(retained.begin(), retained.end(), [](float x) { return x == 0.0f; }),
            "silence cleared only projectM's reported 480 samples, leaving stale input");
}

void silenceGraceUsesElapsedTimeAtDifferentDisplayRates() {
    for (int fps : {30, 60, 120}) {
        MilkdropPcm pcm;
        EngineSink sink;
        const float fresh[] = {0.4f};
        const float dt = 1.0f / static_cast<float>(fps);
        pcm.push(fresh, 1);
        pcm.advance(dt);
        pcm.submit(480, sink);
        sink.clear();
        float elapsed = 0.0f;
        while (sink.samples.empty() && elapsed < 0.5f) {
            pcm.advance(dt);
            elapsed += dt;
            pcm.submit(480, sink);
        }
        require(elapsed >= MilkdropPcm::kNoInputGraceSeconds - 1e-5f &&
                    elapsed <= MilkdropPcm::kNoInputGraceSeconds + dt + 1e-5f,
                "silence grace changed with the display frame rate");
        require(sink.samples.size() == 576, "cadence-dependent stall failed to clear retained input");
    }
}

}  // namespace

int main() {
    try {
        frameSnapshotIsConsumedOnceAndSharedByTransitionScenes();
        boundedQueuePreservesOrderAndDropsOnlyOldestOverflow();
        timelineApproximationRequiresExplicitAdmissionAndYieldsToRealPcm();
        milkdropReceivesAllFreshSamplesInSupportedChunks();
        shortCadenceGapsDoNotFlushAndAStallClearsTheWholeEngineWindow();
        unavailableEngineDoesNotReplayExpiredAudioAndResetDropsPending();
        silenceFlushCoversPinnedProjectmRetentionBeyondItsReportedMaximum();
        silenceGraceUsesElapsedTimeAtDifferentDisplayRates();
        std::cout << "PCM delivery tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
