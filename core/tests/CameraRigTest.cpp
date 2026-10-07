#include "viz/CameraRig.hpp"

#include <cmath>
#include <initializer_list>
#include <iostream>
#include <stdexcept>

namespace {
using geode::viz::CameraRig;
using geode::viz::SceneParams;
using Vec3 = CameraRig::Vec3;

float dot(Vec3 a, Vec3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
float distance(Vec3 a, Vec3 b) {
    const Vec3 d{a.x - b.x, a.y - b.y, a.z - b.z};
    return std::sqrt(dot(d, d));
}
void expect(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
void expectNear(float actual, float expected, float tolerance, const char* message) {
    expect(std::isfinite(actual) && std::fabs(actual - expected) <= tolerance, message);
}
void basisIsStable(const CameraRig::Frame& frame) {
    for (auto v : {frame.right, frame.up, frame.forward}) {
        expectNear(dot(v, v), 1.0f, 1e-5f, "camera basis must have unit length");
    }
    expectNear(dot(frame.right, frame.up), 0.0f, 1e-5f, "camera basis must be orthogonal");
    expectNear(dot(frame.right, frame.forward), 0.0f, 1e-5f, "camera basis must be orthogonal");
    expectNear(dot(frame.up, frame.forward), 0.0f, 1e-5f, "camera basis must be orthogonal");
    expectNear(frame.right.y, 0.0f, 1e-6f, "camera horizon must not roll on sound");
    expect(frame.up.y > 0.8f, "camera up must remain near world up");
}
CameraRig simulate(CameraRig::Profile profile, int fps) {
    CameraRig rig(profile);
    SceneParams params;
    params.motionOrbit = 0.75f;
    for (int i = 0; i < 24 * fps; ++i) {
        const float signal = i < 3 * fps || i >= 12 * fps ? 0.0f : 1.0f;
        rig.step(signal, signal, params, 1.0f / fps);
        basisIsStable(rig.frame());
    }
    return rig;
}
void sameTrajectoryAtDifferentFrameRates(CameraRig::Profile profile) {
    const auto reference = simulate(profile, 120);
    for (int fps : {30, 60}) {
        const auto rig = simulate(profile, fps);
        expect(distance(rig.frame().position, reference.frame().position) < 0.009f,
               "camera trajectory must remain stable across frame rates");
        expect(distance(rig.frame().forward, reference.frame().forward) < 0.002f,
               "camera aim must remain stable across frame rates");
    }
}
void signalStepCannotThrowCamera(CameraRig::Profile profile) {
    CameraRig rig(profile);
    SceneParams params;
    const float dt = 1.0f / 60.0f;
    for (int i = 0; i < 1800; ++i) {
        const auto previous = rig.frame();
        const float signal = i >= 300 && i < 700 ? 1.5f : 0.0f;
        rig.step(signal, signal, params, dt);
        expect(distance(rig.frame().position, previous.position) < 0.045f,
               "sound must not teleport camera position");
        expect(distance(rig.frame().forward, previous.forward) < 0.015f,
               "sound must not snap camera aim");
        basisIsStable(rig.frame());
    }
    params.speed = 0.0f;
    const auto held = rig.frame();
    const double heldTravel = rig.travelDistance();
    for (int i = 0; i < 600; ++i) rig.step(1.5f, 1.5f, params, dt);
    expect(distance(rig.frame().position, held.position) == 0.0f, "zero speed must hold camera position");
    expect(distance(rig.frame().forward, held.forward) == 0.0f, "zero speed must hold camera aim");
    expect(rig.travelDistance() == heldTravel, "zero speed must hold tunnel travel");
}
void tunnelMatchesSharedPath() {
    for (auto profile : {CameraRig::Profile::RodFlight, CameraRig::Profile::NectarFlight}) {
        CameraRig rig(profile);
        SceneParams params;
        params.motionOrbit = 0.0f;
        for (int i = 0; i < 1200; ++i) {
            rig.step(1.0f, 1.0f, params, 1.0f / 60.0f);
            const double z = rig.travelDistance();
            const Vec3 expected{static_cast<float>(1.4 * std::sin(z * 0.21) + 2.6 * std::sin(z * 0.043 + 1.3)),
                                static_cast<float>(1.2 * std::cos(z * 0.17 + 0.7) + 2.2 * std::cos(z * 0.031)),
                                static_cast<float>(z)};
            expect(distance(rig.frame().position, expected) < 1e-5f,
                   "camera must follow the exact shared shader corridor");
        }
    }
}
void slowCameraResponseIsIndependentOfAccent() {
    CameraRig rig(CameraRig::Profile::BloomOrbit);
    SceneParams params;
    params.speed = 1.0f;
    for (int i = 0; i < 1200; ++i) rig.step(1.0f, 1.0f, params, 1.0f / 60.0f);
    const float radius = std::sqrt(dot(rig.frame().position, rig.frame().position));
    expect(radius >= 2.95f && radius <= 3.1f, "audio dolly must stay bounded");
    for (int i = 0; i < 1200; ++i) rig.step(0.0f, 0.0f, params, 1.0f / 60.0f);
    expectNear(std::sqrt(dot(rig.frame().position, rig.frame().position)), 3.1f, 0.001f,
               "silence must settle the camera dolly");
}
}  // namespace

int main() {
    try {
        for (auto profile : {CameraRig::Profile::CathedralOrbit, CameraRig::Profile::BloomOrbit,
                             CameraRig::Profile::RodFlight, CameraRig::Profile::NectarFlight}) {
            sameTrajectoryAtDifferentFrameRates(profile);
            signalStepCannotThrowCamera(profile);
        }
        tunnelMatchesSharedPath();
        slowCameraResponseIsIndependentOfAccent();
        expect(CameraRig::profileFor("custom") == CameraRig::Profile::Stationary,
               "custom shader scenes must keep their existing camera contract");
        std::cout << "CameraRig tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
