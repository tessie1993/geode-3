package dev.geode.analysis

import dev.geode.engine.audio.MidSideWindow
import dev.geode.engine.audio.SampleRing

/** Sample-locked windows with bounded catch-up, separate from wall-clock source expiry. */
internal class AnalysisInput(
    private val ring: SampleRing,
    private val windowFrames: Int,
) {
    enum class State { FRESH, WAITING, SILENT }

    val window = MidSideWindow(ring, windowFrames)
    private var observedEpoch = ring.epoch
    private var lastFreshNs: Long? = null
    private var rateHz = 0
    private var anchorEndFrame = windowFrames.toLong()
    private var nextHop = 0L
    private var previousEndFrame = -1L

    var dtSeconds = HOP_MILLIS / MILLIS_PER_SECOND.toFloat()
        private set

    var discontinuity = false
        private set

    /** An explicit reset in the same epoch must not re-analyze already queued PCM. */
    fun reset(
        position: SampleRing.Position = ring.position(),
        sampleRateHz: Int = rateHz,
    ) {
        observedEpoch = position.epoch
        rateHz = sampleRateHz
        window.invalidate()
        restart(position.writtenFrames + windowFrames)
        discontinuity = false
    }

    fun poll(
        nowNs: Long,
        sampleRateHz: Int,
    ): State {
        val rate = sampleRateHz.coerceAtLeast(1)
        val position = ring.position()
        discontinuity = position.epoch != observedEpoch
        if (discontinuity) {
            observedEpoch = position.epoch
            restart(windowFrames.toLong())
        }
        if (rateHz != rate) {
            if (rateHz != 0 && !discontinuity) {
                restart(position.writtenFrames + windowFrames)
                discontinuity = true
            }
            rateHz = rate
        }
        var endFrame = endpoint(nextHop)
        if (endFrame <= position.writtenFrames) {
            val delta = position.writtenFrames - anchorEndFrame
            // Invert floor(hop * Fs * 16 / 1000), including fractional endpoints.
            val lastHop = ((delta + 1) * MILLIS_PER_SECOND - 1) / (rateHz.toLong() * HOP_MILLIS)
            if (lastHop - nextHop + 1 > MAX_HOPS_PER_WAKE || endFrame - windowFrames < position.oldestAvailable) {
                val stepNumerator = rateHz.toLong() * HOP_MILLIS
                val retainedDelta = maxOf(0L, position.oldestAvailable + windowFrames - anchorEndFrame)
                val firstRetainedHop = (retainedDelta * MILLIS_PER_SECOND + stepNumerator - 1) / stepNumerator
                if (firstRetainedHop > lastHop) {
                    // A ring holding only one FFT window can overwrite every
                    // rational-grid endpoint between producer chunks. Re-anchor
                    // at the latest retained complete window instead of chasing
                    // a future endpoint that will also be overwritten.
                    anchorEndFrame = position.writtenFrames
                    nextHop = 0L
                    endFrame = anchorEndFrame
                } else {
                    nextHop = maxOf(firstRetainedHop, lastHop - MAX_HOPS_PER_WAKE + 1, 0L)
                    endFrame = endpoint(nextHop)
                }
                previousEndFrame = -1L
                lastFreshNs = null
                discontinuity = true
            }
            when (window.refreshAt(endFrame, position.epoch)) {
                SampleRing.WindowRead.OK -> {
                    dtSeconds =
                        if (previousEndFrame < 0L) {
                            HOP_MILLIS / MILLIS_PER_SECOND.toFloat()
                        } else {
                            (endFrame - previousEndFrame).toFloat() / rateHz
                        }
                    previousEndFrame = endFrame
                    nextHop++
                    lastFreshNs = nowNs
                    return State.FRESH
                }

                SampleRing.WindowRead.GAP, SampleRing.WindowRead.DISCONTINUITY -> {
                    // A producer advanced between the position and copy. Recheck
                    // on the next wake; never analyze a substituted window.
                    lastFreshNs = null
                    discontinuity = true
                    return State.SILENT
                }

                SampleRing.WindowRead.WAITING -> Unit
            }
        }
        val previous = lastFreshNs ?: return State.SILENT
        // Two FFT windows allow ordinary low-rate or batched decoder delivery. A boundary
        // bypasses this grace entirely. The lower bound spans a slow display frame.
        val graceNs = maxOf(48_000_000L, 2_000_000_000L * windowFrames / sampleRateHz.coerceAtLeast(1))
        return if (nowNs - previous >= graceNs) State.SILENT else State.WAITING
    }

    private fun endpoint(hop: Long): Long = anchorEndFrame + hop * rateHz * HOP_MILLIS / MILLIS_PER_SECOND

    private fun restart(firstEndFrame: Long) {
        anchorEndFrame = firstEndFrame
        nextHop = 0L
        previousEndFrame = -1L
        lastFreshNs = null
    }

    companion object {
        const val MAX_HOPS_PER_WAKE = 4
        const val HOP_MILLIS = 16L
        private const val MILLIS_PER_SECOND = 1000L
    }
}
