#pragma once

#include <cmath>
#include <cstddef>
#include <initializer_list>
#include <utility>

#include "api/geode_api.h"
#include "viz/Adsr.hpp"
#include "viz/Lfo.hpp"

namespace geode::viz::admission {

inline bool range(float value, float minimum, float maximum) {
    return std::isfinite(value) && value >= minimum && value <= maximum;
}

inline bool flag(float value) { return value == 0.0f || value == 1.0f; }

// Unlike the old clamp-after-lround path, this checks the representable enum
// domain before conversion. Fractional in-range values retain legacy rounding.
template <typename E>
inline bool enumValue(float value, E last, E& out) {
    if (!range(value, 0.0f, static_cast<float>(last))) return false;
    out = static_cast<E>(std::lround(value));
    return true;
}

template <typename E>
inline bool enumValue(E value, E last) {
    return static_cast<int>(value) >= 0 && static_cast<int>(value) <= static_cast<int>(last);
}

inline bool validFeatures(const GeodeFeatureFrame& f) {
    // These levels are normalized by ReactiveAnalyzer/AnalysisSession, and the
    // wallpaper and Studio codecs use the same units. Do not reinterpret the
    // struct as a float array: pointer arithmetic across members is undefined.
    const float levels[] = {
        f.rms, f.bass, f.mid, f.treble, f.centroid, f.flux, f.onset,
        f.beatStrength, f.transient, f.beatPhase, f.pulseConfidence,
        f.tempoStability, f.barPhase, f.downbeatConfidence, f.macroEnergy,
        f.kick, f.snare, f.hat, f.novelty, f.buildup, f.harmonicity,
        f.warmup, f.stereoWidth, f.chromaConfidence,
    };
    for (float value : levels) if (!range(value, 0.0f, 1.0f)) return false;
    for (float value : {f.beat, f.downbeat, f.sectionBoundary, f.drop, f.arrival}) {
        if (!flag(value)) return false;
    }
    // BPM zero means no estimate. TempoTracker produces about 60..200 BPM,
    // while imported/offline timelines can carry other positive tempos.
    if (!std::isfinite(f.bpm) || f.bpm < 0.0f) return false;
    if (!range(f.beatInBar, 0.0f, 3.0f) || std::trunc(f.beatInBar) != f.beatInBar) return false;
    if (!range(f.stereoCorrelation, -1.0f, 1.0f) || !range(f.stereoPan, -1.0f, 1.0f)) return false;
    for (float value : f.bands) if (!range(value, 0.0f, 1.0f)) return false;
    for (float value : f.chroma) if (!range(value, 0.0f, 1.0f)) return false;
    // Waveform is raw signed float PCM, not a normalized level: upstream gain
    // may legitimately exceed full scale. No arbitrary unit clamp is applied.
    for (float value : f.waveform) if (!std::isfinite(value)) return false;
    return true;
}

inline bool publishFeatures(GeodeFeatureFrame& destination, const GeodeFeatureFrame& incoming) {
    if (!validFeatures(incoming)) return false;
    destination = incoming;
    return true;
}

inline bool validLfo(const LfoConfig& c) {
    return enumValue(c.source, ModSource::StereoPan) && enumValue(c.target, LfoTarget::Lfo3Depth) &&
           enumValue(c.wave, LfoWave::Random) && enumValue(c.polarity, ModPolarity::Negative) &&
           enumValue(c.curve, ModCurve::Smooth) &&
           range(c.rateSeconds, LfoConfig::kMinRateSeconds, LfoConfig::kMaxRateSeconds) &&
           range(c.depth, 0.0f, 1.0f);
}

inline bool decodeLfo(const float* values, int count, LfoConfig& destination) {
    if (!values || count != GEODE_LFO_CONFIG_FLOATS || !flag(values[0])) return false;
    LfoConfig next;
    if (!enumValue(values[1], ModSource::StereoPan, next.source) ||
        !enumValue(values[2], LfoTarget::Lfo3Depth, next.target) ||
        !enumValue(values[3], LfoWave::Random, next.wave) ||
        !enumValue(values[6], ModPolarity::Negative, next.polarity) ||
        !enumValue(values[7], ModCurve::Smooth, next.curve)) return false;
    next.enabled = values[0] > 0.5f;
    next.rateSeconds = values[4];
    next.depth = values[5];
    if (!validLfo(next)) return false;
    destination = next;
    return true;
}

inline bool validAdsr(const AdsrConfig& c) {
    // Seconds and amount follow CustomizeTabs' envelope controls. Zero timing
    // remains legal for persisted instant envelopes supported by AdsrEngine.
    if (!range(c.attack, 0.0f, 1.0f) || !range(c.decay, 0.0f, 1.5f) ||
        !range(c.sustain, 0.0f, 1.0f) || !range(c.release, 0.0f, 2.0f) ||
        !range(c.amount, 0.0f, 1.5f) || !range(c.gateThreshold, 0.0f, 1.0f) ||
        !enumValue(c.band, EnvBand::Width) ||
        c.targets.size() > static_cast<size_t>(LfoTarget::Lfo3Depth) + 1) return false;
    for (LfoTarget target : c.targets) if (!enumValue(target, LfoTarget::Lfo3Depth)) return false;
    return true;
}

inline bool decodeAdsr(const float* values, int count, AdsrConfig& destination) {
    constexpr int kMaxTargets = static_cast<int>(LfoTarget::Lfo3Depth) + 1;
    if (!values || count < GEODE_ADSR_CONFIG_FLOATS || count > GEODE_ADSR_CONFIG_FLOATS + kMaxTargets ||
        !flag(values[0]) || !flag(values[8]) || !flag(values[9])) return false;
    AdsrConfig next;
    next.enabled = values[0] > 0.5f;
    next.attack = values[1];
    next.decay = values[2];
    next.sustain = values[3];
    next.release = values[4];
    next.amount = values[5];
    if (!enumValue(values[6], EnvBand::Width, next.band)) return false;
    next.gateThreshold = values[7];
    next.sustainTrack = values[8] > 0.5f;
    next.retrigger = values[9] > 0.5f;
    for (int i = GEODE_ADSR_CONFIG_FLOATS; i < count; ++i) {
        LfoTarget target;
        if (!enumValue(values[i], LfoTarget::Lfo3Depth, target)) return false;
        next.targets.push_back(target);
    }
    if (!validAdsr(next)) return false;
    destination = std::move(next);
    return true;
}

}  // namespace geode::viz::admission
