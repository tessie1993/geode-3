#include "audio/capture/MicPcm.hpp"

#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>

namespace {
using namespace geode::audio::capture;

void expectNear(float actual, float expected, float tolerance, const char* message) {
    if (!std::isfinite(actual) || std::fabs(actual - expected) > tolerance) {
        throw std::runtime_error(message);
    }
}

void expectEqual(int actual, int expected, const char* message) {
    if (actual != expected) throw std::runtime_error(message);
}

void sixteenBitScalesByTheFullRange() {
    const std::vector<int16_t> source{0, 16384, -16384, 32767, -32768};
    std::vector<float> out(source.size());
    i16ToFloat(source.data(), out.data(), source.size());
    expectNear(out[0], 0.0f, 0.0f, "zero must stay zero");
    expectNear(out[1], 0.5f, 0.0f, "half scale must be exactly 0.5");
    expectNear(out[2], -0.5f, 0.0f, "negative half scale must be exactly -0.5");
    expectNear(out[3], 32767.0f / 32768.0f, 0.0f, "positive full scale must stay below 1");
    expectNear(out[4], -1.0f, 0.0f, "most negative sample must be exactly -1");
}

void sixteenBitConvertsOnlyTheRequestedCount() {
    const std::vector<int16_t> source{16384, 16384, 16384};
    std::vector<float> out(3, 9.0f);
    i16ToFloat(source.data(), out.data(), 2);
    expectNear(out[1], 0.5f, 0.0f, "the second sample must convert");
    expectNear(out[2], 9.0f, 0.0f, "samples past the count must be left alone");
}

void stereoDownmixAveragesEachFrame() {
    const std::vector<float> stereo{1.0f, -1.0f, 0.5f, 0.25f, 0.0f, 1.0f};
    std::vector<float> mono(3);
    downmixToMono(stereo.data(), mono.data(), 3, 2);
    expectNear(mono[0], 0.0f, 1e-7f, "opposite channels must cancel");
    expectNear(mono[1], 0.375f, 1e-7f, "the frame mean must be the mono sample");
    expectNear(mono[2], 0.5f, 1e-7f, "one silent channel must halve the other");
}

void threeChannelDownmixAveragesAllThree() {
    const std::vector<float> frames{0.3f, 0.6f, 0.9f, -0.3f, -0.3f, 0.0f};
    std::vector<float> mono(2);
    downmixToMono(frames.data(), mono.data(), 2, 3);
    expectNear(mono[0], 0.6f, 1e-6f, "three channels must average to 0.6");
    expectNear(mono[1], -0.2f, 1e-6f, "three channels must average to -0.2");
}

void monoDownmixIsACopy() {
    const std::vector<float> source{0.1f, -0.2f, 0.3f};
    std::vector<float> out(3, 0.0f);
    downmixToMono(source.data(), out.data(), 3, 1);
    for (size_t i = 0; i < source.size(); ++i) expectNear(out[i], source[i], 0.0f, "mono must copy unchanged");
    downmixToMono(source.data(), out.data(), 3, 0);
    for (size_t i = 0; i < source.size(); ++i) expectNear(out[i], source[i], 0.0f, "no channels must copy as mono");
}

void downmixWorksInPlace() {
    std::vector<float> buffer{1.0f, 0.0f, 0.5f, 0.5f, -1.0f, 1.0f, 0.2f, 0.4f};
    downmixToMono(buffer.data(), buffer.data(), 4, 2);
    expectNear(buffer[0], 0.5f, 1e-7f, "first in-place frame");
    expectNear(buffer[1], 0.5f, 1e-7f, "second in-place frame");
    expectNear(buffer[2], 0.0f, 1e-7f, "third in-place frame");
    expectNear(buffer[3], 0.3f, 1e-6f, "fourth in-place frame");
}

void zeroFramesTouchNothing() {
    const std::vector<float> source{1.0f, 1.0f};
    std::vector<float> out(1, 7.0f);
    downmixToMono(source.data(), out.data(), 0, 2);
    expectNear(out[0], 7.0f, 0.0f, "no frames must write nothing");
}

void peakIsTheLargestMagnitude() {
    const std::vector<float> samples{0.1f, -0.7f, 0.3f};
    expectNear(peakAbs(samples.data(), samples.size()), 0.7f, 0.0f, "the negative sample is the peak");
    expectNear(peakAbs(samples.data(), 1), 0.1f, 0.0f, "only the counted samples are scanned");
    expectNear(peakAbs(samples.data(), 0), 0.0f, 0.0f, "an empty block has no peak");
}

void peakIgnoresNaN() {
    const float nan = std::numeric_limits<float>::quiet_NaN();
    const std::vector<float> samples{nan, 0.2f, nan};
    expectNear(peakAbs(samples.data(), samples.size()), 0.2f, 0.0f, "NaN must not become the peak");
}

void bufferIsTwiceTheBurstWithinCapacity() {
    expectEqual(bufferFramesFor(240, 960), 480, "two bursts when the capacity allows it");
    expectEqual(bufferFramesFor(240, 480), 480, "exactly the capacity is allowed");
    expectEqual(bufferFramesFor(240, 300), 300, "capacity caps the request");
    expectEqual(bufferFramesFor(240, 0), 480, "unknown capacity does not cap");
    expectEqual(bufferFramesFor(0, 960), 0, "unknown burst requests nothing");
    expectEqual(bufferFramesFor(-1, 960), 0, "negative burst requests nothing");
}

void reopenBackoffDoublesToOneSecond() {
    expectEqual(reopenBackoffMs(0), 0, "the first attempt is immediate");
    expectEqual(reopenBackoffMs(-3), 0, "negative failures are immediate");
    expectEqual(reopenBackoffMs(1), 100, "first retry");
    expectEqual(reopenBackoffMs(2), 200, "second retry");
    expectEqual(reopenBackoffMs(3), 400, "third retry");
    expectEqual(reopenBackoffMs(4), 800, "fourth retry");
    expectEqual(reopenBackoffMs(5), 1000, "fifth retry is capped");
    expectEqual(reopenBackoffMs(1000), 1000, "the cap holds");
}

void reopenBudgetSpansSecondsNotMinutes() {
    int totalMs = 0;
    for (int failures = 1; failures <= kMaxReopenAttempts; ++failures) totalMs += reopenBackoffMs(failures);
    if (totalMs < 5000 || totalMs > 15000) throw std::runtime_error("the reopen budget must be 5 to 15 seconds");
}
}  // namespace

int main() {
    try {
        sixteenBitScalesByTheFullRange();
        sixteenBitConvertsOnlyTheRequestedCount();
        stereoDownmixAveragesEachFrame();
        threeChannelDownmixAveragesAllThree();
        monoDownmixIsACopy();
        downmixWorksInPlace();
        zeroFramesTouchNothing();
        peakIsTheLargestMagnitude();
        peakIgnoresNaN();
        bufferIsTwiceTheBurstWithinCapacity();
        reopenBackoffDoublesToOneSecond();
        reopenBudgetSpansSecondsNotMinutes();
        std::cout << "MicPcm tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
