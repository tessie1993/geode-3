package dev.geode.ui.world

import dev.geode.analysis.AudioFeatures
import dev.geode.ui.GeodeDestination

/** Screen-relative optical volume; labels and hit targets remain in the native UI. */
data class WorldLensAnchor(
    val x: Float,
    val y: Float,
    val radius: Float,
    val selected: Boolean = false,
)

/** Provisional budgets, pending sustained measurements on Android hardware. */
enum class WorldQuality(
    val shortEdgePixels: Int,
    val framesPerSecond: Int,
) {
    BALANCED(540, 60),
    LOW(360, 30),
    THROTTLED(360, 15),
}

/** Never passes AudioFeatures' mutable arrays across the UI/GL thread boundary. */
internal data class WorldAudioSnapshot(
    val serial: Long = 0L,
    val receivedNanos: Long = 0L,
    val rms: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val onset: Float = 0f,
    val beat: Boolean = false,
    val transient: Float = 0f,
    val beatStrength: Float = 0f,
) {
    companion object {
        fun from(
            features: AudioFeatures?,
            serial: Long,
        ): WorldAudioSnapshot =
            WorldAudioSnapshot(
                serial = serial,
                receivedNanos = System.nanoTime(),
                rms = features?.rms.unitSignal(),
                bass = features?.bass.unitSignal(),
                treble = features?.treble.unitSignal(),
                onset = features?.onset.unitSignal(),
                beat = features?.beat == true,
                transient = features?.transient.unitSignal(),
                beatStrength = features?.beatStrength.unitSignal(),
            )

        private fun Float?.unitSignal(): Float = if (this != null && isFinite()) coerceIn(0f, 1f) else 0f
    }
}

internal data class WorldSceneSnapshot(
    val destination: GeodeDestination = GeodeDestination.PLAYER,
    val audio: WorldAudioSnapshot = WorldAudioSnapshot(),
    val reducedMotion: Boolean = false,
    val orbitExpanded: Boolean = false,
    val lenses: List<WorldLensAnchor> = emptyList(),
    val backProgress: Float = 0f,
    val backDestination: GeodeDestination? = null,
)
