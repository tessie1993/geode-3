package dev.geode.engine.audio

class MidSideWindow(
    private val ring: SampleRing,
    windowFrames: Int,
) {
    init {
        require(windowFrames > 0) { "windowFrames must be positive, was $windowFrames" }
        require(ring.channelCount >= 2) { "a mid/side view needs two ring channels" }
    }

    private val planar = Array(ring.channelCount) { FloatArray(windowFrames) }

    val mid: FloatArray = FloatArray(windowFrames)

    val side: FloatArray = FloatArray(windowFrames)

    var position: SampleRing.Position? = null
        private set

    fun refresh(): Boolean {
        val next = ring.snapshotPosition(planar) ?: return false
        if (next == position) return false
        val left = planar[0]
        val right = planar[1]
        val sources = next.sourceChannels
        if (sources >= 2) {
            for (i in mid.indices) {
                mid[i] = (left[i] + right[i]) / 2f
                side[i] = (left[i] - right[i]) * 0.5f
            }
        } else {
            left.copyInto(mid)
            side.fill(0f)
        }
        position = next
        return true
    }
}
