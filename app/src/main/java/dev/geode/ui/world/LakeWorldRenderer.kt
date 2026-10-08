package dev.geode.ui.world

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.annotation.DrawableRes
import dev.geode.R
import dev.geode.ui.GeodeDestination
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp

/** All animation state and GL resources belong to the GL thread. */
internal class LakeWorldRenderer(
    private val context: Context,
    private val reportError: (String?) -> Unit,
) : GLSurfaceView.Renderer {
    private val scene = AtomicReference(WorldSceneSnapshot())
    private val audioMailbox = WorldAudioMailbox()
    private val audioResetRequested = AtomicBoolean(false)
    private var program: WorldShaderProgram? = null
    private val texture = IntArray(1)
    private val vertexArray = IntArray(1)
    private val lensUniforms = FloatArray(20)
    private var sceneryResource = 0
    private var width = 1
    private var height = 1
    private var failed = false
    private var clock = 0f
    private var lastFrameNanos = 0L
    private var route = 0f
    private var orbit = 0f
    private var rms = 0f
    private var bass = 0f
    private var treble = 0f
    private var impulse = 0f
    private var impulseAge = 100f

    fun update(snapshot: WorldSceneSnapshot): Boolean {
        val previous = scene.getAndSet(snapshot)
        audioMailbox.setEnabled(!snapshot.reducedMotion)
        audioMailbox.offer(snapshot.audio)
        return previous != snapshot
    }

    fun updateAudio(audio: WorldAudioSnapshot) {
        val snapshot = scene.updateAndGet { it.copy(audio = audio) }
        audioMailbox.setEnabled(!snapshot.reducedMotion)
        audioMailbox.offer(audio)
    }

    fun resetAudio() {
        audioMailbox.clear()
        scene.updateAndGet { it.copy(audio = WorldAudioSnapshot()) }
        audioResetRequested.set(true)
    }

    override fun onSurfaceCreated(
        unused: GL10?,
        config: EGLConfig?,
    ) {
        // IDs from a lost context must never be deleted against the replacement context.
        program = null
        texture[0] = 0
        vertexArray[0] = 0
        sceneryResource = 0
        failed = false
        lastFrameNanos = 0L
        GLES30.glClearColor(0.61f, 0.76f, 0.78f, 1f)
        try {
            val vertex =
                context.assets
                    .open("ui2/world_vert.glsl")
                    .bufferedReader()
                    .use { it.readText() }
            val fragment =
                context.assets
                    .open("ui2/world_frag.glsl")
                    .bufferedReader()
                    .use { it.readText() }
            program = WorldShaderProgram(vertex, fragment)
            loadScenery(desiredScenery())
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Could not initialize UI world GPU resources." }
            reportError(null)
        } catch (failure: IOException) {
            fail(failure.message ?: "UI world asset could not be loaded.")
        } catch (failure: IllegalStateException) {
            fail(failure.message ?: "UI world GPU initialization failed.")
        }
    }

    override fun onSurfaceChanged(
        unused: GL10?,
        width: Int,
        height: Int,
    ) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
        if (!failed && program != null && desiredScenery() != sceneryResource) {
            try {
                loadScenery(desiredScenery())
            } catch (failure: IllegalStateException) {
                fail(failure.message ?: "UI world scenery could not be updated.")
            }
        }
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val shader = program ?: return
        if (failed) return
        val snapshot = scene.get()
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 1f / 60f else ((now - lastFrameNanos) * 1e-9f).coerceIn(0f, 1f / 15f)
        lastFrameNanos = now
        updateMotion(snapshot, dt, now)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(shader.id)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glUniform1i(shader.uniform("uScenery"), 0)
        GLES30.glUniform1f(
            shader.uniform("uSceneryHorizon"),
            if (sceneryResource == R.drawable.ui2_lake_dawn_landscape) LANDSCAPE_HORIZON else PORTRAIT_HORIZON,
        )
        GLES30.glUniform1i(shader.uniform("uLandscapeScenery"), if (sceneryResource == R.drawable.ui2_lake_dawn_landscape) 1 else 0)
        GLES30.glUniform2f(shader.uniform("uResolution"), width.toFloat(), height.toFloat())
        GLES30.glUniform1f(shader.uniform("uTime"), clock)
        GLES30.glUniform4f(shader.uniform("uAudio"), rms, bass, treble, impulse)
        GLES30.glUniform1f(shader.uniform("uImpulseAge"), impulseAge)
        GLES30.glUniform1f(shader.uniform("uRoute"), route)
        GLES30.glUniform1f(shader.uniform("uOrbit"), orbit)
        GLES30.glUniform1f(shader.uniform("uMotion"), if (snapshot.reducedMotion) 0f else 1f)
        uploadLenses(shader, snapshot.lenses)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    fun release() {
        resetAudio()
        program?.release()
        program = null
        if (texture[0] != 0) GLES30.glDeleteTextures(1, texture, 0)
        if (vertexArray[0] != 0) GLES30.glDeleteVertexArrays(1, vertexArray, 0)
        texture[0] = 0
        vertexArray[0] = 0
        sceneryResource = 0
    }

    private fun updateMotion(
        snapshot: WorldSceneSnapshot,
        dt: Float,
        now: Long,
    ) {
        val audioWasReset = audioResetRequested.getAndSet(false)
        if (audioWasReset) {
            rms = 0f
            bass = 0f
            treble = 0f
            impulse = 0f
            impulseAge = 100f
        }
        val destinationPose = snapshot.destination.worldPose()
        val backPose = snapshot.backDestination?.worldPose() ?: destinationPose
        val routeTarget = destinationPose + (backPose - destinationPose) * snapshot.backProgress
        val orbitTarget = if (snapshot.orbitExpanded) 1f else 0f
        if (snapshot.reducedMotion) {
            route = routeTarget
            orbit = orbitTarget
            rms = 0f
            bass = 0f
            treble = 0f
            impulse = 0f
            impulseAge = 100f
            return
        }
        clock += dt
        // Periodic phases use a bounded clock; no uptime-sized float enters GLSL.
        if (clock > CLOCK_PERIOD_SECONDS) clock -= CLOCK_PERIOD_SECONDS
        val settle = (1f - exp((-dt * 6f).toDouble())).toFloat()
        route = if (snapshot.backProgress > 0f) routeTarget else route + (routeTarget - route) * settle
        orbit += (orbitTarget - orbit) * settle
        val audio = snapshot.audio
        // A reset may arrive after onDrawFrame reads its snapshot; that older audio stays muted.
        val fresh = !audioWasReset && now - audio.receivedNanos < WorldAudioMailbox.STALE_NANOS
        val envelope = (1f - exp((-dt * 10f).toDouble())).toFloat()
        rms += ((if (fresh) audio.rms else 0f) - rms) * envelope
        bass += ((if (fresh) audio.bass else 0f) - bass) * envelope
        treble += ((if (fresh) audio.treble else 0f) - treble) * envelope
        impulse *= exp((-dt * 5f).toDouble()).toFloat()
        impulseAge += dt
        val pendingAccent = audioMailbox.consume(now)
        if (pendingAccent > 0f) {
            impulse = maxOf(impulse, pendingAccent)
            impulseAge = 0f
        }
    }

    private fun uploadLenses(
        shader: WorldShaderProgram,
        lenses: List<WorldLensAnchor>,
    ) {
        lensUniforms.fill(0f)
        lenses.forEachIndexed { index, anchor ->
            val offset = index * 4
            lensUniforms[offset] = anchor.x
            lensUniforms[offset + 1] = anchor.y
            lensUniforms[offset + 2] = anchor.radius
            lensUniforms[offset + 3] = if (anchor.selected) 1f else 0f
        }
        GLES30.glUniform1i(shader.uniform("uLensCount"), lenses.size)
        GLES30.glUniform4fv(shader.uniform("uLenses[0]"), 5, lensUniforms, 0)
    }

    @DrawableRes
    private fun desiredScenery(): Int = if (width > height) R.drawable.ui2_lake_dawn_landscape else R.drawable.ui2_lake_dawn

    private fun loadScenery(
        @DrawableRes resource: Int,
    ) {
        val options =
            BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = 2
            }
        val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, resource, options))
        val replacement = IntArray(1)
        var committed = false
        try {
            GLES30.glGenTextures(1, replacement, 0)
            check(replacement[0] != 0) { "Could not allocate UI world scenery texture." }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, replacement[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Could not upload UI world scenery texture." }
            if (texture[0] != 0) GLES30.glDeleteTextures(1, texture, 0)
            texture[0] = replacement[0]
            sceneryResource = resource
            committed = true
        } finally {
            if (!committed && replacement[0] != 0) GLES30.glDeleteTextures(1, replacement, 0)
            bitmap.recycle()
        }
    }

    private fun fail(message: String) {
        failed = true
        release()
        reportError(message.take(600))
    }

    private fun GeodeDestination.worldPose(): Float =
        when (this) {
            GeodeDestination.PLAYER -> 0f
            GeodeDestination.LIBRARY -> 1f
            GeodeDestination.VISUALS -> 2f
            GeodeDestination.STUDIO -> 3f
            GeodeDestination.SETTINGS -> 4f
        }

    private companion object {
        // All authored phase rates are multiples of 0.005; this wrap preserves their phase.
        const val CLOCK_PERIOD_SECONDS = 1256.6371f
        const val PORTRAIT_HORIZON = 0.371f
        const val LANDSCAPE_HORIZON = 0.443f
    }
}
