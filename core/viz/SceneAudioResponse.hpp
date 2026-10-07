#pragma once

#include <algorithm>
#include <cmath>

#include "api/geode_api.h"

namespace geode::viz {

// Geometry, fine highlights and the camera deliberately use different time
// scales. Accents consume rising edges; a cached feature frame cannot keep
// retriggering a beat while the display renders it more than once.
class SceneAudioResponse {
public:
    struct State {
        float bass = 0.0f, mid = 0.0f, treble = 0.0f, energy = 0.0f, swell = 0.0f;
        float accent = 0.0f;
    };
    void step(const GeodeFeatureFrame& features, float drive, float dt) {
        dt = bounded(dt, 0.1f);
        drive = bounded(drive, 4.0f);
        state_.bass = envelope(state_.bass, bounded(features.bass * drive, 1.5f), dt, 0.030f, 0.260f);
        state_.mid = envelope(state_.mid, bounded(features.mid * drive, 1.5f), dt, 0.050f, 0.220f);
        state_.treble = envelope(state_.treble, bounded(features.treble * drive, 1.5f), dt, 0.012f, 0.100f);
        state_.energy = envelope(state_.energy, bounded(features.rms * drive, 1.5f), dt, 0.045f, 0.300f);
        state_.swell = envelope(state_.swell, bounded(features.rms * drive, 1.5f), dt, 0.8f, 1.8f);
        state_.accent *= std::exp(-dt / 0.180f);
        const bool beat = bounded(features.beat, 1.0f) > 0.0f;
        const float transient = bounded(features.transient, 1.0f);
        const bool transientHot = transient >= 0.06f;
        float impulse = 0.0f;
        if (beat && !beatHeld_) {
            const float strength = features.beatStrength > 0.0f ? bounded(features.beatStrength, 1.0f) : 1.0f;
            impulse = strength;
        }
        if (transientHot && !transientHeld_) impulse = std::max(impulse, transient);
        beatHeld_ = beat;
        transientHeld_ = transientHot;
        state_.accent = std::max(state_.accent, bounded(impulse * drive, 1.0f));
    }
    const State& state() const { return state_; }

private:
    static float bounded(float v, float maximum) {
        return std::isfinite(v) ? std::clamp(v, 0.0f, maximum) : 0.0f;
    }
    static float envelope(float current, float target, float dt, float attack, float release) {
        return current + (target - current) * -std::expm1(-dt / (target > current ? attack : release));
    }
    State state_;
    bool beatHeld_ = false;
    bool transientHeld_ = false;
};
}  // namespace geode::viz
