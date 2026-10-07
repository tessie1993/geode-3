#pragma once
#include <algorithm>
#include <cmath>
#include <functional>
#include <string>

#include "viz/Params.hpp"
#include "viz/SceneAudioResponse.hpp"
#include "viz/scenes/ProgramLoader.hpp"

namespace geode::viz::fluid {

// Visible recovery for unsupported float attachments or exhausted allocation
// retries. This is a procedural display, not a substitute signed fluid solver.
// It requires no offscreen textures and compiles only when it is actually used.
class RecoveryDisplay {
public:
    ~RecoveryDisplay() { release(); }

    void draw(const ProgramLoader& loader, const SceneParams& params, const GeodeFeatureFrame& audio,
              float dt, int look, const std::function<void(const std::string&)>& onError, float hueOffset = 0.0f) {
        dt = std::isfinite(dt) ? std::clamp(dt, 0.0f, 0.1f) : 0.0f;
        const float speed = std::isfinite(params.speed) ? std::max(params.speed, 0.0f) : 0.0f;
        phase_ = std::fmod(phase_ + dt * speed, 7100.0f);
        // Keep the display muted even if a caller supplied an unfiltered frame.
        const auto input = std::isfinite(params.audioDrive) && params.audioDrive > 0.0f ? audio : GeodeFeatureFrame{};
        response_.step(input, 1.0f, dt);  // callers already applied audioDrive
        if (program_.program() == 0 && !attempted_) {
            attempted_ = true;
            std::string error;
            const GLuint linked = loader.buildSource(kVertex, kFragment, &error);
            program_ = UniformCache(linked);
            if (linked != 0) glGenVertexArrays(1, &vao_);
            else if (onError) onError("Fluid recovery shader unavailable: " + error);
        }
        if (program_.program() == 0) {
            // Even a driver refusing this small shader gets a visible surface.
            GLfloat clearColor[4]{};
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            glClearColor(0.08f, 0.06f, 0.12f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            return;
        }
        GLint viewport[4]{};
        glGetIntegerv(GL_VIEWPORT, viewport);
        glDisable(GL_BLEND);
        glUseProgram(program_.program());
        glUniform2f(program_.loc("uResolution"), static_cast<float>(std::max(viewport[2], 1)),
                    static_cast<float>(std::max(viewport[3], 1)));
        glUniform1f(program_.loc("uTime"), phase_);
        glUniform1f(program_.loc("uHue"), params.paletteBase() + hueOffset + params.colorShift);
        glUniform1f(program_.loc("uHueSpan"), params.paletteRange());
        const auto& envelopes = response_.state();
        glUniform4f(program_.loc("uAudio"), envelopes.bass, envelopes.mid, envelopes.treble, envelopes.energy);
        glUniform1f(program_.loc("uHit"), envelopes.accent * std::clamp(params.beatResponse, 0.0f, 2.0f));
        glUniform1i(program_.loc("uLook"), look);
        glBindVertexArray(vao_);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    void release() {
        if (program_.program() != 0) glDeleteProgram(program_.program());
        if (vao_ != 0) glDeleteVertexArrays(1, &vao_);
        program_ = UniformCache(0);
        vao_ = 0;
        attempted_ = false;
        phase_ = 0.0f;
        response_ = SceneAudioResponse{};
    }

private:
    static constexpr const char* kVertex = R"GLSL(#version 300 es
precision highp float;
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)GLSL";
    static constexpr const char* kFragment = R"GLSL(#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform vec2 uResolution;
uniform float uTime, uHue, uHueSpan, uHit;
uniform vec4 uAudio;
uniform int uLook;
vec3 color(float h) {
    vec3 p = abs(fract(vec3(h) + vec3(0.0, 0.666667, 0.333333)) * 6.0 - 3.0);
    return mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), 0.78);
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0) * 2.0;
    float bass = clamp(uAudio.x, 0.0, 1.5);
    float mid = clamp(uAudio.y, 0.0, 1.5);
    float high = clamp(uAudio.z, 0.0, 1.5);
    float t = uTime * 0.18;
    float style = float(uLook);
    vec2 q = p + 0.18 * sin(p.yx * (2.5 + mid) + vec2(t, -t * 0.7));
    float field = sin(q.x * 3.1 + sin(q.y * 4.0 - t) + t + style * 0.3);
    field += 0.55 * cos(length(q - vec2(0.28 * sin(t), 0.25 * cos(t))) * (6.0 + bass * 3.0) - t * 2.0);
    float ribbon = exp(-abs(field) * (6.0 + high * 7.0));
    float body = 0.5 + 0.5 * sin(field * 2.0 + style * 0.7);
    vec3 tint = color(uHue + uHueSpan * (body * 0.65 + 0.2 * sin(t * 0.3)));
    vec3 c = tint * (0.06 + body * 0.13 + ribbon * (0.45 + bass * 0.28));
    c += color(uHue + 0.12) * ribbon * high * 0.18;
    c += tint * exp(-dot(p, p) * 5.0) * clamp(uHit, 0.0, 1.0) * 0.16;
    if (uLook == 1 || uLook == 7) {
        vec3 paper = uLook == 1 ? vec3(0.93, 0.90, 0.84) : vec3(0.84, 0.86, 0.89);
        c = mix(paper, tint * 0.32, clamp(body * 0.5 + ribbon * 0.35, 0.0, 0.85));
    }
    if (uLook == 9) c += vec3(0.02, 0.07, 0.12) * (0.5 + 0.5 * cos(length(q) * 12.0 - t));
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
)GLSL";
    UniformCache program_{0};
    GLuint vao_ = 0;
    bool attempted_ = false;
    float phase_ = 0.0f;
    SceneAudioResponse response_;
};

}  // namespace geode::viz::fluid
