package dev.geode.analysis

/** Preserves every event in a catch-up batch and expires holds even when PCM waits. */
internal class AnalysisEvents {
    private class Hold {
        private var level = 0f
        private var expiresNs = 0L

        fun step(
            value: Float,
            nowNs: Long,
        ): Float {
            if (nowNs >= expiresNs) level = 0f
            if (value > 0f) {
                level = maxOf(level, value)
                expiresNs = nowNs + HOLD_NS
            }
            return level
        }

        fun reset() {
            level = 0f
            expiresNs = 0L
        }
    }

    private val maxima = FloatArray(SLOTS)
    private val holds = Array(SLOTS) { Hold() }

    fun beginBatch() = maxima.fill(0f)

    fun reset() {
        beginBatch()
        holds.forEach(Hold::reset)
    }

    fun add(frame: AudioFeatures) {
        include(BEAT, if (frame.beat) 1f else 0f)
        include(STRENGTH, frame.beatStrength)
        include(TRANSIENT, frame.transient)
        include(KICK, frame.kick)
        include(SNARE, frame.snare)
        include(HAT, frame.hat)
        include(DOWNBEAT, if (frame.downbeat) 1f else 0f)
        include(SECTION, if (frame.sectionBoundary) 1f else 0f)
        include(DROP, if (frame.drop) 1f else 0f)
        include(ARRIVAL, if (frame.arrival) 1f else 0f)
    }

    fun apply(
        latest: AudioFeatures,
        nowNs: Long,
    ): AudioFeatures =
        latest.copy(
            beat = value(BEAT, nowNs) > 0f,
            beatStrength = value(STRENGTH, nowNs),
            transient = value(TRANSIENT, nowNs),
            kick = value(KICK, nowNs),
            snare = value(SNARE, nowNs),
            hat = value(HAT, nowNs),
            downbeat = value(DOWNBEAT, nowNs) > 0f,
            sectionBoundary = value(SECTION, nowNs) > 0f,
            drop = value(DROP, nowNs) > 0f,
            arrival = value(ARRIVAL, nowNs) > 0f,
        )

    private fun include(
        slot: Int,
        value: Float,
    ) {
        maxima[slot] = maxOf(maxima[slot], value)
    }

    private fun value(
        slot: Int,
        nowNs: Long,
    ): Float = holds[slot].step(maxima[slot], nowNs)

    private companion object {
        const val HOLD_NS = 48_000_000L
        const val BEAT = 0
        const val STRENGTH = 1
        const val TRANSIENT = 2
        const val KICK = 3
        const val SNARE = 4
        const val HAT = 5
        const val DOWNBEAT = 6
        const val SECTION = 7
        const val DROP = 8
        const val ARRIVAL = 9
        const val SLOTS = 10
    }
}
