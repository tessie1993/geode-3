#pragma once
#include <GLES3/gl3.h>

#include <array>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

#include "viz/CameraRig.hpp"
#include "viz/MotionField.hpp"
#include "viz/Program.hpp"
#include "viz/Scene.hpp"
#include "viz/SceneAudioResponse.hpp"
#include "viz/Texture.hpp"

namespace geode::viz {

// Port of ShaderScene.kt: one fragment style on the fullscreen triangle, fed the shared uniform block.
class ShaderScene : public Scene {
public:
    static constexpr int kAudioTexWidth = 512;

    ShaderScene(std::string id, std::string vertexSrc, std::string fragmentSrc, ProgramBinaryCache* cache, SceneHost host);
    ~ShaderScene() override;

    const std::string& id() const override { return id_; }
    SceneFamily family() const override { return SceneFamily::Shader; }
    void init() override;
    void setParams(const SceneParams& params) override { params_ = params; }
    void resize(int width, int height) override;
    void update(const GeodeFeatureFrame& features, float dt) override;
    void draw(float timeSeconds) override;
    void release() override;

    void acceptPcm(const float* samples, int count) override;
    void setFlow(GLuint texture, float strength) override;
    void setPaletteLut(GLuint texture) override { paletteLutTex_ = texture; }
    void setTouchField(const TouchField* field) override { touchField_ = field; }
    void setFragmentSource(const std::string& source) override;

private:
    static constexpr float kTimeWrapSeconds = 7100.0f;
    static constexpr float kTwoPi = 6.2831853f;
    static constexpr float kAudioClamp = 1.5f;

    // MotionField supplies continuous musical structure, SceneAudioResponse
    // supplies fast local geometry envelopes, and CameraRig deliberately
    // filters audio more slowly. All retain state on the owning GL thread.

    void compilePendingIfAny();
    void uploadParams();
    void uploadMotion();
    void uploadCamera();
    void uploadTouch();
    void set1f(const char* name, float value);
    float marchSteps(float detail);

    std::string id_;
    std::string vertexSrc_;
    std::string currentFragment_;
    ProgramBinaryCache* cache_;
    SceneHost host_;

    GLuint program_ = 0;
    UniformCache uniforms_;
    GLuint vao_ = 0;
    Texture audioTex_;
    int width_ = 1;
    int height_ = 1;

    std::mutex pendingLock_;
    std::optional<std::string> pendingFragment_;
    bool pendingIsUserSource_ = false;

    std::array<float, kAudioTexWidth * 2> texData_{};
    float bass_ = 0.0f;
    float mid_ = 0.0f;
    float treble_ = 0.0f;
    float energy_ = 0.0f;
    // The analyser's own phase-locked beat phase (0..1 of a cycle), cached
    // from update() for draw()'s uBeatPhase upload; not reset by a transient.
    float beatPhase_ = 0.0f;

    // The motion layer's integrated state. All of it survives from frame to
    // frame; none of it can be reconstructed inside the shader.
    SceneAudioResponse audioResponse_;
    CameraRig camera_;
    // Legacy spike/spawn uniforms remain neutral; local accents have their
    // own uAccent uniform so they cannot revive full-frame beat flashes.
    MotionField motionField_;
    SceneParams params_;
    float rotationAngle_ = 0.0f;
    float zoomPhase_ = 0.0f;
    float cyclePhase_ = 0.0f;
    float shaderTime_ = 0.0f;
    double flowPhase_ = 0.0;

    std::vector<float> pcm_;
    int pcmCount_ = 0;
    std::array<float, kAudioTexWidth> waveRow_{};

    GLuint flowTex_ = 0;
    float flowStrength_ = 0.0f;
    GLuint paletteLutTex_ = 0;
    const TouchField* touchField_ = nullptr;
    std::array<float, TouchField::kPointStride> touchAnchor_{};
    std::array<float, TouchField::kMaxPoints * TouchField::kPointStride> touchPoints_{};

    float stepsDetail_ = -1.0f;
    float stepsBudget_ = 0.0f;
};

}  // namespace geode::viz
