package dev.geode.export

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import dev.geode.render.TransitionCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Passes frames through untouched and publishes the last one at end-of-input, before teardown. */
@UnstableApi
class TransitionCaptureEffect(
    private val store: TransitionFrameStore,
) : GlEffect {
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = TransitionCaptureProgram(store, useHdr)
}

/**
 * Opens a clip with a GL Transition from the frame in [store] to the clip's own picture, over
 * [durationUs] from the clip's first frame. Without a captured frame it is a plain pass-through.
 */
@UnstableApi
class GlTransitionEffect(
    private val def: TransitionCatalog.Def,
    private val durationUs: Long,
    private val store: TransitionFrameStore,
) : GlEffect {
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = GlTransitionProgram(def, durationUs, store, useHdr)
}

@UnstableApi
private class TransitionCaptureProgram(
    private val store: TransitionFrameStore,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, PASSTHROUGH_FRAGMENT).also { it.bindQuad() }
    private var width = 0
    private var height = 0
    private var latest = 0
    private var hasFrame = false
    private var inputEnded = false

    override fun configure(
        inputWidth: Int,
        inputHeight: Int,
    ): Size {
        TransitionFrameStore.rgbaByteCount(inputWidth, inputHeight)
        if (latest == 0 || width != inputWidth || height != inputHeight) {
            hasFrame = false
            if (latest != 0) GlUtil.deleteTexture(latest)
            latest = 0
            latest = GlUtil.createTexture(inputWidth, inputHeight, false)
            width = inputWidth
            height = inputHeight
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(
        inputTexId: Int,
        presentationTimeUs: Long,
    ) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, latest)
            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GlUtil.checkGlError()
            hasFrame = true
        } catch (failure: Throwable) {
            store.close()
            throw failure
        }
    }

    override fun signalEndOfCurrentInputStream() {
        try {
            if (hasFrame) store.publish { readBack(latest, width, height) } else store.close()
            inputEnded = true
            super.signalEndOfCurrentInputStream()
        } catch (failure: Throwable) {
            store.close()
            throw failure
        }
    }

    override fun release() {
        // Normal EOS transferred ownership to the consumer. Cancellation must never publish a frame.
        if (!inputEnded) store.close()
        try {
            super.release()
        } finally {
            try {
                if (latest != 0) GlUtil.deleteTexture(latest)
            } finally {
                latest = 0
                program.delete()
            }
        }
    }

    private fun readBack(
        texture: Int,
        w: Int,
        h: Int,
    ): TransitionFrameStore.CapturedFrame {
        val byteCount = TransitionFrameStore.rgbaByteCount(w, h)
        val previous = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previous, 0)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        try {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
                "Incomplete transition readback framebuffer"
            }
            val rgba = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba)
            GlUtil.checkGlError()
            rgba.rewind()
            return TransitionFrameStore.CapturedFrame(w, h, rgba)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previous[0])
            GLES20.glDeleteFramebuffers(1, fbo, 0)
        }
    }
}

@UnstableApi
private class GlTransitionProgram(
    private val def: TransitionCatalog.Def,
    private val durationUs: Long,
    private val store: TransitionFrameStore,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {
    private val blend = GlProgram(VERTEX_SHADER, fragmentFor(def)).also { it.bindQuad() }
    private val passthrough = GlProgram(VERTEX_SHADER, PASSTHROUGH_FRAGMENT).also { it.bindQuad() }
    private var from = 0
    private var ratio = 1f
    private var originUs = -1L

    override fun configure(
        inputWidth: Int,
        inputHeight: Int,
    ): Size {
        ratio = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(
        inputTexId: Int,
        presentationTimeUs: Long,
    ) {
        try {
            if (originUs < 0) originUs = presentationTimeUs
            val progress = if (durationUs <= 0) 1f else ((presentationTimeUs - originUs).toFloat() / durationUs).coerceIn(0f, 1f)
            if (progress >= 1f) {
                store.close()
                if (from != 0) {
                    val texture = from
                    from = 0
                    GlUtil.deleteTexture(texture)
                }
            } else if (from == 0) {
                // The preceding clip may finish after configure; consume immediately before drawing.
                store.take()?.let { from = upload(it) }
            }
            if (from == 0 || progress >= 1f) {
                passthrough.use()
                passthrough.setSamplerTexIdUniform("uTex", inputTexId, 0)
                passthrough.bindAttributesAndUniforms()
            } else {
                blend.use()
                blend.setSamplerTexIdUniform("uFrom", from, 0)
                blend.setSamplerTexIdUniform("uTo", inputTexId, 1)
                blend.setFloatUniform("progress", progress)
                blend.setFloatUniform("ratio", ratio)
                for (param in def.params) setParam(param)
                blend.bindAttributesAndUniforms()
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
            GlUtil.checkGlError()
        } catch (failure: Throwable) {
            store.close()
            throw failure
        }
    }

    override fun release() {
        store.close()
        try {
            super.release()
        } finally {
            try {
                if (from != 0) GlUtil.deleteTexture(from)
            } finally {
                from = 0
                try {
                    blend.delete()
                } finally {
                    passthrough.delete()
                }
            }
        }
    }

    // A parameter the compiler optimised out is not an active uniform, and GlProgram refuses names it does not know.
    private fun setParam(param: TransitionCatalog.Param) {
        val v = param.values
        runCatching {
            when (param.type) {
                "float" -> blend.setFloatUniform(param.name, v.getOrElse(0) { 0f })
                "int", "bool" -> blend.setIntUniform(param.name, v.getOrElse(0) { 0f }.toInt())
                "vec2", "vec3", "vec4" -> blend.setFloatsUniform(param.name, v)
                else -> Unit
            }
        }
    }

    private fun upload(frame: TransitionFrameStore.CapturedFrame): Int {
        val texture = GlUtil.createTexture(frame.width, frame.height, false)
        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            val pixels = frame.rgba.duplicate()
            pixels.clear()
            pixels.limit(TransitionFrameStore.rgbaByteCount(frame.width, frame.height))
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                frame.width,
                frame.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels,
            )
            GlUtil.checkGlError()
            return texture
        } catch (failure: Throwable) {
            runCatching { GlUtil.deleteTexture(texture) }.onFailure(failure::addSuppressed)
            throw failure
        } finally {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }
}

@UnstableApi
private fun GlProgram.bindQuad() {
    setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
}

private fun fragmentFor(def: TransitionCatalog.Def): String =
    """
    #version 300 es
    precision highp float;
    in vec2 vUv;
    out vec4 fragColor;
    uniform sampler2D uFrom;
    uniform sampler2D uTo;
    uniform float progress;
    uniform float ratio;
    vec4 getFromColor(vec2 uv) { return texture(uFrom, clamp(uv, 0.0, 1.0)); }
    vec4 getToColor(vec2 uv) { return texture(uTo, clamp(uv, 0.0, 1.0)); }
    """.trimIndent() + "\n" + def.glsl + "\nvoid main() { fragColor = transition(vUv); }\n"

private const val VERTEX_SHADER = """#version 300 es
in vec4 aFramePosition;
out vec2 vUv;
void main() {
    gl_Position = aFramePosition;
    vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

private const val PASSTHROUGH_FRAGMENT = """#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
void main() { fragColor = texture(uTex, vUv); }
"""

private const val QUAD_VERTICES = 4
