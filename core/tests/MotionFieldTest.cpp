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
}  // namespace

int main() {
    try {
        for (int fps : {30, 60, 120}) rotationStaysConstantThroughAngleWraps(fps);
        driftDialAndReducedMotionScaleTheRate();
        sectionChangesEaseTheRotationRate();
        std::cout << "MotionField tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
