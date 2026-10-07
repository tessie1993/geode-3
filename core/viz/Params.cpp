#include "viz/Params.hpp"

#include <algorithm>
#include <cmath>

namespace geode::viz {

const std::array<SceneParams::Palette, 21>& SceneParams::palettes() {
    static const std::array<Palette, 21> kPalettes = {{
        {"Spectrum", 0.0f, 1.0f},   {"Neon", 0.5f, 0.45f},    {"Fire", 0.0f, 0.14f},    {"Ocean", 0.5f, 0.2f},
        {"Mono", 0.6f, 0.02f},      {"Candy", 0.85f, 0.5f},   {"Forest", 0.33f, 0.18f}, {"Aurora", 0.45f, 0.7f},
        {"Sunset", 0.05f, 0.3f},    {"Ice", 0.55f, 0.15f},    {"Vapor", 0.78f, 0.35f},  {"Toxic", 0.25f, 0.25f},
        {"Royal", 0.7f, 0.25f},     {"Blush", 0.93f, 0.12f},  {"Copper", 0.07f, 0.1f},  {"Mint", 0.4f, 0.12f},
        {"Galaxy", 0.65f, 0.5f},    {"Cherry", 0.97f, 0.08f}, {"Cyan", 0.5f, 0.08f},    {"Magenta", 0.833f, 0.08f},
        {"Yellow", 0.167f, 0.08f},
    }};
    return kPalettes;
}

namespace {
const SceneParams::Palette& paletteAt(int index) {
    const auto& table = SceneParams::palettes();
    return table[static_cast<size_t>(std::clamp(index, 0, static_cast<int>(table.size()) - 1))];
}
}  // namespace

float SceneParams::paletteBase() const { return paletteBaseOverride >= 0.0f ? paletteBaseOverride : paletteAt(palette).base; }
float SceneParams::paletteRange() const { return paletteRangeOverride >= 0.0f ? paletteRangeOverride : paletteAt(palette).range; }
float SceneParams::palette2Base() const { return palette2BaseOverride >= 0.0f ? palette2BaseOverride : paletteAt(palette2).base; }
float SceneParams::palette2Range() const { return palette2RangeOverride >= 0.0f ? palette2RangeOverride : paletteAt(palette2).range; }

const std::array<SceneParams::FloatField, SceneParams::kLerpedFloatCount>& SceneParams::lerpedFloats() {
    static const std::array<FloatField, kLerpedFloatCount> kFields = {{
        {"speed", &SceneParams::speed},
        {"zoom", &SceneParams::zoom},
        {"rotation", &SceneParams::rotation},
        {"endlessZoomSpeed", &SceneParams::endlessZoomSpeed},
        {"sway", &SceneParams::sway},
        {"pulse", &SceneParams::pulse},
        {"driftX", &SceneParams::driftX},
        {"driftY", &SceneParams::driftY},
        {"shake", &SceneParams::shake},
        {"audioDrive", &SceneParams::audioDrive},
        {"beatResponse", &SceneParams::beatResponse},
        {"turbulence", &SceneParams::turbulence},
        {"density", &SceneParams::density},
        {"marchDetail", &SceneParams::marchDetail},
        {"trailLength", &SceneParams::trailLength},
        {"trailZoom", &SceneParams::trailZoom},
        {"trailWarp", &SceneParams::trailWarp},
        {"warp", &SceneParams::warp},
        {"ripple", &SceneParams::ripple},
        {"morph", &SceneParams::morph},
        {"pixelate", &SceneParams::pixelate},
        {"posterize", &SceneParams::posterize},
        {"particleSize", &SceneParams::particleSize},
        {"tile", &SceneParams::tile},
        {"twist", &SceneParams::twist},
        {"paletteMix", &SceneParams::paletteMix},
        {"milkdropPaletteTint", &SceneParams::milkdropPaletteTint},
        {"colorShift", &SceneParams::colorShift},
        {"hueRange", &SceneParams::hueRange},
        {"saturation", &SceneParams::saturation},
        {"brightness", &SceneParams::brightness},
        {"contrast", &SceneParams::contrast},
        {"gamma", &SceneParams::gamma},
        {"cycleSpeed", &SceneParams::cycleSpeed},
        {"intensity", &SceneParams::intensity},
        {"bloom", &SceneParams::bloom},
        {"temperature", &SceneParams::temperature},
        {"bassGain", &SceneParams::bassGain},
        {"midGain", &SceneParams::midGain},
        {"trebGain", &SceneParams::trebGain},
        {"flash", &SceneParams::flash},
        {"chromaAb", &SceneParams::chromaAb},
        {"vignette", &SceneParams::vignette},
        {"scanlines", &SceneParams::scanlines},
        {"grain", &SceneParams::grain},
        {"glitch", &SceneParams::glitch},
        {"fisheye", &SceneParams::fisheye},
        {"strobe", &SceneParams::strobe},
        {"fluidPressure", &SceneParams::fluidPressure},
        {"fluidCurl", &SceneParams::fluidCurl},
        {"fluidVelocityDissipation", &SceneParams::fluidVelocityDissipation},
        {"fluidDensityDissipation", &SceneParams::fluidDensityDissipation},
        {"fluidChromaticAging", &SceneParams::fluidChromaticAging},
        {"fluidSplatRadius", &SceneParams::fluidSplatRadius},
        {"fluidSplatForce", &SceneParams::fluidSplatForce},
        {"fluidStirrerSpeed", &SceneParams::fluidStirrerSpeed},
        {"fluidPaletteCycleSpeed", &SceneParams::fluidPaletteCycleSpeed},
        {"fluidSpawnProgress", &SceneParams::fluidSpawnProgress},
        {"fluidCatchPull", &SceneParams::fluidCatchPull},
        {"fluidCatchRadius", &SceneParams::fluidCatchRadius},
        {"fluidParticleLife", &SceneParams::fluidParticleLife},
        {"fluidParticleDrag", &SceneParams::fluidParticleDrag},
        {"fluidParticleBrightness", &SceneParams::fluidParticleBrightness},
        {"fluidBloomIntensity", &SceneParams::fluidBloomIntensity},
        {"fluidBloomThreshold", &SceneParams::fluidBloomThreshold},
        {"fluidSunraysWeight", &SceneParams::fluidSunraysWeight},
        {"fluidCurlAudio", &SceneParams::fluidCurlAudio},
        {"fluidBloomAudio", &SceneParams::fluidBloomAudio},
        {"fluidFadeAudio", &SceneParams::fluidFadeAudio},
        {"fluidRadiusPulse", &SceneParams::fluidRadiusPulse},
        {"flowStrength", &SceneParams::flowStrength},
        {"flowForce", &SceneParams::flowForce},
        {"flowCurl", &SceneParams::flowCurl},
        {"waterWaveSpeed", &SceneParams::waterWaveSpeed},
        {"waterDamping", &SceneParams::waterDamping},
        {"waterRippleStrength", &SceneParams::waterRippleStrength},
        {"waterDepth", &SceneParams::waterDepth},
        {"waterSpecular", &SceneParams::waterSpecular},
        {"waterFlow", &SceneParams::waterFlow},
        {"waterLiquid", &SceneParams::waterLiquid},
        {"waterLiquidFlow", &SceneParams::waterLiquidFlow},
        {"waterLiquidFade", &SceneParams::waterLiquidFade},
        {"cymaticsFundamental", &SceneParams::cymaticsFundamental},
        {"cymaticsRing", &SceneParams::cymaticsRing},
        {"cymaticsFocus", &SceneParams::cymaticsFocus},
        {"cymaticsScale", &SceneParams::cymaticsScale},
        {"cymaticsFill", &SceneParams::cymaticsFill},
        {"cymaticsLine", &SceneParams::cymaticsLine},
        {"cymaticsGlow", &SceneParams::cymaticsGlow},
        {"cymaticsIridescence", &SceneParams::cymaticsIridescence},
        {"cymaticsCaustic", &SceneParams::cymaticsCaustic},
        {"cymaticsFlow", &SceneParams::cymaticsFlow},
        {"cymaticsSwirl", &SceneParams::cymaticsSwirl},
        {"rippleOverlayStrength", &SceneParams::rippleOverlayStrength},
        {"rippleOverlaySpecular", &SceneParams::rippleOverlaySpecular},
        {"formDrive", &SceneParams::formDrive},
        {"motionAmount", &SceneParams::motionAmount},
        {"motionBreath", &SceneParams::motionBreath},
        {"motionOrbit", &SceneParams::motionOrbit},
        {"motionDrift", &SceneParams::motionDrift},
        {"motionHue", &SceneParams::motionHue},
    }};
    return kFields;
}

namespace {
// Admission domains mirror CustomizeTabs, PresetAdmission and SceneParams.kt.
// Keep signed controls, units (Hz/seconds) and disabled zero values intact.
// Negative palette overrides in the admitted -1..0 interval mean unset,
// matching saved Kotlin presets; the nominal sentinel remains exactly -1.
// These constrain user input, not the internally modulated/rendered parameters.
struct ParamField {
    const char* name;
    float minimum;
    float maximum;
    float SceneParams::*floating = nullptr;
    int SceneParams::*integer = nullptr;
    bool SceneParams::*boolean = nullptr;

    constexpr ParamField(const char* n, float SceneParams::*m, float lo, float hi)
        : name(n), minimum(lo), maximum(hi), floating(m) {}
    constexpr ParamField(const char* n, int SceneParams::*m, float lo, float hi)
        : name(n), minimum(lo), maximum(hi), integer(m) {}
    constexpr ParamField(const char* n, bool SceneParams::*m, float lo, float hi)
        : name(n), minimum(lo), maximum(hi), boolean(m) {}

    bool accepts(float value) const {
        return std::isfinite(value) && value >= minimum && value <= maximum;
    }

    float read(const SceneParams& p) const {
        if (floating) return p.*floating;
        if (integer) return static_cast<float>(p.*integer);
        return p.*boolean ? 1.0f : 0.0f;
    }

    void write(SceneParams& p, float value) const {
        if (floating) p.*floating = value;
        // All integer domains are small and checked before reaching lround.
        // Retain the existing round-to-nearest API behavior within that domain.
        else if (integer) p.*integer = static_cast<int>(std::lround(value));
        else p.*boolean = value > 0.5f;
    }
};

constexpr ParamField kFields[] = {
    {"speed", &SceneParams::speed, 0.0f, 4.0f},
    {"zoom", &SceneParams::zoom, 0.3f, 3.0f},
    {"rotation", &SceneParams::rotation, -3.0f, 3.0f},
    {"endlessZoom", &SceneParams::endlessZoom, 0.0f, 1.0f},
    {"endlessZoomSpeed", &SceneParams::endlessZoomSpeed, 0.0f, 1.2f},
    {"sway", &SceneParams::sway, 0.0f, 1.0f},
    {"pulse", &SceneParams::pulse, 0.0f, 1.0f},
    {"driftX", &SceneParams::driftX, -1.0f, 1.0f},
    {"driftY", &SceneParams::driftY, -1.0f, 1.0f},
    {"shake", &SceneParams::shake, 0.0f, 1.0f},
    {"audioDrive", &SceneParams::audioDrive, 0.0f, 2.5f},
    {"beatResponse", &SceneParams::beatResponse, 0.0f, 2.0f},
    {"turbulence", &SceneParams::turbulence, 0.0f, 1.5f},
    {"density", &SceneParams::density, 0.0f, 1.0f},
    {"marchDetail", &SceneParams::marchDetail, 0.25f, 1.5f},
    {"trails", &SceneParams::trails, 0.0f, 1.0f},
    {"trailLength", &SceneParams::trailLength, 0.0f, 1.0f},
    {"trailZoom", &SceneParams::trailZoom, -0.5f, 0.5f},
    {"trailWarp", &SceneParams::trailWarp, 0.0f, 1.0f},
    {"mirror", &SceneParams::mirror, 0.0f, 1.0f},
    {"warp", &SceneParams::warp, 0.0f, 1.0f},
    {"ripple", &SceneParams::ripple, 0.0f, 1.0f},
    {"symmetry", &SceneParams::symmetry, 0.0f, 16.0f},
    {"kaleidoscope", &SceneParams::kaleidoscope, 0.0f, 1.0f},
    {"morph", &SceneParams::morph, 0.0f, 1.0f},
    {"pixelate", &SceneParams::pixelate, 0.0f, 1.0f},
    {"posterize", &SceneParams::posterize, 0.0f, 1.0f},
    {"particleShape", &SceneParams::particleShape, 0.0f, 6.0f},
    {"particleSize", &SceneParams::particleSize, 0.3f, 2.5f},
    {"tile", &SceneParams::tile, 1.0f, 6.0f},
    {"twist", &SceneParams::twist, -1.0f, 1.0f},
    {"palette", &SceneParams::palette, 0.0f, 20.0f},
    {"palette2", &SceneParams::palette2, 0.0f, 20.0f},
    {"paletteMix", &SceneParams::paletteMix, 0.0f, 1.0f},
    {"paletteBaseOverride", &SceneParams::paletteBaseOverride, -1.0f, 1.0f},
    {"paletteRangeOverride", &SceneParams::paletteRangeOverride, -1.0f, 1.0f},
    {"palette2BaseOverride", &SceneParams::palette2BaseOverride, -1.0f, 1.0f},
    {"palette2RangeOverride", &SceneParams::palette2RangeOverride, -1.0f, 1.0f},
    {"paletteLut", &SceneParams::paletteLut, -1.0f, 4.0f},
    {"milkdropPaletteTint", &SceneParams::milkdropPaletteTint, 0.0f, 1.0f},
    {"milkdropBlendPresets", &SceneParams::milkdropBlendPresets, 0.0f, 1.0f},
    {"colorShift", &SceneParams::colorShift, 0.0f, 1.0f},
    {"hueRange", &SceneParams::hueRange, 0.0f, 1.5f},
    {"saturation", &SceneParams::saturation, 0.0f, 1.5f},
    {"brightness", &SceneParams::brightness, 0.0f, 2.0f},
    {"contrast", &SceneParams::contrast, 0.0f, 2.5f},
    {"gamma", &SceneParams::gamma, 0.3f, 2.5f},
    {"colorCycle", &SceneParams::colorCycle, 0.0f, 1.0f},
    {"cycleSpeed", &SceneParams::cycleSpeed, 0.0f, 0.6f},
    {"invert", &SceneParams::invert, 0.0f, 1.0f},
    {"intensity", &SceneParams::intensity, 0.0f, 2.0f},
    {"duotone", &SceneParams::duotone, 0.0f, 1.0f},
    {"bloom", &SceneParams::bloom, 0.0f, 1.0f},
    {"temperature", &SceneParams::temperature, -1.0f, 1.0f},
    {"solarize", &SceneParams::solarize, 0.0f, 1.0f},
    {"bassGain", &SceneParams::bassGain, 0.0f, 2.0f},
    {"midGain", &SceneParams::midGain, 0.0f, 2.0f},
    {"trebGain", &SceneParams::trebGain, 0.0f, 2.0f},
    {"flash", &SceneParams::flash, 0.0f, 1.0f},
    {"chromaAb", &SceneParams::chromaAb, 0.0f, 1.0f},
    {"vignette", &SceneParams::vignette, 0.0f, 1.0f},
    {"scanlines", &SceneParams::scanlines, 0.0f, 1.0f},
    {"grain", &SceneParams::grain, 0.0f, 1.0f},
    {"glitch", &SceneParams::glitch, 0.0f, 1.0f},
    {"fisheye", &SceneParams::fisheye, -1.0f, 1.0f},
    {"strobe", &SceneParams::strobe, 0.0f, 1.0f},
    {"paramFadeSec", &SceneParams::paramFadeSec, 0.0f, 5.0f},
    {"fluidQuality", &SceneParams::fluidQuality, 0.0f, 4.0f},
    {"fluidAutoQuality", &SceneParams::fluidAutoQuality, 0.0f, 1.0f},
    {"fluidIterations", &SceneParams::fluidIterations, 8.0f, 40.0f},
    {"fluidPressure", &SceneParams::fluidPressure, 0.0f, 1.0f},
    {"fluidCurl", &SceneParams::fluidCurl, 0.0f, 50.0f},
    {"fluidVelocityDissipation", &SceneParams::fluidVelocityDissipation, 0.0f, 4.0f},
    {"fluidDensityDissipation", &SceneParams::fluidDensityDissipation, 0.0f, 4.0f},
    {"fluidChromaticAging", &SceneParams::fluidChromaticAging, 0.0f, 1.0f},
    {"fluidSplatRadius", &SceneParams::fluidSplatRadius, 0.02f, 0.4f},
    {"fluidSplatForce", &SceneParams::fluidSplatForce, 0.0f, 3.0f},
    {"fluidBeatPattern", &SceneParams::fluidBeatPattern, 0.0f, 3.0f},
    {"fluidBeatSplats", &SceneParams::fluidBeatSplats, 0.0f, 8.0f},
    {"fluidStirrers", &SceneParams::fluidStirrers, 0.0f, 4.0f},
    {"fluidStirrerSpeed", &SceneParams::fluidStirrerSpeed, 0.0f, 2.0f},
    {"fluidBassPump", &SceneParams::fluidBassPump, 0.0f, 1.0f},
    {"fluidPaletteCycleSpeed", &SceneParams::fluidPaletteCycleSpeed, 0.0f, 2.0f},
    {"fluidSparkle", &SceneParams::fluidSparkle, 0.0f, 1.0f},
    {"fluidSpawnPath", &SceneParams::fluidSpawnPath, 0.0f, 4.0f},
    {"fluidSpawnPoints", &SceneParams::fluidSpawnPoints, 1.0f, 8.0f},
    {"fluidSpawnProgress", &SceneParams::fluidSpawnProgress, 0.0f, 1.0f},
    {"fluidCatchPoints", &SceneParams::fluidCatchPoints, 0.0f, 4.0f},
    {"fluidCatchPull", &SceneParams::fluidCatchPull, 0.0f, 3.0f},
    {"fluidCatchRadius", &SceneParams::fluidCatchRadius, 0.03f, 0.3f},
    {"fluidParticlesEnabled", &SceneParams::fluidParticlesEnabled, 0.0f, 1.0f},
    {"fluidParticleLife", &SceneParams::fluidParticleLife, 1.0f, 20.0f},
    {"fluidParticleDrag", &SceneParams::fluidParticleDrag, 0.0f, 1.0f},
    {"fluidParticleBrightness", &SceneParams::fluidParticleBrightness, 0.0f, 2.0f},
    {"fluidDyeEnabled", &SceneParams::fluidDyeEnabled, 0.0f, 1.0f},
    {"fluidShading", &SceneParams::fluidShading, 0.0f, 1.0f},
    {"fluidBloom", &SceneParams::fluidBloom, 0.0f, 1.0f},
    {"fluidBloomIntensity", &SceneParams::fluidBloomIntensity, 0.0f, 2.0f},
    {"fluidBloomThreshold", &SceneParams::fluidBloomThreshold, 0.0f, 1.0f},
    {"fluidSunrays", &SceneParams::fluidSunrays, 0.0f, 1.0f},
    // BuiltInPresets' Spectrum/Aurora ship 1.2/1.1; FluidScene already caps
    // the rendered weight at 1. Keep loading those complete presets intact.
    {"fluidSunraysWeight", &SceneParams::fluidSunraysWeight, 0.0f, 1.2f},
    {"fluidCurlAudio", &SceneParams::fluidCurlAudio, 0.0f, 1.0f},
    {"fluidBloomAudio", &SceneParams::fluidBloomAudio, 0.0f, 1.0f},
    {"fluidFadeAudio", &SceneParams::fluidFadeAudio, 0.0f, 1.0f},
    {"fluidRadiusPulse", &SceneParams::fluidRadiusPulse, 0.0f, 1.0f},
    {"flowEnabled", &SceneParams::flowEnabled, 0.0f, 1.0f},
    {"flowStrength", &SceneParams::flowStrength, 0.0f, 1.0f},
    {"flowForce", &SceneParams::flowForce, 0.0f, 3.0f},
    {"flowCurl", &SceneParams::flowCurl, 0.0f, 50.0f},
    {"waterWaveSpeed", &SceneParams::waterWaveSpeed, 0.2f, 2.0f},
    {"waterDamping", &SceneParams::waterDamping, 0.9f, 0.999f},
    {"waterRippleStrength", &SceneParams::waterRippleStrength, 0.0f, 2.0f},
    {"waterDepth", &SceneParams::waterDepth, 0.0f, 1.0f},
    {"waterSpecular", &SceneParams::waterSpecular, 0.0f, 1.0f},
    {"waterFlow", &SceneParams::waterFlow, 0.0f, 1.0f},
    {"waterLiquid", &SceneParams::waterLiquid, 0.0f, 1.0f},
    {"waterLiquidFlow", &SceneParams::waterLiquidFlow, 0.0f, 4.0f},
    {"waterLiquidFade", &SceneParams::waterLiquidFade, 0.0f, 2.0f},
    {"cymaticsGeometry", &SceneParams::cymaticsGeometry, 0.0f, 1.0f},
    {"cymaticsFundamental", &SceneParams::cymaticsFundamental, 40.0f, 440.0f},
    {"cymaticsModes", &SceneParams::cymaticsModes, 1.0f, 8.0f},
    {"cymaticsRing", &SceneParams::cymaticsRing, 0.0f, 1.0f},
    {"cymaticsFocus", &SceneParams::cymaticsFocus, 0.0f, 1.0f},
    {"cymaticsScale", &SceneParams::cymaticsScale, 0.5f, 8.0f},
    {"cymaticsFill", &SceneParams::cymaticsFill, 0.0f, 1.0f},
    {"cymaticsLine", &SceneParams::cymaticsLine, 0.0f, 2.0f},
    {"cymaticsGlow", &SceneParams::cymaticsGlow, 0.0f, 2.0f},
    {"cymaticsIridescence", &SceneParams::cymaticsIridescence, 0.0f, 1.0f},
    {"cymaticsCaustic", &SceneParams::cymaticsCaustic, 0.0f, 1.5f},
    {"cymaticsFlow", &SceneParams::cymaticsFlow, 0.0f, 1.0f},
    {"cymaticsSwirl", &SceneParams::cymaticsSwirl, -1.0f, 1.0f},
    {"rippleOverlayEnabled", &SceneParams::rippleOverlayEnabled, 0.0f, 1.0f},
    {"rippleOverlayStrength", &SceneParams::rippleOverlayStrength, 0.0f, 1.0f},
    {"rippleOverlaySpecular", &SceneParams::rippleOverlaySpecular, 0.0f, 1.0f},
    {"formDrive", &SceneParams::formDrive, 0.0f, 1.0f},
    {"motionAmount", &SceneParams::motionAmount, 0.0f, 1.0f},
    {"motionBreath", &SceneParams::motionBreath, 0.0f, 1.0f},
    {"motionOrbit", &SceneParams::motionOrbit, 0.0f, 1.0f},
    {"motionDrift", &SceneParams::motionDrift, 0.0f, 1.0f},
    {"motionHue", &SceneParams::motionHue, 0.0f, 1.0f},
};
static_assert(sizeof(kFields) / sizeof(kFields[0]) == SceneParams::kFieldCount);
}  // namespace

bool SceneParams::set(std::string_view name, float value) {
    for (const auto& field : kFields) {
        if (name != field.name) continue;
        if (!field.accepts(value)) return false;
        field.write(*this, value);
        return true;
    }
    return false;
}

bool SceneParams::get(std::string_view name, float& value) const {
    for (const auto& field : kFields) {
        if (name != field.name) continue;
        value = field.read(*this);
        return true;
    }
    return false;
}

bool SceneParams::valid() const {
    for (const auto& field : kFields) {
        if (!field.accepts(field.read(*this))) return false;
    }
    return true;
}

bool SceneParams::setFrame(const float* values, int count) {
    if (!values || count != kFieldCount) return false;
    SceneParams next;
    const auto& names = fieldNames();
    for (int i = 0; i < count; ++i) {
        if (!next.set(names[static_cast<size_t>(i)], values[i])) return false;
    }
    *this = next;
    return true;
}

SceneParams lerpParams(const SceneParams& from, const SceneParams& to, float k) {
    SceneParams out = to;
    for (const auto& f : SceneParams::lerpedFloats()) {
        const float a = from.*f.member;
        out.*f.member = a + (to.*f.member - a) * k;
    }
    return out;
}

SceneParams blendParams(const SceneParams& a, const SceneParams& b, float t) {
    const float k = std::clamp(t, 0.0f, 1.0f);
    SceneParams out = k < 0.5f ? a : b;
    for (const auto& f : SceneParams::lerpedFloats()) {
        const float from = a.*f.member;
        out.*f.member = from + (b.*f.member - from) * k;
    }
    return out;
}

}  // namespace geode::viz
