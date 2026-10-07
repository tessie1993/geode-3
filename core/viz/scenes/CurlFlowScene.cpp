#include "viz/scenes/CurlFlowScene.hpp"

#include <algorithm>
#include <cmath>

#include "viz/LiveSignal.hpp"
#include "viz/fluid/AllocationPlan.hpp"
#include "viz/fluid/FluidMath.hpp"
#include "viz/fluid/FluidQuality.hpp"

namespace geode::viz {

void CurlFlowScene::init() {
    release();
    formats_ = fluid::probeFormats();
    available_ = formats_.ok;
    if (!available_) {
        host_.onShaderError("Curl Flow solver unavailable on this GPU; showing a recovery visual");
        return;
    }
    choreography_.reset();
    quad_.create();
    std::string error;
    const GLuint program = loader_.build("fluid_base_vert.glsl", "curl_field_frag.glsl", &error);
    if (program == 0) {
        host_.onShaderError("Curl Flow unavailable on this GPU: " + error);
        release();
        return;
    }
    fieldUniforms_ = UniformCache(program);
    appliedTier_ = -1;
    applyQualityTier();
}

void CurlFlowScene::resize(int width, int height) {
    width_ = std::max(width, 1);
    height_ = std::max(height, 1);
    aspect_ = static_cast<float>(width_) / static_cast<float>(height_);
    if (!available_) return;
    fluid::AllocationState state;
    for (int attempt = 0; attempt < fluid::kAllocationAttempts; ++attempt) {
        const int res = fluid::allocationRequest(fieldRes_, fieldRes_, attempt).sim;
        const auto [fw, fh] = fluid::resolution(res, width_, height_);
        std::optional<fluid::Fbo> field(std::in_place, fw, fh, formats_.rg, true);
        field->create();
        if (!field->ok()) continue;
        field_ = std::move(field);
        particles_.invalidateSeed();
        allocationErrorReported_ = false;
        return;
    }
    if (!allocationErrorReported_) {
        host_.onShaderError(field_ ? "Curl Flow resize refused by GPU; keeping the previous buffer"
                                  : "Curl Flow buffer refused by GPU; showing a recovery visual");
        allocationErrorReported_ = true;
    }
}

void CurlFlowScene::onApplyQualityTier(int index, bool userChanged) {
    (void) userChanged;
    if (!available_) return;
    const auto& tier = fluid::quality::tier(index);
    fieldRes_ = std::min(tier.simRes, 96);
    const int requestedSide = std::min(tier.particleSide, 224);
    for (int attempt = 0; attempt < fluid::kAllocationAttempts; ++attempt) {
        const int side = fluid::particleSideRequest(requestedSide, attempt);
        particles_.create(side * side, formats_);
        if (particles_.available()) break;
    }
    if (!particles_.available()) host_.onShaderError("Curl Flow particle buffers refused by GPU; showing a recovery visual");
    if (width_ > 1 && height_ > 1) resize(width_, height_);
}

void CurlFlowScene::update(const GeodeFeatureFrame& features, float dt) {
    pending_ = features;
    hasPending_ = true;
    lastDt_ = std::clamp(dt, 0.0f, 1.0f / 30.0f);
    pcmKick_ = std::clamp(fluid::math::driven(tickPcm(dt), params_.audioDrive), 0.0f, 1.0f);
}

void CurlFlowScene::draw(float timeSeconds) {
    (void) timeSeconds;
    resetFrameState();
    saveGlState();
    autoQualityTick();
    if (!available_ || !field_ || !particles_.available()) {
        restoreFramebufferAndViewport();
        const auto f = hasPending_ ? fluid::scaledFeatures(pending_, params_.audioDrive) : GeodeFeatureFrame{};
        recovery_.draw(loader_, params_, f, lastDt_, 10, host_.onShaderError);
        hasPending_ = false;
        restoreBlend();
        return;
    }
    const fluid::Fbo& fld = *field_;

    if (hasPending_) {
        const GeodeFeatureFrame f = fluid::scaledFeatures(pending_, params_.audioDrive);
        wallTime_ = std::fmod(wallTime_ + lastDt_, kWallWrapSeconds);
        beatEnv_ = std::max(live::hit(f), beatEnv_ * std::exp(-lastDt_ / 0.35f));
        beatDrive_ = fluid::curl::beatDrive(beatEnv_, params_.beatResponse);
        noiseTime_ = std::fmod(noiseTime_ + lastDt_ * (0.15f + f.mid * 1.4f) * fluid::Choreography::sceneSpeed(params_.speed), kNoiseWrapSeconds);

        configureChoreography();
        choreography_.tick(f, lastDt_, aspect_);

        glDisable(GL_BLEND);
        quad_.bind();
        glUseProgram(fieldUniforms_.program());
        glUniform2f(fieldUniforms_.loc("uInvRes"), 1.0f / static_cast<float>(fld.width()), 1.0f / static_cast<float>(fld.height()));
        glUniform1f(fieldUniforms_.loc("uAspect"), aspect_);
        glUniform1f(fieldUniforms_.loc("uTime"), noiseTime_);
        glUniform1f(fieldUniforms_.loc("uFreq"), 1.2f * (0.5f + std::clamp(params_.turbulence, 0.1f, 2.0f)));
        glUniform1f(fieldUniforms_.loc("uDetail"), std::clamp(f.treble * 3.0f + pcmKick_ * 0.8f, 0.0f, 1.5f));
        glUniform1f(fieldUniforms_.loc("uAmp"), fluid::curl::fieldAmp(params_.audioDrive, beatDrive_) * (1.0f + pcmKick_ * 0.35f));
        glUniform2f(fieldUniforms_.loc("uPeriod"), 0.0f, 0.0f);
        glBindFramebuffer(GL_FRAMEBUFFER, fld.fbo());
        // The field is rebuilt from noise every audio frame, so nothing carries over: discard, then fill.
        fld.discardContents();
        glViewport(0, 0, fld.width(), fld.height());
        glDrawArrays(GL_TRIANGLES, 0, 3);
        quad_.unbind();

        applyChoreographyTo(particles_);
        particles_.step(lastDt_, fld.tex(), aspect_, 1.0f, wallTime_);
        hasPending_ = false;
    }

    restoreFramebufferAndViewport();
    particles_.draw(aspect_, std::clamp(params_.particleSize, 0.4f, 4.0f) * viewportDpiScale(), params_.paletteBase(),
                    hue::span(params_.hueRange, params_.paletteRange()), fluid::curl::particleBrightness(beatDrive_),
                    static_cast<float>(params_.particleShape), particle_look::glow(params_.bloom), wallTime_);
    restoreBlend();
}

float CurlFlowScene::trailRetention(const SceneParams& params) const { return fluid::curl::retention(params.trailLength, params.trails); }

void CurlFlowScene::release() {
    particles_.release();
    recovery_.release();
    if (field_) field_->release();
    field_.reset();
    if (fieldUniforms_.program() != 0) glDeleteProgram(fieldUniforms_.program());
    fieldUniforms_ = UniformCache(0);
    quad_.release();
    available_ = false;
    allocationErrorReported_ = false;
    appliedTier_ = -1;
}

}  // namespace geode::viz
