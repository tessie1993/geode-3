#include "viz/SceneAudioResponse.hpp"

#include <cmath>
#include <initializer_list>
#include <iostream>
#include <stdexcept>

namespace {
using geode::viz::SceneAudioResponse;
void expect(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
void expectNear(float a, float b, float tolerance, const char* message) {
    expect(std::isfinite(a) && std::fabs(a - b) <= tolerance, message);
}
void bandEnvelopesHaveDifferentTimeScales(int fps) {
    SceneAudioResponse response;
    GeodeFeatureFrame frame{};
    frame.rms = frame.bass = frame.mid = frame.treble = 1.0f;
    for (int i = 0; i < fps / 10; ++i) response.step(frame, 1.0f, 1.0f / fps);
    const auto raised = response.state();
    expect(raised.treble > raised.bass && raised.bass > raised.mid,
           "highlights must attack faster than bass and mid deformation");
    expect(raised.bass > 0.95f && raised.mid > 0.85f && raised.treble > 0.99f,
           "geometry envelopes must react within 100 milliseconds");
    expect(raised.swell < 0.13f, "slow passage energy must not follow individual drum hits");
    frame = {};
    for (int i = 0; i < fps / 5; ++i) response.step(frame, 1.0f, 1.0f / fps);
    expect(response.state().treble < response.state().mid && response.state().mid < response.state().bass,
           "highlights must release faster than body deformation");
}
void heldFeaturePulsesFireOnce() {
    SceneAudioResponse response;
    GeodeFeatureFrame frame{};
    frame.beat = 1.0f;
    frame.beatStrength = 0.8f;
    frame.transient = 0.6f;
    const float dt = 1.0f / 60.0f;
    response.step(frame, 1.0f, dt);
    expectNear(response.state().accent, 0.8f, 1e-6f, "simultaneous impulses must use their maximum");
    for (int i = 0; i < 60; ++i) response.step(frame, 1.0f, dt);
    expect(response.state().accent < 0.004f, "cached held feature pulses must not retrigger");
    frame.beat = 0.0f;
    response.step(frame, 1.0f, dt);
    frame.beat = 1.0f;
    frame.beatStrength = 0.5f;
    response.step(frame, 1.0f, dt);
    expectNear(response.state().accent, 0.5f, 1e-6f,
               "a fresh beat edge must work while the transient channel stays held");
    frame.beat = 0.0f;
    frame.transient = 0.0f;
    response.step(frame, 1.0f, dt);
    frame.transient = 1.0f;
    response.step(frame, 1.0f, dt);
    expectNear(response.state().accent, 1.0f, 1e-6f, "a fresh transient edge must be captured");
}
void mutedDriveAndSilenceSettle() {
    SceneAudioResponse muted;
    GeodeFeatureFrame frame{};
    frame.rms = frame.bass = frame.mid = frame.treble = frame.beat = frame.transient = 1.0f;
    muted.step(frame, 0.0f, 1.0f / 60.0f);
    const auto silent = muted.state();
    expect(silent.bass == 0.0f && silent.mid == 0.0f && silent.treble == 0.0f && silent.accent == 0.0f,
           "muted audio drive must leave every response channel silent");
    SceneAudioResponse response;
    for (int i = 0; i < 600; ++i) response.step(frame, 1.0f, 1.0f / 60.0f);
    frame = {};
    for (int i = 0; i < 600; ++i) response.step(frame, 1.0f, 1.0f / 60.0f);
    const auto settled = response.state();
    expect(settled.bass < 1e-6f && settled.mid < 1e-6f && settled.treble < 1e-6f && settled.accent < 1e-6f,
           "stale or silent input must decay geometry and accents");
}
}  // namespace

int main() {
    try {
        for (int fps : {30, 60, 120}) bandEnvelopesHaveDifferentTimeScales(fps);
        heldFeaturePulsesFireOnce();
        mutedDriveAndSilenceSettle();
        std::cout << "SceneAudioResponse tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
