#pragma once

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>

namespace geode::viz {

// Stateful path-following rig. Scene adapters own corridor geometry and lens;
// this director supplies integrated travel, lateral displacement and bank.
// Positions are scene units, roll is radians, speed is the already safety-scaled
// scene speed. No clocks, allocation or entropy calls occur in step().
class CameraDirector {
public:
    struct Frame {
        double distance = 0.0;
        float x = 0.0f;
        float y = 0.0f;
        float roll = 0.0f;
    };

    // Bounds leave over one scene unit of clearance in Rod Tunnel (radius 1.6).
    static constexpr float kOffsetLimit = 0.30f;
    static constexpr float kRollLimit = 0.18f;
    static constexpr float kBaseTravelRate = 1.4f;
    static constexpr float kAudioTravelRate = 1.8f;

    CameraDirector() : CameraDirector(liveSeed()) {}
    // Reproducible entropy is for tests only, never a track/preset-derived route.
    explicit CameraDirector(uint32_t fixtureSeed) : random_(fixtureSeed ? fixtureSeed : 1U) {
        frame_.distance = 100.0 * random01();
        chooseTarget();
    }

    const Frame& frame() const { return frame_; }

    void step(float dt, float speed, float energy, float bass, float motion,
              float orbit, float drift) {
        if (!std::isfinite(dt) || dt <= 0.0f) return;
        dt = std::min(dt, 0.1f); // A resumed surface never catches up by teleporting.
        speed = finiteClamp(speed, 0.0f, 4.0f);
        motion = finiteClamp(motion, 0.0f, 1.0f);
        const float clock = dt * speed * motion;
        if (clock == 0.0f) return;
        orbit = finiteClamp(orbit, 0.0f, 1.0f) * motion;
        drift = finiteClamp(drift, 0.0f, 1.0f) * motion;
        const float drive = 0.65f * finiteClamp(energy, 0.0f, 1.0f) +
                            0.35f * finiteClamp(bass, 0.0f, 1.0f);
        audio_ = ease(audio_, drive, dt, 0.45f);
        // Integrate the rate: changing loudness/speed cannot relocate the origin.
        frame_.distance += static_cast<double>(clock) *
                           (kBaseTravelRate + kAudioTravelRate * audio_ * motion);
        targetRemaining_ -= clock;
        if (targetRemaining_ <= 0.0f) chooseTarget();
        // Two poles keep velocity continuous when a new organic target arrives.
        // Music changes excursion, not an instantaneous position or random shake.
        const float excursion = 0.35f + 0.65f * audio_;
        xAim_ = ease(xAim_, targetX_ * orbit * excursion, clock, 1.5f);
        yAim_ = ease(yAim_, targetY_ * orbit * excursion, clock, 1.5f);
        rollAim_ = ease(rollAim_, targetRoll_ * drift * excursion, clock, 2.0f);
        frame_.x = ease(frame_.x, xAim_, clock, 1.5f);
        frame_.y = ease(frame_.y, yAim_, clock, 1.5f);
        frame_.roll = ease(frame_.roll, rollAim_, clock, 2.0f);
    }

private:
    static float finiteClamp(float value, float lo, float hi) {
        return std::isfinite(value) ? std::clamp(value, lo, hi) : lo;
    }
    static float ease(float current, float target, float dt, float seconds) {
        return current + (target - current) * (-std::expm1(-dt / seconds));
    }
    static uint32_t liveSeed() {
        // Variation only; never used for security. Serial separates simultaneous
        // scene construction and the clock gives each live session a fresh route.
        static std::atomic<uint64_t> serial{0};
        uint64_t value = static_cast<uint64_t>(std::chrono::steady_clock::now().time_since_epoch().count()) +
                         serial.fetch_add(0x9e3779b97f4a7c15ULL, std::memory_order_relaxed);
        value = (value ^ (value >> 30)) * 0xbf58476d1ce4e5b9ULL;
        value = (value ^ (value >> 27)) * 0x94d049bb133111ebULL;
        return static_cast<uint32_t>(value ^ (value >> 31));
    }
    float random01() {
        random_ ^= random_ << 13;
        random_ ^= random_ >> 17;
        random_ ^= random_ << 5;
        return static_cast<float>(random_ >> 8) / 16777216.0f;
    }
    void chooseTarget() {
        targetX_ = (2.0f * random01() - 1.0f) * kOffsetLimit;
        targetY_ = (2.0f * random01() - 1.0f) * kOffsetLimit;
        targetRoll_ = (2.0f * random01() - 1.0f) * kRollLimit;
        targetRemaining_ += 4.0f + 5.0f * random01();
    }

    uint32_t random_;
    Frame frame_;
    float targetRemaining_ = 0.0f;
    float targetX_ = 0.0f;
    float targetY_ = 0.0f;
    float targetRoll_ = 0.0f;
    float xAim_ = 0.0f;
    float yAim_ = 0.0f;
    float rollAim_ = 0.0f;
    float audio_ = 0.0f;
};

}  // namespace geode::viz
