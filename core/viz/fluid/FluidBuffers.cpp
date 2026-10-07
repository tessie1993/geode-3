#include "viz/fluid/FluidBuffers.hpp"

#include <algorithm>
#include <cmath>

#include "util/Log.hpp"
#include "viz/Framebuffer.hpp"

namespace geode::viz::fluid {

namespace {

constexpr const char* kTag = "FluidSim";

bool renderable(TexFormat f) {
    AllocationState state;
    GLuint tex = 0;
    GLuint fbo = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexImage2D(GL_TEXTURE_2D, 0, static_cast<GLint>(f.internal), 4, 4, 0, f.format, f.type, nullptr);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    const bool ok = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    glDeleteFramebuffers(1, &fbo);
    glDeleteTextures(1, &tex);
    return ok;
}

void setupTexture(GLuint tex, int w, int h, TexFormat fmt, GLint filter) {
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, static_cast<GLint>(fmt.internal), w, h, 0, fmt.format, fmt.type, nullptr);
}

}  // namespace

AllocationState::AllocationState() {
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawFbo_);
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &readFbo_);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTexture_);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &texture_);
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &texture0_);
    glActiveTexture(static_cast<GLenum>(activeTexture_));
    glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &unpackBuffer_);
    glGetIntegerv(GL_CURRENT_PROGRAM, &program_);
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao_);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &arrayBuffer_);
    glGetIntegerv(GL_VIEWPORT, viewport_);
    glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor_);
    glGetBooleanv(GL_COLOR_WRITEMASK, colorMask_);
    glGetIntegerv(GL_BLEND_SRC_RGB, &blendFunc_[0]);
    glGetIntegerv(GL_BLEND_DST_RGB, &blendFunc_[1]);
    glGetIntegerv(GL_BLEND_SRC_ALPHA, &blendFunc_[2]);
    glGetIntegerv(GL_BLEND_DST_ALPHA, &blendFunc_[3]);
    scissor_ = glIsEnabled(GL_SCISSOR_TEST) == GL_TRUE;
    blend_ = glIsEnabled(GL_BLEND) == GL_TRUE;
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
}

AllocationState::~AllocationState() {
    // A committed resize may have retired an object which was bound on entry.
    // Never resurrect its numeric name while restoring the caller's state.
    if (drawFbo_ != 0 && !glIsFramebuffer(static_cast<GLuint>(drawFbo_))) drawFbo_ = 0;
    if (readFbo_ != 0 && !glIsFramebuffer(static_cast<GLuint>(readFbo_))) readFbo_ = 0;
    if (texture_ != 0 && !glIsTexture(static_cast<GLuint>(texture_))) texture_ = 0;
    if (texture0_ != 0 && !glIsTexture(static_cast<GLuint>(texture0_))) texture0_ = 0;
    if (program_ != 0 && !glIsProgram(static_cast<GLuint>(program_))) program_ = 0;
    if (vao_ != 0 && !glIsVertexArray(static_cast<GLuint>(vao_))) vao_ = 0;
    if (arrayBuffer_ != 0 && !glIsBuffer(static_cast<GLuint>(arrayBuffer_))) arrayBuffer_ = 0;
    if (unpackBuffer_ != 0 && !glIsBuffer(static_cast<GLuint>(unpackBuffer_))) unpackBuffer_ = 0;
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, static_cast<GLuint>(drawFbo_));
    glBindFramebuffer(GL_READ_FRAMEBUFFER, static_cast<GLuint>(readFbo_));
    glViewport(viewport_[0], viewport_[1], viewport_[2], viewport_[3]);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, static_cast<GLuint>(texture0_));
    glActiveTexture(static_cast<GLenum>(activeTexture_));
    glBindTexture(GL_TEXTURE_2D, static_cast<GLuint>(texture_));
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, static_cast<GLuint>(unpackBuffer_));
    glUseProgram(static_cast<GLuint>(program_));
    glBindVertexArray(static_cast<GLuint>(vao_));
    glBindBuffer(GL_ARRAY_BUFFER, static_cast<GLuint>(arrayBuffer_));
    glClearColor(clearColor_[0], clearColor_[1], clearColor_[2], clearColor_[3]);
    glColorMask(colorMask_[0], colorMask_[1], colorMask_[2], colorMask_[3]);
    if (scissor_) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
    if (blend_) glEnable(GL_BLEND); else glDisable(GL_BLEND);
    glBlendFuncSeparate(static_cast<GLenum>(blendFunc_[0]), static_cast<GLenum>(blendFunc_[1]),
                        static_cast<GLenum>(blendFunc_[2]), static_cast<GLenum>(blendFunc_[3]));
}

Formats probeFormats() {
    const TexFormat rgba{GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT};
    const TexFormat rg{GL_RG16F, GL_RG, GL_HALF_FLOAT};
    const TexFormat r{GL_R16F, GL_RED, GL_HALF_FLOAT};
    const TexFormat rgba32{GL_RGBA32F, GL_RGBA, GL_FLOAT};
    const bool rgba32Ok = renderable(rgba32);
    const bool rgbaOk = renderable(rgba);
    const bool rgOk = renderable(rg);
    const bool rOk = renderable(r);
    Formats out;
    out.r = rOk ? r : (rgOk ? rg : rgba);
    out.rg = rgOk ? rg : rgba;
    out.rgba = rgba;
    out.rgba32 = rgba32;
    out.hasRgba32 = rgba32Ok;
    out.ok = rgbaOk;
    GEODE_LOGI(kTag, "fluid formats: R16F=%s RG16F=%s RGBA16F=%s RGBA32F=%s", rOk ? "ok" : "fb", rgOk ? "ok" : "fb",
               rgbaOk ? "ok" : "MISSING", rgba32Ok ? "ok" : "no");
    return out;
}

std::pair<int, int> resolution(int res, int width, int height) {
    if (width <= 0 || height <= 0) return {res, res};
    const float aspect = static_cast<float>(width) / static_cast<float>(height);
    if (aspect >= 1.0f) return {std::max(static_cast<int>(std::lround(res * aspect)), 2), res};
    return {res, std::max(static_cast<int>(std::lround(res / aspect)), 2)};
}

Fbo& Fbo::operator=(Fbo&& o) noexcept {
    if (this != &o) {
        release();
        width_ = o.width_;
        height_ = o.height_;
        fmt_ = o.fmt_;
        linear_ = o.linear_;
        fbo_ = o.fbo_;
        tex_ = o.tex_;
        o.fbo_ = 0;
        o.tex_ = 0;
    }
    return *this;
}

void Fbo::create() {
    if (ok()) return;
    AllocationState state;
    GLint maxSize = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxSize);
    if (width_ < 1 || height_ < 1 || width_ > maxSize || height_ > maxSize) return;
    GLuint tex = 0, fbo = 0;
    glGenTextures(1, &tex);
    setupTexture(tex, width_, height_, fmt_, linear_ ? GL_LINEAR : GL_NEAREST);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        GEODE_LOGW(kTag, "FBO incomplete (%dx%d fmt=0x%x)", width_, height_, static_cast<unsigned>(fmt_.internal));
        glDeleteFramebuffers(1, &fbo);
        glDeleteTextures(1, &tex);
        return;
    }
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    tex_ = tex;
    fbo_ = fbo;
}

void Fbo::discardContents() const {
    if (fbo_ != 0) Framebuffer::discardColorAttachments(GL_FRAMEBUFFER, 1);
}

void Fbo::release() {
    if (tex_ != 0) glDeleteTextures(1, &tex_);
    if (fbo_ != 0) glDeleteFramebuffers(1, &fbo_);
    tex_ = 0;
    fbo_ = 0;
}

GLuint DoubleMrt::Side::makeTex(int w, int h, TexFormat fmt) {
    GLuint tex = 0;
    glGenTextures(1, &tex);
    setupTexture(tex, w, h, fmt, GL_NEAREST);
    return tex;
}

void DoubleMrt::Side::create() {
    if (ok()) return;
    AllocationState state;
    GLint maxSize = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxSize);
    if (width_ < 1 || height_ < 1 || width_ > maxSize || height_ > maxSize) return;
    const GLuint texA = makeTex(width_, height_, fmtA_);
    const GLuint texB = makeTex(width_, height_, fmtB_);
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texA, 0);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, texB, 0);
    const GLenum buffers[2] = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1};
    glDrawBuffers(2, buffers);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        GEODE_LOGW(kTag, "MRT FBO incomplete (%dx%d)", width_, height_);
        glDeleteFramebuffers(1, &fbo);
        glDeleteTextures(1, &texA);
        glDeleteTextures(1, &texB);
        return;
    }
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    texA_ = texA;
    texB_ = texB;
    fbo_ = fbo;
}

void DoubleMrt::Side::discardContents() const {
    if (fbo_ != 0) Framebuffer::discardColorAttachments(GL_FRAMEBUFFER, 2);
}

void DoubleMrt::Side::release() {
    if (texA_ != 0) glDeleteTextures(1, &texA_);
    if (texB_ != 0) glDeleteTextures(1, &texB_);
    if (fbo_ != 0) glDeleteFramebuffers(1, &fbo_);
    texA_ = 0;
    texB_ = 0;
    fbo_ = 0;
}

}  // namespace geode::viz::fluid
