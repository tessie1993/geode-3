package dev.geode.audio

import java.util.concurrent.atomic.AtomicLong

class PcmRingBuffer(
    capacity: Int = 1 shl 16,
) : dev.geode.engine.audio.PcmSink {
    override fun write(
        interleaved: FloatArray,
        frameCount: Int,
        sourceChannelCount: Int,
    ) = writeInterleaved(interleaved, frameCount, sourceChannelCount)

    private val data: FloatArray = FloatArray(capacity)

    private val sideData: FloatArray = FloatArray(capacity)
    private val mask: Long = (capacity - 1).toLong()

    @Volatile
    private var writeIndex: Long = 0

    // The active capture source is the single producer. Readers use this
    // sequence to reject a snapshot overlapped by a producer write.
    private val writeSequence = AtomicLong(0)

    init {
        require(capacity and (capacity - 1) == 0) { "capacity must be a power of two" }
    }

    fun writeInterleaved(
        samples: FloatArray,
        frameCount: Int,
        channelCount: Int,
    ) {
        require(channelCount > 0) { "channelCount must be positive, was $channelCount" }
        require(frameCount * channelCount <= samples.size) {
            "$frameCount frames x $channelCount channels exceeds buffer of ${samples.size}"
        }
        writeSequence.incrementAndGet()
        try {
            var w = writeIndex
            var s = 0
            val stereo = channelCount >= 2
            repeat(frameCount) {
                var acc = 0f
                val base = s
                repeat(channelCount) {
                    acc += samples[s]
                    s++
                }
                val slot = (w and mask).toInt()
                data[slot] = acc / channelCount
                sideData[slot] = if (stereo) (samples[base] - samples[base + 1]) * 0.5f else 0f
                w++
            }
            writeIndex = w
        } finally {
            writeSequence.incrementAndGet()
        }
    }

    fun currentWriteIndex(): Long = writeIndex

    var lastCopyEndIndex: Long = 0L
        private set

    fun copyNewSince(
        fromIndex: Long,
        out: FloatArray,
    ): Int {
        val w = writeIndex
        lastCopyEndIndex = w
        return copyEndingAt(fromIndex, w, out)
    }

    internal data class Read(
        val count: Int,
        val endIndex: Long,
    )

    /**
     * Each consumer receives the exact end position associated with its copy.
     * It never reads or writes the legacy shared [lastCopyEndIndex] side channel.
     * Null means a writer overlapped the copy; retain the cursor and try next frame.
     */
    internal fun readNewSince(
        fromIndex: Long,
        out: FloatArray,
    ): Read? {
        val sequence = writeSequence.get()
        if (sequence and 1L != 0L) return null
        val end = writeIndex
        val count = copyEndingAt(fromIndex, end, out)
        // A successful read-and-update validates the version and orders the
        // preceding array copy before validation; a plain volatile reread is
        // insufficient for that ordering on every supported runtime.
        return if (writeSequence.compareAndSet(sequence, sequence)) Read(count, end) else null
    }

    private fun copyEndingAt(
        fromIndex: Long,
        endIndex: Long,
        out: FloatArray,
    ): Int {
        var available = endIndex - fromIndex
        if (available <= 0L) return 0
        if (available > out.size) available = out.size.toLong()
        val maxRun = data.size - (data.size shr SNAPSHOT_HEADROOM_SHIFT)
        if (available > maxRun) available = maxRun.toLong()
        val start = endIndex - available
        for (i in 0 until available.toInt()) {
            out[i] = data[((start + i) and mask).toInt()]
        }
        return available.toInt()
    }

    fun snapshotLatest(out: FloatArray): Boolean = snapshotFrom(data, out)

    fun snapshotLatestSide(out: FloatArray): Boolean = snapshotFrom(sideData, out)

    private fun snapshotFrom(
        src: FloatArray,
        out: FloatArray,
    ): Boolean {
        if (out.size > src.size) return false
        val w = writeIndex
        if (w < out.size) return false
        var r = w - out.size
        for (i in out.indices) {
            out[i] = src[(r and mask).toInt()]
            r++
        }
        return true
    }

    private companion object {
        const val SNAPSHOT_HEADROOM_SHIFT = 2
    }
}
