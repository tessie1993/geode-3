package dev.geode.ui.world

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.MotionEvent
import dev.geode.analysis.AudioFeatures
import dev.geode.render.ThermalGovernor
import dev.geode.ui.GeodeDestination
import kotlin.math.min
import kotlin.math.roundToInt

/** A decorative surface: no pointer interception and no shared visualizer pacing side effects. */
internal class LakeWorldView(
    context: Context,
    onRenderError: (String?) -> Unit,
) : GLSurfaceView(context) {
    private val worldRenderer =
        LakeWorldRenderer(context.applicationContext) { error ->
            post { if (!released) onRenderError(error) }
        }
    private var hostResumed = false
    private var hostActive = true
    private var renderingResumed = true
    private var released = false
    private var reducedMotion = false
    private var quality = WorldQuality.BALANCED
    private var requestedQuality = WorldQuality.BALANCED
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var lastBudgetCheckNanos = 0L
    private var lastFeatures: AudioFeatures? = null
    private var audioSerial = 0L
    private var audio = WorldAudioSnapshot()
    private var featureSourceEnabled = false
    private var lastFrameNanos = 0L
    private var frameCallbackPosted = false
    private val choreographer = Choreographer.getInstance()
    private val frameCallback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCallbackPosted = false
                if (!shouldAnimate()) return
                if (frameTimeNanos - lastBudgetCheckNanos >= BUDGET_CHECK_PERIOD_NANOS) {
                    lastBudgetCheckNanos = frameTimeNanos
                    refreshBudget()
                }
                val framePeriodNanos = 1_000_000_000L / quality.framesPerSecond
                if (lastFrameNanos == 0L || frameTimeNanos - lastFrameNanos >= framePeriodNanos - 500_000L) {
                    lastFrameNanos = frameTimeNanos
                    requestRender()
                }
                postFrame()
            }
        }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(worldRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // GLSurfaceView starts its thread in setRenderer. The owner explicitly resumes it later.
        onPause()
        renderingResumed = false
    }

    fun updateScene(
        destination: GeodeDestination,
        features: AudioFeatures?,
        reducedMotion: Boolean,
        orbitExpanded: Boolean,
        lenses: List<WorldLensAnchor>,
        backProgress: Float,
        backDestination: GeodeDestination?,
    ) {
        if (!featureSourceEnabled) receiveAudio(features)
        this.reducedMotion = reducedMotion
        val anchors =
            lenses.take(MAX_LENSES).mapNotNull { anchor ->
                if (!anchor.x.isFinite() || !anchor.y.isFinite() || !anchor.radius.isFinite()) {
                    null
                } else {
                    anchor.copy(
                        x = anchor.x.coerceIn(0f, 1f),
                        y = anchor.y.coerceIn(0f, 1f),
                        radius = anchor.radius.coerceIn(0.015f, 0.16f),
                    )
                }
            }
        val changed =
            worldRenderer.update(
                WorldSceneSnapshot(
                    destination,
                    audio,
                    reducedMotion,
                    orbitExpanded,
                    anchors,
                    if (backProgress.isFinite()) backProgress.coerceIn(0f, 1f) else 0f,
                    backDestination,
                ),
            )
        syncRendering()
        if (changed && renderingResumed && !shouldAnimate()) requestRender()
    }

    fun setQuality(quality: WorldQuality) {
        if (requestedQuality == quality) return
        requestedQuality = quality
        refreshBudget()
    }

    fun setFeatureSourceEnabled(enabled: Boolean) {
        if (featureSourceEnabled == enabled) return
        featureSourceEnabled = enabled
        resetAudio()
    }

    fun updateAudioFeatures(features: AudioFeatures) {
        if (released || !featureSourceEnabled) return
        if (!canCaptureAudio()) return
        receiveAudio(features)
        worldRenderer.updateAudio(audio)
    }

    fun setHostActive(active: Boolean) {
        val yielded = hostActive && !active
        hostActive = active
        if (yielded) resetAudio()
        syncRendering()
    }

    fun setHostResumed(resumed: Boolean) {
        val yielded = hostResumed && !resumed
        hostResumed = resumed
        if (yielded) resetAudio()
        syncRendering()
    }

    fun release() {
        if (released) return
        released = true
        resetAudio()
        stopFrames()
        // Context destruction also frees resources if the host was already paused.
        preserveEGLContextOnPause = false
        if (renderingResumed) queueEvent { worldRenderer.release() }
        onPause()
        renderingResumed = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Reattachment creates a fresh GLThread; establish the owner's paused baseline again.
        onPause()
        renderingResumed = false
        syncRendering()
    }

    override fun onDetachedFromWindow() {
        // Stop capture before resetting: the collector can briefly outlive surface detachment.
        renderingResumed = false
        resetAudio()
        stopFrames()
        // Android destroys the EGL surface/context after this; IDs never escape the renderer.
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncRendering()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resizeSurface(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun resizeSurface(
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) return
        val scale = min(1f, quality.shortEdgePixels.toFloat() / min(width, height))
        holder.setFixedSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }

    private fun receiveAudio(features: AudioFeatures?) {
        if (!canCaptureAudio()) return
        if (features === lastFeatures) return
        lastFeatures = features
        audio = WorldAudioSnapshot.from(features, ++audioSerial)
    }

    private fun resetAudio() {
        lastFeatures = null
        audio = WorldAudioSnapshot()
        worldRenderer.resetAudio()
    }

    private fun canCaptureAudio(): Boolean {
        val hostReady = hostActive && hostResumed && !released
        val surfaceReady = renderingResumed && isAttachedToWindow && windowVisibility == VISIBLE
        return hostReady && surfaceReady
    }

    private fun syncRendering() {
        val shouldResume = !released && hostResumed && hostActive && isAttachedToWindow && windowVisibility == VISIBLE
        if (shouldResume != renderingResumed) {
            if (shouldResume) {
                refreshBudget()
                onResume()
                renderingResumed = true
                requestRender()
            } else {
                renderingResumed = false
                resetAudio()
                stopFrames()
                onPause()
            }
        }
        if (shouldAnimate()) postFrame() else stopFrames()
    }

    private fun shouldAnimate(): Boolean = renderingResumed && !released && !reducedMotion

    private fun refreshBudget() {
        val reportedThermal = ThermalGovernor.platformStatus
        val thermal =
            if (reportedThermal >= 0) {
                reportedThermal
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                power?.currentThermalStatus ?: 0
            } else {
                0
            }
        val budget =
            when {
                thermal >= 3 || requestedQuality == WorldQuality.THROTTLED -> WorldQuality.THROTTLED
                thermal >= 2 || power?.isPowerSaveMode == true || requestedQuality == WorldQuality.LOW -> WorldQuality.LOW
                else -> WorldQuality.BALANCED
            }
        if (quality == budget) return
        quality = budget
        resizeSurface(width, height)
        lastFrameNanos = 0L
    }

    private fun postFrame() {
        if (frameCallbackPosted) return
        frameCallbackPosted = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        choreographer.removeFrameCallback(frameCallback)
        frameCallbackPosted = false
        lastFrameNanos = 0L
    }

    private companion object {
        const val MAX_LENSES = 5
        const val BUDGET_CHECK_PERIOD_NANOS = 1_000_000_000L
    }
}
