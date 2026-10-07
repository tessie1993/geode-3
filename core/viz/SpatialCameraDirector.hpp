#pragma once

#include <array>
#include <cstdint>

namespace geode::viz {

// Native state for Prismatic Passage. Coordinates are world-space scene units;
// +z follows the corridor, with camera and geometry sharing corridorCenter().
// All updates are allocation-free. ShaderScene init/release retain this state;
// Renderer::releaseScenes currently destroys it on full context recreation.
class SpatialCameraDirector {
public:
    static constexpr float kCorridorPeriod = 96.0f;
    static constexpr float kCorridorRadius = 2.1f;

    struct Signals {
        float bass = 0.0f;
        float mid = 0.0f;
        float treble = 0.0f;
        float energy = 0.0f;
        bool section = false;
    };

    struct Intent {
        float motion = 0.7f;
        float orbit = 0.5f;
        float speed = 1.0f;
        bool reducedMotion = false;
    };

    struct Frame {
        std::array<float, 3> position{};
        std::array<float, 3> right{1.0f, 0.0f, 0.0f};
        std::array<float, 3> up{0.0f, 1.0f, 0.0f};
        std::array<float, 3> forward{0.0f, 0.0f, 1.0f};
        // Phase offsets used by the shader's identical corridor equation.
        std::array<float, 2> corridorPhase{};
        // Smoothed band envelopes, independent of camera travel permission.
        std::array<float, 4> bands{};
        float travel = 0.0f;
        float formPhase = 0.0f;
        float roll = 0.0f;
    };

    SpatialCameraDirector();
    // Explicit entropy is for test fixtures only; live code uses the constructor above.
    explicit SpatialCameraDirector(uint64_t fixtureEntropy);
    void step(const Signals& signals, const Intent& intent, float dt);
    const Frame& frame() const { return frame_; }
    std::array<float, 2> corridorCenter(float z) const;

private:
    void tick(const Signals& signals, const Intent& intent);
    void chooseTarget();
    void updatePose();
    float random01();

    uint64_t entropy_;
    Frame frame_;
    double accumulator_ = 0.0;
    double travel_ = 0.0;
    double formPhase_ = 0.0;
    float lateralX_ = 0.0f;
    float lateralY_ = 0.0f;
    float lateralAimX_ = 0.0f;
    float lateralAimY_ = 0.0f;
    float rollAim_ = 0.0f;
    float lateralTargetX_ = 0.0f;
    float lateralTargetY_ = 0.0f;
    float rollTarget_ = 0.0f;
    float speed_ = 0.0f;
    float targetAge_ = 0.0f;
    float targetLife_ = 0.0f;
    bool sectionHeld_ = false;
};

}  // namespace geode::viz
