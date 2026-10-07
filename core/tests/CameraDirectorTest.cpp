#include "viz/CameraDirector.hpp"

#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace {
using geode::viz::CameraDirector;

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
void advance(CameraDirector& camera, int fps, float seconds, float speed,
             float audio, float motion = 1.0f, float orbit = 1.0f, float drift = 1.0f) {
    for (int i = 0; i < static_cast<int>(seconds * fps); ++i) {
        camera.step(1.0f / fps, speed, audio, audio, motion, orbit, drift);
    }
}

void musicChangesTravelWithoutTeleporting() {
    CameraDirector quiet(7), music(7), noMotion(7);
    const double initial = noMotion.frame().distance;
    advance(quiet, 60, 10, 1, 0);
    advance(music, 60, 10, 1, 1);
    advance(noMotion, 60, 10, 1, 1, 0);
    require(music.frame().distance > quiet.frame().distance + 10.0,
            "real energy/bass must increase forward travel");
    require(noMotion.frame().distance == initial,
            "motion amount zero disables camera travel");
    const auto before = quiet.frame();
    quiet.step(1.0f / 60.0f, 4, 1, 1, 1, 1, 1);
    require(quiet.frame().distance - before.distance < 0.22,
            "speed/energy changes integrate velocity rather than jump distance");
}

void boundedMotionAndIndependentDials() {
    CameraDirector camera(51), noOrbit(51), noDrift(51);
    for (int i = 0; i < 60 * 600; ++i) {
        const auto before = camera.frame();
        const float signal = (i / 120) % 2 ? 1.0f : 0.0f;
        camera.step(1.0f / 60.0f, 1, signal, signal, 1, 1, 1);
        const auto& frame = camera.frame();
        require(std::abs(frame.x) <= CameraDirector::kOffsetLimit &&
                std::abs(frame.y) <= CameraDirector::kOffsetLimit &&
                std::abs(frame.roll) <= CameraDirector::kRollLimit,
                "generative path must stay inside corridor/bank bounds");
        require(std::abs(frame.x - before.x) < 0.007f &&
                std::abs(frame.roll - before.roll) < 0.0031f,
                "retargets must stay smooth");
    }
    advance(noOrbit, 60, 20, 1, 1, 1, 0, 1);
    advance(noDrift, 60, 20, 1, 1, 1, 1, 0);
    require(noOrbit.frame().x == 0 && noOrbit.frame().y == 0,
            "orbit zero must disable lateral travel");
    require(noDrift.frame().roll == 0, "drift zero must disable camera bank");
    require(std::abs(noOrbit.frame().roll) > 0.0001f,
            "orbit dial must not disable independent bank");
    require(std::abs(noDrift.frame().x) > 0.0001f,
            "drift dial must not disable independent orbit");
}

void reducedSpeedAndPauseApplyToTheWholeRig() {
    CameraDirector regular(12), reduced(12);
    const double initial = regular.frame().distance;
    advance(regular, 60, 1, 1, 0);
    advance(reduced, 60, 1, 0.4f, 0);
    require(std::abs((reduced.frame().distance - initial) /
                     (regular.frame().distance - initial) - 0.4) < 0.00001,
            "safety-scaled speed must reduce travel");
    require(std::abs(reduced.frame().x) < std::abs(regular.frame().x),
            "safety-scaled speed must also slow lateral movement");
    const auto before = reduced.frame();
    advance(reduced, 60, 5, 0, 1);
    require(reduced.frame().distance == before.distance && reduced.frame().x == before.x &&
            reduced.frame().roll == before.roll, "speed zero must freeze the whole rig");
}

void invalidFramesAndResumeDoNotCorruptPose() {
    CameraDirector camera(13);
    const double before = camera.frame().distance;
    camera.step(std::numeric_limits<float>::quiet_NaN(), 1, 1, 1, 1, 1, 1);
    camera.step(-1, 1, 1, 1, 1, 1, 1);
    require(camera.frame().distance == before, "invalid dt must not move camera");
    camera.step(100, 1, 1, 1, 1, 1, 1);
    require(camera.frame().distance - before <= 0.321, "resume must not replay elapsed suspension");
    const float nan = std::numeric_limits<float>::quiet_NaN();
    camera.step(0.016f, 1, nan, nan, nan, nan, nan);
    require(std::isfinite(camera.frame().distance) && std::isfinite(camera.frame().x) &&
            std::isfinite(camera.frame().roll), "invalid parameters/features must stay finite");
}

void cadenceAndFreshRoutes() {
    CameraDirector at30(99), at120(99), otherSession(100);
    advance(at30, 30, 30, 1, 1);
    advance(at120, 120, 30, 1, 1);
    advance(otherSession, 30, 30, 1, 1);
    require(std::abs(at30.frame().distance - at120.frame().distance) < 0.05,
            "travel must not depend on display cadence");
    require(std::abs(at30.frame().x - at120.frame().x) < 0.01f &&
            std::abs(at30.frame().roll - at120.frame().roll) < 0.01f,
            "orbit/bank must stay consistent across frame rates");
    require(std::abs(at30.frame().x - otherSession.frame().x) > 0.0001f,
            "different session entropy must produce different paths");
}
}  // namespace

int main() {
    try {
        musicChangesTravelWithoutTeleporting();
        boundedMotionAndIndependentDials();
        reducedSpeedAndPauseApplyToTheWholeRig();
        invalidFramesAndResumeDoNotCorruptPose();
        cadenceAndFreshRoutes();
        std::cout << "CameraDirector tests passed\n";
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
