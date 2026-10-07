#include "viz/SpatialCameraDirector.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <random>

namespace geode::viz {
namespace {
constexpr double kTickSeconds = 1.0 / 120.0;
constexpr float kTau = 6.28318530718f;
constexpr float kMaxLateral = 0.24f;
constexpr float kMaxRoll = 0.13f;
constexpr float kLookAhead = 3.5f;

uint64_t mix(uint64_t value) {
    value = (value ^ (value >> 30)) * 0xbf58476d1ce4e5b9ULL;
    value = (value ^ (value >> 27)) * 0x94d049bb133111ebULL;
    return value ^ (value >> 31);
}

uint64_t liveEntropy() {
    // Art variation, not a cryptographic secret. No preset or track seed is used.
    // OS entropy is acquired once, outside step(); time/serial also prevent an
    // unavailable random_device from turning all live sessions into one route.
    static std::atomic<uint64_t> serial{0};
    uint64_t value = static_cast<uint64_t>(std::chrono::steady_clock::now().time_since_epoch().count()) ^
                     serial.fetch_add(0x9e3779b97f4a7c15ULL, std::memory_order_relaxed);
    try {
        std::random_device source;
        value ^= static_cast<uint64_t>(source()) << 32;
        value ^= static_cast<uint64_t>(source());
    } catch (...) {
        // Variation still receives a new clock/serial value; audio steers it next.
    }
    return mix(value);
}

float finiteClamp(float value, float lo, float hi, float fallback = 0.0f) {
    return std::isfinite(value) ? std::clamp(value, lo, hi) : fallback;
}

float approach(float current, float target, float seconds) {
    return current + (target - current) * (1.0f - std::exp(-static_cast<float>(kTickSeconds) / seconds));
}

std::array<float, 3> normalized(std::array<float, 3> value) {
    const float length = std::sqrt(value[0] * value[0] + value[1] * value[1] + value[2] * value[2]);
    for (float& component : value) component /= std::max(length, 1e-6f);
    return value;
}

std::array<float, 3> cross(const std::array<float, 3>& a, const std::array<float, 3>& b) {
    return {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
}
}  // namespace

SpatialCameraDirector::SpatialCameraDirector() : SpatialCameraDirector(liveEntropy()) {}

SpatialCameraDirector::SpatialCameraDirector(uint64_t fixtureEntropy) : entropy_(fixtureEntropy) {
    frame_.corridorPhase = {random01() * kTau, random01() * kTau};
    formPhase_ = static_cast<double>(random01()) * kTau;
    frame_.formPhase = static_cast<float>(formPhase_);
    chooseTarget();
    updatePose();
}

float SpatialCameraDirector::random01() {
    entropy_ += 0x9e3779b97f4a7c15ULL;
    return static_cast<float>(mix(entropy_) >> 40) / 16777216.0f;
}

void SpatialCameraDirector::chooseTarget() {
    const float angle = random01() * kTau;
    const float radius = kMaxLateral * (0.35f + 0.65f * random01());
    lateralTargetX_ = std::cos(angle) * radius;
    lateralTargetY_ = std::sin(angle) * radius;
    rollTarget_ = (random01() * 2.0f - 1.0f) * kMaxRoll;
    targetAge_ = 0.0f;
    targetLife_ = 4.5f + random01() * 4.5f;
}

std::array<float, 2> SpatialCameraDirector::corridorCenter(float z) const {
    // Mirrors passageCenter() in prismatic_passage_frag.glsl. All harmonics
    // share the 96-unit repeat, so wrapping travel never teleports the geometry.
    const float phase = finiteClamp(z, -10000.0f, 10000.0f) * (kTau / kCorridorPeriod);
    return {
        0.55f * std::sin(phase + frame_.corridorPhase[0]) + 0.18f * std::sin(phase * 2.0f + frame_.corridorPhase[1]),
        0.42f * std::sin(phase + frame_.corridorPhase[1]) + 0.16f * std::sin(phase * 3.0f + frame_.corridorPhase[0]),
    };
}

void SpatialCameraDirector::step(const Signals& signals, const Intent& intent, float dt) {
    if (!std::isfinite(dt) || dt <= 0.0f) return;
    // A stalled/resumed surface does not catch up seconds of travel in one frame.
    accumulator_ += static_cast<double>(std::min(dt, 0.1f));
    for (int ticks = 0; ticks < 13 && accumulator_ + 1e-10 >= kTickSeconds; ++ticks) {
        tick(signals, intent);
        accumulator_ = std::max(0.0, accumulator_ - kTickSeconds);
    }
}

void SpatialCameraDirector::tick(const Signals& signals, const Intent& intent) {
    const std::array<float, 4> levels = {signals.bass, signals.mid, signals.treble, signals.energy};
    for (std::size_t i = 0; i < levels.size(); ++i) {
        const float level = finiteClamp(levels[i], 0.0f, 1.5f);
        frame_.bands[i] = approach(frame_.bands[i], level, level > frame_.bands[i] ? 0.12f : 0.65f);
    }
    const float motion = finiteClamp(intent.motion, 0.0f, 1.0f);
    const float requestedSpeed = finiteClamp(intent.speed, 0.0f, 4.0f);
    const bool sectionEdge = signals.section && !sectionHeld_;
    sectionHeld_ = signals.section;
    // Shape/material response remains available with a stationary camera.
    const float shapeMotion = intent.reducedMotion ? 0.0f : motion;
    formPhase_ = std::fmod(formPhase_ + kTickSeconds * requestedSpeed * shapeMotion *
                                       (0.11 + 0.12 * frame_.bands[1]), kTau);
    frame_.formPhase = static_cast<float>(formPhase_);
    if (intent.reducedMotion || motion <= 0.0f || requestedSpeed <= 0.0f) {
        // Preserve the realized pose; no easing-to-origin and no hidden clock
        // advance that would jump the camera when motion is enabled again.
        speed_ = 0.0f;
        return;
    }
    targetAge_ += static_cast<float>(kTickSeconds);
    if (targetAge_ >= targetLife_ || (sectionEdge && targetAge_ >= 2.5f)) chooseTarget();
    const float orbit = finiteClamp(intent.orbit, 0.0f, 1.0f) * motion;
    // Two poles keep velocity continuous at each fresh target choice.
    lateralAimX_ = approach(lateralAimX_, lateralTargetX_ * orbit, 1.4f);
    lateralAimY_ = approach(lateralAimY_, lateralTargetY_ * orbit, 1.4f);
    rollAim_ = approach(rollAim_, rollTarget_ * orbit, 1.6f);
    lateralX_ = approach(lateralX_, lateralAimX_, 1.4f);
    lateralY_ = approach(lateralY_, lateralAimY_, 1.4f);
    frame_.roll = approach(frame_.roll, rollAim_, 1.6f);
    // Bass changes forward pace; the integration, never wall time multiplied
    // by loudness, owns travel. Slow acceleration avoids beat-driven lurches.
    const float targetSpeed = motion * requestedSpeed * (0.48f + 0.54f * frame_.bands[0] + 0.2f * frame_.bands[3]);
    speed_ = approach(speed_, targetSpeed, 1.4f);
    travel_ = std::fmod(travel_ + static_cast<double>(speed_) * kTickSeconds, kCorridorPeriod);
    frame_.travel = std::min(static_cast<float>(travel_), std::nextafter(kCorridorPeriod, 0.0f));
    updatePose();
}

void SpatialCameraDirector::updatePose() {
    const auto center = corridorCenter(frame_.travel);
    const auto ahead = corridorCenter(frame_.travel + kLookAhead);
    frame_.position = {center[0] + lateralX_, center[1] + lateralY_, frame_.travel};
    frame_.forward = normalized({ahead[0] - frame_.position[0], ahead[1] - frame_.position[1], kLookAhead});
    const auto right = normalized(cross({0.0f, 1.0f, 0.0f}, frame_.forward));
    const auto up = cross(frame_.forward, right);
    const float cosine = std::cos(frame_.roll);
    const float sine = std::sin(frame_.roll);
    for (std::size_t i = 0; i < right.size(); ++i) {
        frame_.right[i] = right[i] * cosine + up[i] * sine;
        frame_.up[i] = up[i] * cosine - right[i] * sine;
    }
}

}  // namespace geode::viz
