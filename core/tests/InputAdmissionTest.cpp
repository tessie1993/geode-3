#include "viz/InputAdmission.hpp"
#include "viz/Params.hpp"

#include <array>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string_view>
#include <vector>

namespace {
using geode::viz::AdsrConfig;
using geode::viz::LfoConfig;
using geode::viz::LfoTarget;
using geode::viz::SceneParams;
namespace admission = geode::viz::admission;
using ParamFrame = std::array<float, SceneParams::kFieldCount>;

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

ParamFrame pack(const SceneParams& params) {
    ParamFrame values{};
    const auto& names = SceneParams::fieldNames();
    for (size_t i = 0; i < names.size(); ++i) {
        require(names[i] != nullptr && params.get(names[i], values[i]), "wire field has no typed admission descriptor");
        for (size_t j = 0; j < i; ++j) {
            require(std::string_view(names[i]) != names[j], "duplicate wire field");
        }
    }
    return values;
}

size_t indexOf(std::string_view name) {
    const auto& names = SceneParams::fieldNames();
    for (size_t i = 0; i < names.size(); ++i) if (name == names[i]) return i;
    throw std::runtime_error("missing parameter in test fixture");
}

void everyWireFieldRejectsNonfiniteValuesWithoutPartialPublication() {
    SceneParams previous;
    previous.speed = 2.5f;
    previous.palette = 19;
    const auto retained = pack(previous);
    const auto defaults = pack(SceneParams{});
    require(previous.valid(), "valid existing preset was rejected");
    SceneParams decoded;
    require(decoded.setFrame(defaults.data(), defaults.size()), "default full frame rejected");
    require(pack(decoded) == defaults, "default roundtrip changed wire values");

    for (size_t i = 0; i < defaults.size(); ++i) {
        for (float invalid : {std::numeric_limits<float>::quiet_NaN(),
                              std::numeric_limits<float>::infinity(),
                              -std::numeric_limits<float>::infinity(),
                              std::numeric_limits<float>::max()}) {
            auto frame = defaults;
            frame[i] = invalid;
            require(!previous.setFrame(frame.data(), frame.size()), "invalid frame accepted");
            require(pack(previous) == retained, "invalid frame partially replaced the previous preset");
            require(!previous.set(SceneParams::fieldNames()[i], invalid), "named ingress disagrees with bulk rejection");
            require(pack(previous) == retained, "invalid named input changed a parameter");
        }
    }
    require(!previous.setFrame(nullptr, defaults.size()), "null parameter frame accepted");
    require(!previous.setFrame(defaults.data(), defaults.size() - 1), "short parameter frame accepted");
    require(!previous.setFrame(defaults.data(), defaults.size() + 1), "long parameter frame accepted");
    require(pack(previous) == retained, "wrong-size frame changed previous state");
}

void legalUnitsSignedControlsAndSentinelsMatchNamedAndBulkPaths() {
    struct Example { const char* name; float value; };
    const Example values[] = {
        {"speed", 0.0f}, {"zoom", 0.3f}, {"rotation", -3.0f},
        {"driftX", -1.0f}, {"driftY", 1.0f}, {"twist", -1.0f},
        {"trailZoom", -0.5f}, {"temperature", -1.0f}, {"fisheye", -1.0f},
        {"cymaticsSwirl", -1.0f}, {"palette", 20.0f}, {"paletteLut", -1.0f},
        {"paletteBaseOverride", -1.0f}, {"paletteRangeOverride", -1.0f},
        {"palette2BaseOverride", -1.0f}, {"palette2RangeOverride", -1.0f},
        {"cymaticsFundamental", 440.0f}, {"fluidIterations", 40.0f},
        {"fluidParticleLife", 20.0f}, {"fluidCurl", 50.0f}, {"fluidSpawnPoints", 8.0f},
        {"waterDamping", 0.999f}, {"symmetry", 0.0f}, {"paramFadeSec", 5.0f},
        {"fluidSunraysWeight", 1.2f}, // shipped Spectrum preset, capped inside FluidScene
        // Preserve the legacy named float-to-int/flag behavior in legal ranges.
        {"symmetry", 5.6f}, {"trails", 0.6f},
        {"paletteBaseOverride", -0.5f}, {"paletteLut", -0.5f},
    };
    for (const auto& example : values) {
        SceneParams named;
        require(named.set(example.name, example.value), "valid named value rejected");
        auto frame = pack(SceneParams{});
        frame[indexOf(example.name)] = example.value;
        SceneParams bulk;
        require(bulk.setFrame(frame.data(), frame.size()), "valid bulk value rejected");
        require(pack(named) == pack(bulk), "named and bulk coercion differ");
    }
    SceneParams p;
    require(!p.set("paletteBaseOverride", -1.01f), "out-of-range negative override accepted");
    require(!p.set("paletteLut", -2.0f), "out-of-range negative LUT accepted");
    require(!p.set("fluidIterations", 41.0f), "excessive solver iterations accepted");
    require(!p.set("gamma", 0.0f), "invalid gamma accepted");
    require(!p.set("notAParameter", 0.0f), "unknown field accepted");
    p.symmetry = std::numeric_limits<int>::max();
    require(!p.valid(), "direct typed integer frame bypassed validation");
}

void featureFramesPreserveSignedAudioAndRejectAtomically() {
    GeodeFeatureFrame state{};
    state.rms = 0.4f;
    state.bpm = 180.0f;
    state.beatInBar = 3.0f;
    state.stereoCorrelation = -1.0f;
    state.stereoPan = -0.7f;
    state.waveform[0] = -1.25f; // raw floating PCM can exceed full scale after gain
    state.bands[GEODE_BAND_COUNT - 1] = 1.0f;
    state.chroma[GEODE_CHROMA_BINS - 1] = 1.0f;
    require(admission::validFeatures(state), "signed stereo, PCM, BPM or bar index was treated as a 0..1 level");
    auto incoming = state;
    incoming.rms = 0.9f;
    incoming.waveform[GEODE_WAVEFORM_POINTS - 1] = std::numeric_limits<float>::quiet_NaN();
    require(!admission::publishFeatures(state, incoming), "NaN at end of waveform accepted");
    require(state.rms == 0.4f && state.waveform[0] == -1.25f, "invalid feature frame partially published");
    incoming = state;
    incoming.chroma[GEODE_CHROMA_BINS - 1] = std::numeric_limits<float>::infinity();
    require(!admission::publishFeatures(state, incoming), "infinite final chroma bin accepted");
    incoming = state;
    incoming.beatInBar = 4.0f;
    require(!admission::validFeatures(incoming), "invalid four-beat bar index accepted");
    incoming = state;
    incoming.stereoPan = -1.1f;
    require(!admission::validFeatures(incoming), "out-of-range pan accepted");
    incoming = state;
    incoming.bands[0] = -0.1f;
    require(!admission::validFeatures(incoming), "negative normalized band accepted");
    incoming = state;
    incoming.rms = 0.8f;
    require(admission::publishFeatures(state, incoming) && state.rms == 0.8f,
            "valid feature recovery failed after rejected input");
}

void modulationRejectsInvalidEnumsBeforeConversionAndRetainsConfig() {
    std::array<float, GEODE_LFO_CONFIG_FLOATS> lfo = {1, 0, 1, 0, 2, 0.3f, 0, 0};
    LfoConfig oldLfo;
    require(admission::decodeLfo(lfo.data(), lfo.size(), oldLfo), "valid LFO rejected");
    for (int index : {1, 2, 3, 6, 7}) {
        auto bad = lfo;
        bad[index] = std::numeric_limits<float>::max();
        require(!admission::decodeLfo(bad.data(), bad.size(), oldLfo), "overflowing enum reached LFO");
        require(oldLfo.enabled && oldLfo.target == LfoTarget::Speed, "bad LFO changed prior configuration");
    }
    auto bad = lfo;
    bad[4] = 0.0f;
    require(!admission::decodeLfo(bad.data(), bad.size(), oldLfo), "zero LFO period accepted");
    bad = lfo;
    bad[5] = std::numeric_limits<float>::quiet_NaN();
    require(!admission::decodeLfo(bad.data(), bad.size(), oldLfo), "nonfinite depth accepted");

    std::vector<float> adsr = {1, 0.05f, 0.25f, 0.5f, 0.35f, 0.5f, 0, 0.25f, 0, 1,
                              static_cast<float>(LfoTarget::Zoom)};
    AdsrConfig oldAdsr;
    require(admission::decodeAdsr(adsr.data(), adsr.size(), oldAdsr), "valid envelope rejected");
    adsr[1] = 0.9f;
    adsr.back() = std::numeric_limits<float>::infinity();
    require(!admission::decodeAdsr(adsr.data(), adsr.size(), oldAdsr), "invalid final envelope target accepted");
    require(oldAdsr.attack == 0.05f && oldAdsr.targets == std::vector<LfoTarget>{LfoTarget::Zoom},
            "invalid final target partially published envelope config");
    require(!admission::decodeAdsr(adsr.data(), std::numeric_limits<int>::max(), oldAdsr),
            "unbounded envelope target count accepted");
    require(!admission::decodeAdsr(nullptr, 10, oldAdsr), "null envelope accepted");
}
}  // namespace

int main() {
    try {
        everyWireFieldRejectsNonfiniteValuesWithoutPartialPublication();
        legalUnitsSignedControlsAndSentinelsMatchNamedAndBulkPaths();
        featureFramesPreserveSignedAudioAndRejectAtomically();
        modulationRejectsInvalidEnumsBeforeConversionAndRetainsConfig();
        std::cout << "Native input admission tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
