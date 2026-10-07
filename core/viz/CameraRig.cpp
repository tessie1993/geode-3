#include "viz/CameraRig.hpp"

#include <algorithm>
#include <cmath>

namespace geode::viz {
namespace {
using Vec3 = CameraRig::Vec3;
constexpr double kPi = 3.14159265358979323846;

float finiteClamp(float value, float lo, float hi) {
    return std::isfinite(value) ? std::clamp(value, lo, hi) : lo;
}
float follow(float current, float target, float dt, float seconds) {
    return current + (target - current) * -std::expm1(-dt / seconds);
}
// Exact integral of a one-pole velocity avoids an FPS-dependent start ramp.
double integrate(float& current, float target, float dt, float seconds) {
    const float previous = current;
    current = follow(current, target, dt, seconds);
    return static_cast<double>(target) * dt + static_cast<double>(previous - target) * seconds * -std::expm1(-dt / seconds);
}
Vec3 subtract(Vec3 a, Vec3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
Vec3 cross(Vec3 a, Vec3 b) {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}
Vec3 normalized(Vec3 v) {
    const float inverse = 1.0f / std::sqrt(std::max(v.x * v.x + v.y * v.y + v.z * v.z, 1e-12f));
    return {v.x * inverse, v.y * inverse, v.z * inverse};
}
}  // namespace

CameraRig::CameraRig(Profile profile) : profile_(profile) {
    yaw_ = profile == Profile::BloomOrbit ? kPi : 0.0;
    compose(0.0f, 0.0f);
}

CameraRig::Profile CameraRig::profileFor(std::string_view sceneId) {
    if (sceneId == "kifs") return Profile::CathedralOrbit;
    if (sceneId == "curl_bloom") return Profile::BloomOrbit;
    if (sceneId == "rod_tunnel") return Profile::RodFlight;
    if (sceneId == "nectar_flow") return Profile::NectarFlight;
    return Profile::Stationary;
}

CameraRig::Vec3 CameraRig::tunnelCenter(double distance) {
    return {static_cast<float>(1.4 * std::sin(distance * 0.21) + 2.6 * std::sin(distance * 0.043 + 1.3)),
            static_cast<float>(1.2 * std::cos(distance * 0.17 + 0.7) + 2.2 * std::cos(distance * 0.031)),
            static_cast<float>(distance)};
}

void CameraRig::step(float energy, float bass, const SceneParams& params, float dt) {
    dt = finiteClamp(dt, 0.0f, 0.1f);
    // Zero speed holds the entire rig, including its dolly and aim. Geometry
    // remains audio reactive through separate, much faster envelopes.
    const float requestedSpeed = finiteClamp(params.speed, 0.0f, 4.0f);
    if (dt == 0.0f || profile_ == Profile::Stationary) return;
    if (requestedSpeed == 0.0f) {
        speed_ = 0.0f;
        turnRate_ = 0.0f;
        return;
    }
    energy_ = follow(energy_, finiteClamp(energy, 0.0f, 1.5f), dt, energy > energy_ ? 0.8f : 2.0f);
    bass_ = follow(bass_, finiteClamp(bass, 0.0f, 1.5f), dt, bass > bass_ ? 0.9f : 2.2f);
    const float amount = finiteClamp(params.motionAmount, 0.0f, 1.0f);
    orbit_ = follow(orbit_, finiteClamp(params.motionOrbit, 0.0f, 1.0f), dt, 0.8f);
    sway_ = follow(sway_, finiteClamp(params.sway, 0.0f, 1.0f), dt, 0.8f);
    const float breath = finiteClamp(params.motionBreath, 0.0f, 1.0f);
    const float speedTarget = requestedSpeed * (1.0f + 0.10f * amount * energy_);
    const double advance = integrate(speed_, speedTarget, dt, 0.7f);
    time_ += advance;
    const bool flight = profile_ == Profile::RodFlight || profile_ == Profile::NectarFlight;
    if (flight) {
        distance_ += advance * (profile_ == Profile::RodFlight ? 1.55 : 0.62);
    } else {
        const float baseTurn = profile_ == Profile::CathedralOrbit ? 0.032f : 0.045f;
        const float turnTarget = finiteClamp(params.rotation, -0.18f, 0.18f);
        yaw_ += advance * baseTurn + integrate(turnRate_, turnTarget, dt, 1.0f);
        yaw_ = std::remainder(yaw_, 2.0 * kPi);
    }
    dolly_ = follow(dolly_, -0.14f * breath * bass_, dt, 1.2f);
    compose(orbit_, sway_);
}

void CameraRig::compose(float orbit, float sway) {
    Vec3 target;
    if (profile_ == Profile::RodFlight || profile_ == Profile::NectarFlight) {
        frame_.position = tunnelCenter(distance_);
        frame_.position.x += static_cast<float>(0.12 * orbit * std::sin(time_ * 0.17));
        frame_.position.y += static_cast<float>(0.08 * orbit * std::sin(time_ * 0.13));
        // Look ahead into a bend instead of following every local tangent.
        target = tunnelCenter(distance_ + 2.4);
    } else if (profile_ != Profile::Stationary) {
        const float radius = (profile_ == Profile::CathedralOrbit ? 4.15f : 3.1f) + dolly_;
        const double pitch = (0.20 * orbit + 0.08 * sway) * std::sin(time_ * 0.09);
        frame_.position = {static_cast<float>(radius * std::cos(pitch) * std::sin(yaw_)),
                           static_cast<float>(radius * std::sin(pitch)),
                           static_cast<float>(radius * std::cos(pitch) * std::cos(yaw_))};
    } else {
        frame_.position = {0.0f, 0.0f, -3.4f};
    }
    frame_.forward = normalized(subtract(target, frame_.position));
    frame_.right = normalized(cross({0.0f, 1.0f, 0.0f}, frame_.forward));
    frame_.up = cross(frame_.forward, frame_.right);
}
}  // namespace geode::viz
