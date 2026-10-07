#include "viz/FramePcm.hpp"

#include <array>
#include <cassert>
#include <cstdio>

namespace {
using geode::viz::FramePcm;

void consumedOnceAfterPause() {
    FramePcm pcm;
    const float source[] = {0.1f, -0.2f, 0.3f, -0.4f};
    pcm.push(source, 4);
    pcm.beginFrame();
    assert(pcm.count() == 4);
    for (int i = 0; i < 4; ++i) assert(pcm.samples()[i] == source[i]);
    // The provider returns no PCM on pause or an inter-chunk gap. Repeated
    // display frames must not resubmit its last block into projectM.
    pcm.beginFrame();
    assert(pcm.count() == 0);
    pcm.beginFrame();
    assert(pcm.count() == 0);
}

void queuedChunksAndAllScenesShareAFrame() {
    FramePcm pcm;
    const float first[] = {1.0f, 2.0f, 3.0f, 4.0f};
    const float second[] = {5.0f, 6.0f};
    pcm.push(first, 4);
    pcm.push(second, 2);
    pcm.beginFrame();
    assert(pcm.count() == 6);
    // Active scene reads this snapshot, then audio arrives before the layer
    // or outgoing scene reads. Every scene in the frame must still get 1..6.
    for (int i = 0; i < 6; ++i) assert(pcm.samples()[i] == i + 1.0f);
    const float next[] = {7.0f, 8.0f};
    pcm.push(next, 2);
    assert(pcm.count() == 6);
    for (int scene = 0; scene < 2; ++scene) {
        for (int i = 0; i < 6; ++i) assert(pcm.samples()[i] == i + 1.0f);
    }
    pcm.beginFrame();
    assert(pcm.count() == 2);
    assert(pcm.samples()[0] == 7.0f);
    assert(pcm.samples()[1] == 8.0f);
}

void boundedOverflowKeepsNewest() {
    FramePcm pcm;
    std::array<float, FramePcm::kCapacity + 3> source{};
    for (size_t i = 0; i < source.size(); ++i) source[i] = static_cast<float>(i);
    pcm.push(source.data(), static_cast<int>(source.size()));
    pcm.beginFrame();
    assert(pcm.count() == FramePcm::kCapacity);
    for (int i = 0; i < pcm.count(); ++i) assert(pcm.samples()[i] == i + 3.0f);

    pcm.push(source.data(), FramePcm::kCapacity);
    const float tail[] = {-1.0f, -2.0f};
    pcm.push(tail, 2);
    pcm.beginFrame();
    assert(pcm.count() == FramePcm::kCapacity);
    for (int i = 0; i < pcm.count() - 2; ++i) assert(pcm.samples()[i] == i + 2.0f);
    assert(pcm.samples()[pcm.count() - 2] == -1.0f);
    assert(pcm.samples()[pcm.count() - 1] == -2.0f);
}

void resetDiscardsPendingAndLatchedAudio() {
    FramePcm pcm;
    const float source[] = {1.0f, 2.0f};
    pcm.push(source, 2);
    pcm.beginFrame();
    pcm.push(source, 2);
    pcm.reset();
    assert(pcm.count() == 0);
    pcm.beginFrame();
    assert(pcm.count() == 0);
    pcm.push(nullptr, 2);
    pcm.push(source, 0);
    pcm.beginFrame();
    assert(pcm.count() == 0);
}
}  // namespace

int main() {
    consumedOnceAfterPause();
    queuedChunksAndAllScenesShareAFrame();
    boundedOverflowKeepsNewest();
    resetDiscardsPendingAndLatchedAudio();
    std::puts("FramePcm tests passed");
}
