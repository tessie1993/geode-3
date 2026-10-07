package dev.geode.render

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.analysis.AudioFeatures
import dev.geode.render.bridge.NativeViz
import dev.geode.render.scene.SceneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin

/** Exercises the shipped asset loader, native camera uniforms and real GLES shaders. */
@RunWith(AndroidJUnit4::class)
class SpatialSceneRenderTest {
    @Test
    fun spatialScenesRenderFreshPcmThenGapAndSilence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidence = File(context.cacheDir, "spatial-scene-review")
        assertTrue("Could not create scene evidence directory", evidence.isDirectory || evidence.mkdirs())
        val pcm = FloatArray(512) { (0.3 * sin(it * 0.11)).toFloat() }
        val audio =
            AudioFeatures(
                bands = FloatArray(64) { 0.2f + 0.2f * (it % 8) / 7f },
                waveform = FloatArray(128) { pcm[it * 4] },
                rms = 0.4f,
                bass = 0.6f,
                mid = 0.4f,
                treble = 0.25f,
                beat = true,
                beatStrength = 0.7f,
                transient = 0.7f,
            )
        val scenes =
            listOf(
                "kifs",
                "curl_bloom",
                "rod_tunnel",
                "nectar_flow",
                "vanishing",
                "morphogen",
                "nebula",
                "noneuclid",
            )
        withPbuffer {
            for (scene in scenes) {
                val viz = NativeViz(context)
                try {
                    assertTrue("Could not create native renderer", viz.create())
                    viz.setOffscreen(true)
                    viz.surfaceCreated()
                    viz.surfaceChanged(WIDTH, HEIGHT)
                    assertTrue("Unknown spatial scene: $scene", viz.setScene(scene))
                    viz.setParams(SceneParams(paramFadeSec = 0f, marchDetail = 0.25f))
                    viz.pushPcm(pcm, pcm.size)
                    for (frame in 0..2) {
                        // Frame 1 repeats a held analysis pulse without new PCM;
                        // frame 2 is silence. Neither may produce a stale upload.
                        viz.setFeatures(if (frame < 2) audio else AudioFeatures.empty())
                        viz.render((frame + 1) / 60.0, targetFbo = 0)
                        captureAndCheck(viz, File(evidence, "$scene-$frame.png"))
                    }
                } finally {
                    // Native GL resources must die before unbinding the EGL context.
                    viz.destroy()
                }
            }
        }
    }

    @Test
    fun fluidLooksRenderAfterResizeAndSceneRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidence = File(context.cacheDir, "spatial-scene-review")
        assertTrue("Could not create scene evidence directory", evidence.isDirectory || evidence.mkdirs())
        val failures = mutableListOf<String>()
        val scenes =
            listOf(
                "fluid",
                "fluid_ink",
                "fluid_oilslick",
                "fluid_neon",
                "fluid_chrome",
                "fluid_smoke",
                "fluid_lava",
                "fluid_marble",
                "fluid_aurora",
                "curlflow",
                "water",
            )
        withPbuffer {
            for (scene in scenes) {
                val viz = NativeViz(context)
                try {
                    assertTrue("Could not create native renderer", viz.create())
                    viz.setOffscreen(true)
                    viz.surfaceCreated()
                    viz.surfaceChanged(WIDTH, HEIGHT)
                    assertTrue("Unknown fluid scene: $scene", viz.setScene(scene))
                    viz.setParams(
                        SceneParams(
                            fluidQuality = 4,
                            fluidAutoQuality = false,
                            fluidIterations = 8,
                            // Dye alone must render; particle glints cannot mask a broken solver.
                            fluidParticlesEnabled = false,
                            fluidDyeEnabled = true,
                            paramFadeSec = 0f,
                        ),
                    )
                    viz.setFeatures(AudioFeatures.empty())
                    viz.render(0.0, targetFbo = 0)
                    val before = captureAndCheck(viz, File(evidence, "$scene-baseline.png"), requireVisible = false)
                    val audio = AudioFeatures.empty().copy(rms = 0.6f, bass = 0.8f, mid = 0.5f, treble = 0.3f)
                    for (frame in 1..30) {
                        val hit = frame % 10 == 1
                        viz.setFeatures(audio.copy(beat = hit, beatStrength = 0.8f, transient = if (hit) 0.8f else 0f))
                        if (frame == 1) viz.pushPcm(FloatArray(512) { (0.3 * sin(it * 0.11)).toFloat() }, 512)
                        if (scene == "water" && frame == 1) viz.queueTouchStroke(0.5f, 0.5f, 0.02f, 0.01f, 1f / 60f, 1f)
                        viz.render(frame / 60.0, targetFbo = 0)
                    }
                    val after = captureAndCheck(viz, File(evidence, "$scene-active.png"))
                    assertTrue("$scene showed no simulated image activity", changedPixels(before, after) > 8)
                    viz.surfaceChanged(WIDTH - 16, HEIGHT - 8)
                    viz.render(31 / 60.0, targetFbo = 0)
                    captureAndCheck(
                        viz,
                        File(evidence, "$scene-resized.png"),
                        viewportWidth = WIDTH - 16,
                        viewportHeight = HEIGHT - 8,
                    )
                    viz.surfaceChanged(WIDTH, HEIGHT)
                    viz.releaseScenes()
                    assertTrue("Could not recreate $scene", viz.setScene(scene))
                    for (frame in 32..35) viz.render(frame / 60.0, targetFbo = 0)
                    captureAndCheck(viz, File(evidence, "$scene-recreated.png"))
                } catch (failure: AssertionError) {
                    failures += "$scene: ${failure.message}"
                } finally {
                    viz.destroy()
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun fluidStylesStayVisibleAcrossRepeatedSwitchesInOneRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidence = File(context.cacheDir, "spatial-scene-review")
        assertTrue("Could not create scene evidence directory", evidence.isDirectory || evidence.mkdirs())
        withPbuffer {
            val viz = NativeViz(context)
            try {
                assertTrue("Could not create native renderer", viz.create())
                viz.setOffscreen(true)
                viz.surfaceCreated()
                viz.surfaceChanged(WIDTH, HEIGHT)
                // Production Medium buffers, including particle state, must
                // survive visiting every look in the same renderer.
                viz.setParams(SceneParams(fluidQuality = 2, fluidAutoQuality = false, paramFadeSec = 0f))
                viz.setTransition("cut", 1)
                val audio = AudioFeatures.empty().copy(rms = 0.5f, bass = 0.7f, mid = 0.4f, treble = 0.3f)
                val scenes =
                    listOf(
                        "fluid",
                        "fluid_ink",
                        "fluid_oilslick",
                        "fluid_neon",
                        "fluid_chrome",
                        "fluid_smoke",
                        "fluid_lava",
                        "fluid_marble",
                        "fluid_aurora",
                    )
                var frame = 0
                repeat(2) { cycle ->
                    for (scene in scenes) {
                        assertTrue("Could not switch to $scene", viz.setScene(scene))
                        repeat(8) { tick ->
                            viz.setFeatures(
                                audio.copy(beat = tick == 0, beatStrength = 0.8f, transient = if (tick == 0) 0.8f else 0f),
                            )
                            viz.render(++frame / 60.0, targetFbo = 0)
                        }
                        captureAndCheck(viz, File(evidence, "$scene-cycle-$cycle.png"))
                    }
                }
            } finally {
                viz.destroy()
            }
        }
    }

    private fun captureAndCheck(
        viz: NativeViz,
        file: File,
        requireVisible: Boolean = true,
        viewportWidth: Int = WIDTH,
        viewportHeight: Int = HEIGHT,
    ): IntArray {
        var shaderError: String? = null
        viz.pollError { shaderError = it }
        val glError = GLES30.glGetError()
        val framebufferStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        // Capture before assertions so a black/error render still leaves useful evidence.
        val pixels = saveFrame(file)
        assertTrue("${file.name}: $shaderError", shaderError.isNullOrEmpty())
        assertEquals("${file.name}: GLES error", GLES30.GL_NO_ERROR, glError)
        assertEquals("${file.name}: incomplete render target", GLES30.GL_FRAMEBUFFER_COMPLETE, framebufferStatus)
        if (requireVisible) {
            var litPixels = 0
            for (y in 0 until viewportHeight) {
                for (x in 0 until viewportWidth) {
                    // saveFrame flips the complete pbuffer vertically. The
                    // resized viewport still starts at GL's bottom-left.
                    if (maxChannel(pixels[(HEIGHT - 1 - y) * WIDTH + x]) >= 8) litPixels++
                }
            }
            assertTrue("${file.name} rendered black", litPixels > 10)
        }
        return pixels
    }

    private fun maxChannel(pixel: Int): Int = maxOf(pixel shr 16 and 255, pixel shr 8 and 255, pixel and 255)

    private fun changedPixels(
        before: IntArray,
        after: IntArray,
    ): Int {
        // Exclude the outer border so clamped edges and viewport padding cannot pass the test.
        var count = 0
        for (y in HEIGHT / 8 until HEIGHT * 7 / 8) {
            for (x in WIDTH / 8 until WIDTH * 7 / 8) {
                val a = before[y * WIDTH + x]
                val b = after[y * WIDTH + x]
                if ((0..16 step 8).any { kotlin.math.abs((a shr it and 255) - (b shr it and 255)) > 3 }) count++
            }
        }
        return count
    }

    private fun saveFrame(file: File): IntArray {
        val rgba = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
        GLES30.glReadPixels(0, 0, WIDTH, HEIGHT, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba)
        val readError = GLES30.glGetError()
        val pixels = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val offset = (y * WIDTH + x) * 4
                val r = rgba.get(offset).toInt() and 255
                val g = rgba.get(offset + 1).toInt() and 255
                val b = rgba.get(offset + 2).toInt() and 255
                pixels[(HEIGHT - 1 - y) * WIDTH + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue("Could not save scene evidence", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        assertEquals("Scene readback failed", GLES30.GL_NO_ERROR, readError)
        return pixels
    }

    private fun withPbuffer(block: () -> Unit) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("No EGL display", display != EGL14.EGL_NO_DISPLAY)
        var eglContext = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val version = IntArray(2)
            assertTrue("EGL initialization failed", EGL14.eglInitialize(display, version, 0, version, 1))
            val attributes =
                intArrayOf(
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_ALPHA_SIZE,
                    8,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            assertTrue(
                "No GLES 3 pbuffer config",
                EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0,
            )
            eglContext =
                EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                    0,
                )
            assertTrue("GLES 3 context creation failed", eglContext != EGL14.EGL_NO_CONTEXT)
            surface =
                EGL14.eglCreatePbufferSurface(
                    display,
                    configs[0],
                    intArrayOf(EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE),
                    0,
                )
            assertTrue("Pbuffer creation failed", surface != EGL14.EGL_NO_SURFACE)
            assertTrue("Could not bind GLES 3 context", EGL14.eglMakeCurrent(display, surface, surface, eglContext))
            block()
        } finally {
            try {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            } finally {
                try {
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                } finally {
                    try {
                        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                    } finally {
                        EGL14.eglTerminate(display)
                        EGL14.eglReleaseThread()
                    }
                }
            }
        }
    }

    private companion object {
        const val WIDTH = 128
        const val HEIGHT = 96
    }
}
