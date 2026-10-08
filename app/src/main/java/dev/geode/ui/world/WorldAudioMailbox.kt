package dev.geode.ui.world

/**
 * Collector-side edges outlive the native analyser's 48 ms event hold until a slower world
 * frame consumes them. Storage stays constant: one bounded maximum, timestamp and sequence.
 */
internal class WorldAudioMailbox {
    private val lock = Any()
    private var enabled = true
    private var needsBaseline = true
    private var lastSerial = -1L
    private var lastReceivedNanos = 0L
    private var beatHeld = false
    private var transientHeld = false
    private var pendingStrength = 0f
    private var pendingNanos = 0L
    private var pendingSequence = 0L
    private var consumedSequence = 0L

    fun offer(snapshot: WorldAudioSnapshot) {
        synchronized(lock) {
            if (!enabled || snapshot.serial <= lastSerial || snapshot.serial <= 0L) return
            if (lastSerial >= 0L && snapshot.receivedNanos - lastReceivedNanos >= STALE_NANOS) clearLocked()
            lastSerial = snapshot.serial
            lastReceivedNanos = snapshot.receivedNanos
            if (pendingStrength > 0f && snapshot.receivedNanos - pendingNanos >= STALE_NANOS) {
                pendingStrength = 0f
                consumedSequence = pendingSequence
            }
            val transient = snapshot.transient.bounded()
            val transientHot = transient >= TRANSIENT_THRESHOLD
            if (needsBaseline) {
                // A StateFlow replay after yielding is current state, not a fresh strike.
                beatHeld = snapshot.beat
                transientHeld = transientHot
                needsBaseline = false
                return
            }
            var strength = 0f
            if (snapshot.beat && !beatHeld) {
                strength = snapshot.beatStrength.bounded().takeIf { it > 0f } ?: 1f
            }
            if (transientHot && !transientHeld) strength = maxOf(strength, transient)
            beatHeld = snapshot.beat
            transientHeld = transientHot
            if (strength > 0f) {
                if (strength >= pendingStrength) {
                    pendingStrength = strength
                    pendingNanos = snapshot.receivedNanos
                }
                pendingSequence++
            }
        }
    }

    /** No per-frame object, event list or audio-thread work is created by a drain. */
    fun consume(nowNanos: Long): Float =
        synchronized(lock) {
            if (!enabled || pendingSequence == consumedSequence) return@synchronized 0f
            consumedSequence = pendingSequence
            val fresh = nowNanos - pendingNanos < STALE_NANOS
            val strength = if (fresh) pendingStrength else 0f
            pendingStrength = 0f
            strength
        }

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (this.enabled == enabled) return
            this.enabled = enabled
            clearLocked()
        }
    }

    fun clear() {
        synchronized(lock) { clearLocked() }
    }

    private fun clearLocked() {
        needsBaseline = true
        lastSerial = -1L
        lastReceivedNanos = 0L
        beatHeld = false
        transientHeld = false
        pendingStrength = 0f
        pendingNanos = 0L
        consumedSequence = pendingSequence
    }

    private fun Float.bounded(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

    companion object {
        const val STALE_NANOS = 400_000_000L
        private const val TRANSIENT_THRESHOLD = 0.06f
    }
}
