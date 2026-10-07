package dev.geode.engine.audio

class MidSideWindow(
    private val ring: SampleRing,
    windowFrames: Int,
) {
    init {
        require(windowFrames > 0) { "windowFrames must be positive, was $windowFrames" }
        require(windowFrames <= ring.capacityFrames) { "windowFrames exceeds ring capacity" }
        require(ring.channelCount >= 2) { "a mid/side view needs two ring channels" }
    }

    private val planar = Array(ring.channelCount) { FloatArray(windowFrames) }

    val mid: FloatArray = FloatArray(windowFrames)

    val side: FloatArray = FloatArray(windowFrames)

    var epoch: Int = ring.epoch
        private set

    var endFrame = -1L
        private set

    /** A copied window rejected by a publication boundary may be read again. */
    fun invalidate() {
        endFrame = -1L
    }

    /** Copies each PCM position once, with format and epoch from the same snapshot. */
    fun refresh(): Boolean {
        val position = ring.position()
        return refreshAt(position.writtenFrames, position.epoch) == SampleRing.WindowRead.OK
    }

    /** Copies one scheduled window, never substituting a newer window or epoch. */
    fun refreshAt(
        endFrame: Long,
        expectedEpoch: Int,
    ): SampleRing.WindowRead {
        var sources = 0
        val result =
            synchronized(ring) {
                if (epoch != ring.epoch) {
                    epoch = ring.epoch
                    this.endFrame = -1L
                }
                if (epoch != expectedEpoch) {
                    SampleRing.WindowRead.DISCONTINUITY
                } else if (this.endFrame == endFrame) {
                    SampleRing.WindowRead.WAITING
                } else {
                    ring.snapshotWindow(endFrame - mid.size, expectedEpoch, planar).also { read ->
                        if (read == SampleRing.WindowRead.OK) {
                            this.endFrame = endFrame
                            sources = ring.sourceChannelCount
                        }
                    }
                }
            }
        if (result != SampleRing.WindowRead.OK) return result
        val left = planar[0]
        val right = planar[1]
        if (sources >= 2) {
            for (i in mid.indices) {
                mid[i] = (left[i] + right[i]) / 2f
                side[i] = (left[i] - right[i]) * 0.5f
            }
        } else {
            left.copyInto(mid)
            side.fill(0f)
        }
        return SampleRing.WindowRead.OK
    }
}
