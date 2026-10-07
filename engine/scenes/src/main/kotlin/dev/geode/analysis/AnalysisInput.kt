package dev.geode.analysis

import dev.geode.engine.audio.MidSideWindow
import dev.geode.engine.audio.SampleRing

/** Couples each admitted PCM window to the rate and lifecycle boundary that admitted it. */
internal class AnalysisInput(
    private val ring: SampleRing,
    private val windowFrames: Int,
    initialRateHz: Int,
) {
    private data class Boundary(val generation: Long, val rateHz: Int, val position: SampleRing.Position)

    private val publicationLock = Any()

    @Volatile
    private var boundary = Boundary(0L, initialRateHz, ring.currentPosition())

    val sampleRateHz: Int get() = boundary.rateHz

    fun setSampleRate(
        rateHz: Int,
        clear: () -> Unit,
    ) {
        require(rateHz > 0) { "sample rate must be positive" }
        synchronized(publicationLock) {
            if (rateHz != boundary.rateHz) advanceBoundary(rateHz, clear)
        }
    }

    fun reset(clear: () -> Unit) {
        synchronized(publicationLock) { advanceBoundary(boundary.rateHz, clear) }
    }

    // Only metadata and the empty feature publication are inside this lock. Native
    // analysis/configuration never blocks a producer updating its sample rate.
    private fun advanceBoundary(
        rateHz: Int,
        clear: () -> Unit,
    ) {
        boundary = Boundary(boundary.generation + 1L, rateHz, ring.currentPosition())
        clear()
    }

    fun openWindow(clear: () -> Unit): Window {
        reset(clear)
        return Window()
    }

    internal data class Frame(
        val mid: FloatArray,
        val side: FloatArray,
        val position: SampleRing.Position,
        val sampleRateHz: Int,
        val generation: Long,
    )

    inner class Window internal constructor() {
        private val samples = MidSideWindow(ring, windowFrames)

        fun refresh(): Frame? {
            val admittedBy = boundary
            if (!samples.refresh()) return null
            val position = checkNotNull(samples.position)
            if (
                position.epoch == admittedBy.position.epoch &&
                position.frames - admittedBy.position.frames < windowFrames
            ) {
                // A partly new FFT window still contains audio from before the
                // restart/rate change. Wait for an entirely post-boundary window.
                return null
            }
            val frame = Frame(samples.mid, samples.side, position, admittedBy.rateHz, admittedBy.generation)
            return frame.takeIf(::isCurrent)
        }
    }

    fun isCurrent(frame: Frame): Boolean = boundary.generation == frame.generation && ring.epoch == frame.position.epoch

    /** Prevents a completed old FFT from overwriting a concurrently cleared feature frame. */
    fun publishIfCurrent(
        frame: Frame,
        publish: () -> Unit,
    ): Boolean =
        synchronized(publicationLock) {
            if (!isCurrent(frame)) return@synchronized false
            publish()
            true
        }
}
