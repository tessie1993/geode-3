#include "viz/MotionField.hpp"

#include <cmath>
#include <initializer_list>
#include <iostream>
#include <stdexcept>

#include "viz/VisualSafety.hpp"

namespace {
using geode::viz::MotionField;
using geode::viz::SceneParams;

void expectNear(float actual, float expected, float tolerance, const char* message) {
    if (!std::isfinite(actual) || std::fabs(actual - expected) > tolerance) {
        throw std::runtime_error(message);
    }
}

GeodeFeatureFrame steadyMusic() {
    GeodeFeatureFrame frame{};
    frame.rms = 1.0f;
    frame.bass = 1.0f;
    frame.mid = 1.0f;
    frame.treble = 1.0f;
    return frame;
}

void rotationStaysConstantThroughAngleWraps(int framesPerSecond) {
    MotionField motion;
    SceneParams params;
    params.rotation = 0.25f;
    params.motionDrift = 1.0f;
    const auto frame = steadyMusic();
    const float dt = 1.0f / static_cast<float>(framesPerSecond);
    float previousAngle = 0.0f;
    int wraps = 0;

    // Four minutes crosses the shader angle's wrap several times. The former
    // angle-as-rate implementation accelerated, hit the clamp, then reversed.
    for (int i = 0; i < 240 * framesPerSecond; ++i) {
        motion.step(frame, dt);
        expectNear(motion.apply(params, false).rotation, 0.35f, 1e-5f,
                   "steady music must produce a steady rotation velocity");
        if (motion.state().drift < previousAngle) ++wraps;
        previousAngle = motion.state().drift;
    }
    if (wraps < 2) throw std::runtime_error("test must cross multiple angle wraps");
}

void driftDialAndReducedMotionScaleTheRate() {
    MotionField motion;
    motion.step(steadyMusic(), 1.0f / 60.0f);
    SceneParams params;
    params.rotation = -0.4f;
    params.motionDrift = 0.5f;
    expectNear(motion.apply(params, false).rotation, -0.35f, 1e-5f,
               "drift dial must scale radians per second");
    expectNear(motion.apply(params, true).rotation,
               params.rotation + 0.05f * geode::viz::safety::kReducedMotionScale, 1e-5f,
               "reduced motion must scale the drift velocity");
    params.motionDrift = 0.0f;
    expectNear(motion.apply(params, false).rotation, params.rotation, 1e-5f,
               "zero drift must preserve the requested rotation");
    motion.reset();
    expectNear(motion.state().driftRate, 0.0f, 1e-5f, "reset must clear drift velocity");
}

void sectionChangesEaseTheRotationRate() {
    MotionField motion;
    auto frame = steadyMusic();
    SceneParams params;
    params.rotation = 0.0f;
    params.motionDrift = 1.0f;
    const float dt = 1.0f / 60.0f;
    motion.step(frame, dt);
    float previousRate = motion.apply(params, false).rotation;

    for (int i = 0; i < 1200; ++i) {
        frame.sectionBoundary = i % 120 == 0 ? 1.0f : 0.0f;
        motion.step(frame, dt);
        const float rate = motion.apply(params, false).rotation;
        expectNear(rate, previousRate, 0.002f,
                   "section boundaries must ease rotation without snapping");
        previousRate = rate;
    }
}

void zeroSpeedAndZeroAudioDriveAreRespected() {
    MotionField motion;
    motion.step(steadyMusic(), 1.0f / 60.0f);
    SceneParams params;
    params.speed = 0.0f;
    expectNear(motion.apply(params, false).speed, 0.0f, 0.0f,
               "zero scene speed must not be raised by audio modulation");
    params.audioDrive = 0.0f;
    params.speed = 0.7f;
    params.motionDrift = 0.0f;
    params.motionAmount = 1.0f;
    params.motionBreath = 1.0f;
    params.motionHue = 1.0f;
    const auto quiet = motion.apply(params, false);
    auto frame = steadyMusic();
    frame.rms = 0.1f;
    frame.bass = 1.5f;
    frame.mid = 0.0f;
    frame.treble = 1.5f;
    frame.centroid = 1.0f;
    frame.harmonicity = 1.0f;
    for (int i = 0; i < 120; ++i) motion.step(frame, 1.0f / 60.0f);
    const auto loud = motion.apply(params, false);
    expectNear(loud.speed, params.speed, 1e-6f, "zero audio drive must preserve scene speed");
    expectNear(loud.zoom, quiet.zoom, 1e-6f, "zero audio drive must disable musical breathing");
    expectNear(loud.warp, quiet.warp, 1e-6f, "zero audio drive must disable musical deformation");
    expectNear(loud.fluidCurl, quiet.fluidCurl, 1e-6f, "zero audio drive must disable bass fluid modulation");
    expectNear(loud.colorShift, quiet.colorShift, 1e-6f, "zero audio drive must disable musical hue changes");
}

MotionField::State bandStepResponse(int fps) {
    MotionField motion;
    auto frame = steadyMusic();
    for (int i = 0; i < 4 * fps; ++i) motion.step(frame, 1.0f / fps);
    frame.bass = frame.mid = frame.treble = 1.5f;
    for (int i = 0; i < fps / 10; ++i) motion.step(frame, 1.0f / fps);
    return motion.state();
}

void bandsHaveDistinctTimelyResponses() {
    const auto reference = bandStepResponse(60);
    if (!(reference.trebRel > reference.bassRel && reference.bassRel > reference.midRel)) {
        throw std::runtime_error("high detail, bass mass and mid deformation need distinct attacks");
    }
    if (reference.bassRel < 1.40f || reference.midRel < 1.35f) {
        throw std::runtime_error("musical geometry must respond within 100ms");
    }
    for (int fps : {30, 120}) {
        const auto response = bandStepResponse(fps);
        expectNear(response.bassRel, reference.bassRel, 0.002f, "bass response must be frame-rate independent");
        expectNear(response.midRel, reference.midRel, 0.002f, "mid response must be frame-rate independent");
        expectNear(response.trebRel, reference.trebRel, 0.002f, "treble response must be frame-rate independent");
    }
    MotionField motion;
    for (int i = 0; i < 120; ++i) motion.step(steadyMusic(), 1.0f / 60.0f);
    const GeodeFeatureFrame silence{};
    for (int i = 0; i < 120; ++i) motion.step(silence, 1.0f / 60.0f);
    expectNear(motion.state().bassRel, 0.0f, 0.001f, "bass must settle after silence");
    expectNear(motion.state().midRel, 0.0f, 0.001f, "mid must settle after silence");
    expectNear(motion.state().trebRel, 0.0f, 0.001f, "treble must settle after silence");
}
}  // namespace

int main() {
    try {
        for (int fps : {30, 60, 120}) rotationStaysConstantThroughAngleWraps(fps);
        driftDialAndReducedMotionScaleTheRate();
        sectionChangesEaseTheRotationRate();
        zeroSpeedAndZeroAudioDriveAreRespected();
        bandsHaveDistinctTimelyResponses();
        std::cout << "MotionField tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
