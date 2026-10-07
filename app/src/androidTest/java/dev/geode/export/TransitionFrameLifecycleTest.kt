package dev.geode.export

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.media3.common.MediaItem
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.geode.render.TransitionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** Runs in Actions on an emulator; verifies actual readback/upload and lifecycle order, not only the store. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionFrameLifecycleTest {
    @Test
    fun captureAfterConsumerConfigurationStillBlendsAndReleasesCpuFrame() {
        withGl {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val store = TransitionFrameStore()
            val capture = TransitionCaptureEffect(store).toGlShaderProgram(context, false)
            val transition = GlTransitionEffect(crossfade, 1_000_000L, store).toGlShaderProgram(context, false)
            val red = texture(255, 0)
            val green = texture(0, 255)
            var captureReleased = false
            try {
                transition.configure(2, 2)
                capture.configure(2, 2)
                capture.drawFrame(red, 0)
                capture.signalEndOfCurrentInputStream()
                capture.release()
                captureReleased = true
                transition.drawFrame(green, 0)
                assertPixel(255, 0)
                assertNull(store.take())
                transition.drawFrame(green, 500_000)
                assertPixel(128, 128)
                transition.drawFrame(green, 1_000_000)
                assertPixel(0, 255)
            } finally {
                if (!captureReleased) capture.release()
                transition.release()
                GlUtil.deleteTexture(red)
                GlUtil.deleteTexture(green)
            }
        }
    }

    @Test
    fun releasedConsumerRejectsProducerEndOfInput() {
        withGl {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val store = TransitionFrameStore()
            val capture = TransitionCaptureEffect(store).toGlShaderProgram(context, false)
            val transition = GlTransitionEffect(crossfade, 1_000_000L, store).toGlShaderProgram(context, false)
            val red = texture(255, 0)
            try {
                transition.configure(2, 2)
                transition.release()
                capture.configure(2, 2)
                capture.drawFrame(red, 0)
                capture.signalEndOfCurrentInputStream()
                assertNull(store.take())
                assertFalse(store.publish { error("Cancelled boundary must reject readback") })
            } finally {
                capture.release()
                GlUtil.deleteTexture(red)
            }
        }
    }

    @Test
    fun compositionTeardownClosesBoundariesWithoutConsumerPrograms() {
        val pending = TransitionFrameStore()
        val notYetProduced = TransitionFrameStore()
        pending.publish { TransitionFrameStore.CapturedFrame(2, 2, ByteBuffer.allocateDirect(16)) }
        val item = EditedMediaItem.Builder(MediaItem.fromUri("file:///unused-test-video.mp4")).build()
        val sequence = EditedMediaItemSequence.Builder().addItem(item).build()
        val ready =
            ProjectComposition.Outcome.Ready(
                Composition.Builder(listOf(sequence)).build(),
                1000L,
                listOf(pending, notYetProduced),
            )
        ready.close()
        ready.close()
        assertNull(pending.take())
        assertFalse(notYetProduced.publish { error("Composition teardown must prevent allocation") })
    }

    private fun texture(
        red: Int,
        green: Int,
    ): Int {
        val texture = GlUtil.createTexture(2, 2, false)
        val pixels = ByteBuffer.allocateDirect(16)
        repeat(4) { pixels.put(red.toByte()).put(green.toByte()).put(0.toByte()).put(255.toByte()) }
        pixels.rewind()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 2, 2, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GlUtil.checkGlError()
        return texture
    }

    private fun assertPixel(
        red: Int,
        green: Int,
    ) {
        val pixels = ByteBuffer.allocateDirect(4)
        GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        GlUtil.checkGlError()
        assertEquals(red.toDouble(), (pixels.get(0).toInt() and 255).toDouble(), 2.0)
        assertEquals(green.toDouble(), (pixels.get(1).toInt() and 255).toDouble(), 2.0)
    }

    private fun withGl(block: () -> Unit) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                0x40,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_NONE,
            )
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0))
        assertTrue(count[0] > 0)
        val config = checkNotNull(configs[0])
        val context =
            EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                0,
            )
        val surface =
            EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 2, EGL14.EGL_HEIGHT, 2, EGL14.EGL_NONE),
                0,
            )
        try {
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            GLES20.glViewport(0, 0, 2, 2)
            block()
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private val crossfade =
        TransitionCatalog.Def(
            name = "test crossfade",
            author = "Geode",
            license = "test",
            params = emptyList(),
            glsl = "vec4 transition(vec2 uv) { return mix(getFromColor(vec2(uv.x / ratio, uv.y)), getToColor(uv), progress); }",
        )
}
