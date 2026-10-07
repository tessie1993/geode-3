#pragma once

#include <string_view>

#include "viz/Params.hpp"

namespace geode::viz {

// Stateful camera motion is integrated on the render thread. Shader time and
// instantaneous audio levels never multiply a camera position.
class CameraRig {
public:
    struct Vec3 { float x = 0.0f, y = 0.0f, z = 0.0f; };
    struct Frame {
        Vec3 position;
        Vec3 right{1.0f, 0.0f, 0.0f};
        Vec3 up{0.0f, 1.0f, 0.0f};
        Vec3 forward{0.0f, 0.0f, 1.0f};
    };
    enum class Profile { Stationary, CathedralOrbit, BloomOrbit, RodFlight, NectarFlight };

    explicit CameraRig(Profile profile = Profile::Stationary);
    static Profile profileFor(std::string_view sceneId);
    // Shared with dmtTunnelPath in lib_dmt.glsl. Audio never rewrites this path.
    static Vec3 tunnelCenter(double distance);
    void step(float energy, float bass, const SceneParams& params, float dt);
    const Frame& frame() const { return frame_; }
    double travelDistance() const { return distance_; }

private:
    void compose(float orbit, float sway);
    Profile profile_;
    Frame frame_;
    double time_ = 0.0;
    double yaw_ = 0.0;
    double distance_ = 0.0;
    float speed_ = 0.0f;
    float turnRate_ = 0.0f;
    float energy_ = 0.0f;
    float bass_ = 0.0f;
    float dolly_ = 0.0f;
    float orbit_ = 0.0f;
    float sway_ = 0.0f;
};

}  // namespace geode::viz
