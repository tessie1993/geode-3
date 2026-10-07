package dev.geode.analysis

import dev.geode.engine.audio.MidSideWindow
import dev.geode.engine.audio.SampleRing

/** Keeps a normal gap between chunks distinct from a disconnected or stalled input. */
internal class AnalysisInput(
    ring: SampleRing,
    private val windowFrames: Int,
) {
    enum class State { FRESH, WAITING, SILENT }

    val window = MidSideWindow(ring, windowFrames)
    private var observedEpoch = ring.epoch
    private var lastFreshNs: Long? = null

    var discontinuity = false
        private set

    fun poll(
        nowNs: Long,
        sampleRateHz: Int,
    ): State {
        val fresh = window.refresh()
        discontinuity = window.epoch != observedEpoch
        observedEpoch = window.epoch
        if (discontinuity) lastFreshNs = null
        if (fresh) {
            lastFreshNs = nowNs
            return State.FRESH
        }
        val previous = lastFreshNs ?: return State.SILENT
        // Two FFT windows allow ordinary low-rate or batched decoder delivery. A boundary
        // bypasses this grace entirely. The lower bound spans a slow display frame.
        val graceNs = maxOf(48_000_000L, 2_000_000_000L * windowFrames / sampleRateHz.coerceAtLeast(1))
        return if (nowNs - previous >= graceNs) State.SILENT else State.WAITING
    }
}
