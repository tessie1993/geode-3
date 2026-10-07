#include "viz/SpatialCameraDirector.hpp"

#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace {
using Director = geode::viz::SpatialCameraDirector;

void expect(bool passed, const char* message) {
    if (!passed) throw std::runtime_error(message);
}

void near(float actual, float expected, float tolerance, const char* message) {
    expect(std::isfinite(actual) && std::fabs(actual - expected) <= tolerance, message);
}

void samePose(const Director::Frame& a, const Director::Frame& b) {
    for (std::size_t i = 0; i < a.position.size(); ++i) {
        near(a.position[i], b.position[i], 0.0f, "stationary camera position changed");
        near(a.right[i], b.right[i], 0.0f, "stationary camera right changed");
        near(a.up[i], b.up[i], 0.0f, "stationary camera up changed");
        near(a.forward[i], b.forward[i], 0.0f, "stationary camera forward changed");
    }
    near(a.travel, b.travel, 0.0f, "stationary camera advanced travel");
    near(a.roll, b.roll, 0.0f, "stationary camera bank changed");
}

Director::Signals music() {
    Director::Signals signals;
    signals.bass = 0.9f;
    signals.mid = 0.5f;
    signals.treble = 0.7f;
    signals.energy = 0.8f;
    return signals;
}

void run(Director& director, Director::Signals signals, const Director::Intent& intent, int seconds, int fps = 60) {
    for (int i = 0; i < seconds * fps; ++i) director.step(signals, intent, 1.0f / static_cast<float>(fps));
}

void stationaryContracts() {
    Director director(18);
    Director::Intent intent;
    intent.motion = 0.0f;
    auto before = director.frame();
    run(director, music(), intent, 12);
    samePose(before, director.frame());
    near(director.frame().formPhase, before.formPhase, 0.0f, "Motion zero must stop automatic sculpture turning");
    expect(director.frame().bands[0] > 0.5f, "stationary camera must retain live audio material response");
    intent.motion = 0.8f;
    run(director, music(), intent, 8);
    expect(director.frame().travel > 1.0f, "enabled camera must travel");
    before = director.frame();
    intent.reducedMotion = true;
    auto changing = music();
    changing.section = true;
    run(director, changing, intent, 12);
    samePose(before, director.frame());
    near(director.frame().formPhase, before.formPhase, 0.0f, "reduced motion must stop automatic sculpture turning");
    intent.reducedMotion = false;
    director.step(changing, intent, 1.0f / 60.0f);
    expect(director.frame().travel - before.travel < 0.003f, "resume must accelerate without catch-up");
    before = director.frame();
    intent.motion = 0.0f;
    run(director, music(), intent, 8);
    samePose(before, director.frame());
    intent.motion = 1.0f;
    intent.speed = 0.0f;
    run(director, music(), intent, 8);
    samePose(before, director.frame());
}

void audioChangesTravelWithoutTeleporting() {
    Director quiet(119), bassDriven(119), midDriven(119);
    Director::Intent intent;
    Director::Signals bass;
    bass.bass = 1.0f;
    Director::Signals mids;
    mids.mid = 1.0f;
    run(quiet, {}, intent, 10);
    run(bassDriven, bass, intent, 10);
    run(midDriven, mids, intent, 10);
    expect(bassDriven.frame().travel > quiet.frame().travel + 1.0f, "bass must change native travel");
    near(midDriven.frame().travel, quiet.frame().travel, 1e-5f, "mid timbre must not impersonate bass travel");
    expect(std::fabs(midDriven.frame().formPhase - quiet.frame().formPhase) > 0.1f,
           "mids must change sculpture evolution");
    const auto before = quiet.frame();
    quiet.step(music(), intent, 1.0f / 60.0f);
    expect(quiet.frame().travel - before.travel < 0.02f, "audio onset must not relocate the camera");
}

void cadenceUsesOneIntegrationClock() {
    Director thirty(23), sixty(23), oneTwenty(23);
    const Director::Intent intent;
    run(thirty, music(), intent, 90, 30);
    run(sixty, music(), intent, 90, 60);
    run(oneTwenty, music(), intent, 90, 120);
    for (std::size_t i = 0; i < 3; ++i) {
        near(thirty.frame().position[i], sixty.frame().position[i], 2e-4f, "30/60 fps changed the route");
        near(oneTwenty.frame().position[i], sixty.frame().position[i], 2e-4f, "60/120 fps changed the route");
        near(thirty.frame().forward[i], sixty.frame().forward[i], 2e-4f, "frame cadence changed aim");
    }
}

void liveSessionsVaryAndHeldSectionsAreOneEvent() {
    Director first, second;
    expect(first.frame().corridorPhase != second.frame().corridorPhase, "live sessions must receive fresh entropy");
    Director held(71), edge(71), different(72);
    Director::Intent intent;
    const float dt = 1.0f / 60.0f;
    run(held, music(), intent, 3);
    run(edge, music(), intent, 3);
    for (int i = 0; i < 300; ++i) {
        auto signals = music();
        signals.section = true;
        held.step(signals, intent, dt);
        signals.section = i == 0;
        edge.step(signals, intent, dt);
    }
    samePose(held.frame(), edge.frame());
    run(different, music(), intent, 8);
    expect(held.frame().position != different.frame().position, "different entropy must alter composition");
}

void validateBounds(const Director& director) {
    const auto& frame = director.frame();
    expect(std::isfinite(frame.travel) && frame.travel >= 0.0f && frame.travel < Director::kCorridorPeriod,
           "travel must remain bounded in long sessions");
    expect(std::isfinite(frame.formPhase) && frame.formPhase >= 0.0f && frame.formPhase < 6.284f,
           "form clock must remain bounded");
    expect(std::isfinite(frame.roll) && std::fabs(frame.roll) <= 0.131f, "bank must remain bounded");
    const auto centre = director.corridorCenter(frame.travel);
    const float x = frame.position[0] - centre[0];
    const float y = frame.position[1] - centre[1];
    expect(std::hypot(x, y) <= 0.241f, "camera must remain inside the corridor clearance");
    for (float level : frame.bands) expect(std::isfinite(level) && level >= 0.0f && level <= 1.501f, "band invalid");
    float forwardLength = 0.0f;
    float rightLength = 0.0f;
    float orthogonal = 0.0f;
    for (std::size_t i = 0; i < 3; ++i) {
        expect(std::isfinite(frame.position[i]) && std::isfinite(frame.up[i]), "pose must stay finite");
        forwardLength += frame.forward[i] * frame.forward[i];
        rightLength += frame.right[i] * frame.right[i];
        orthogonal += frame.right[i] * frame.forward[i];
    }
    near(forwardLength, 1.0f, 1e-5f, "forward basis not normalized");
    near(rightLength, 1.0f, 1e-5f, "right basis not normalized");
    near(orthogonal, 0.0f, 1e-5f, "basis not orthogonal");
}

void invalidInputsAndLongSessionsStayFinite() {
    Director director(4);
    Director::Intent intent;
    intent.motion = 1.0f;
    intent.orbit = 1.0f;
    intent.speed = 3.0f;
    auto signals = music();
    float previous = director.frame().travel;
    int wraps = 0;
    for (int i = 0; i < 600 * 60; ++i) {
        director.step(signals, intent, 1.0f / 60.0f);
        if (director.frame().travel < previous) ++wraps;
        float change = director.frame().travel - previous;
        if (change < 0.0f) change += Director::kCorridorPeriod;
        expect(change < 0.081f, "travel stepped discontinuously");
        previous = director.frame().travel;
        validateBounds(director);
    }
    expect(wraps > 5, "long-session regression did not cross the wrap");
    const auto first = director.corridorCenter(0.0f);
    const auto repeat = director.corridorCenter(Director::kCorridorPeriod);
    near(first[0], repeat[0], 1e-5f, "world seam differs in x");
    near(first[1], repeat[1], 1e-5f, "world seam differs in y");

    auto before = director.frame();
    director.step(signals, intent, std::numeric_limits<float>::infinity());
    director.step(signals, intent, std::numeric_limits<float>::quiet_NaN());
    director.step(signals, intent, -1.0f);
    samePose(before, director.frame());
    signals.bass = std::numeric_limits<float>::quiet_NaN();
    signals.mid = std::numeric_limits<float>::infinity();
    signals.treble = -std::numeric_limits<float>::infinity();
    signals.energy = std::numeric_limits<float>::max();
    intent.motion = std::numeric_limits<float>::quiet_NaN();
    intent.orbit = std::numeric_limits<float>::infinity();
    run(director, signals, intent, 3);
    samePose(before, director.frame());
    validateBounds(director);

    intent.motion = 1.0f;
    before = director.frame();
    director.step(music(), intent, 3600.0f);
    float advance = director.frame().travel - before.travel;
    if (advance < 0.0f) advance += Director::kCorridorPeriod;
    expect(advance <= 0.5f, "stalled surface must not catch up a large interval");
    validateBounds(director);
}
}  // namespace

int main() {
    try {
        stationaryContracts();
        audioChangesTravelWithoutTeleporting();
        cadenceUsesOneIntegrationClock();
        liveSessionsVaryAndHeldSectionsAreOneEvent();
        invalidInputsAndLongSessionsStayFinite();
        std::cout << "SpatialCameraDirector tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
