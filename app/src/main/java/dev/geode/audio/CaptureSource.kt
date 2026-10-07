package dev.geode.audio

/**
 * What [AudioCapturePump] reads: a capture that is already started and hands out interleaved float
 * frames. [readFrames] is the size of one read; the pump sizes its buffer as `readFrames * channels`.
 */
interface CaptureSource {
    val sampleRateHz: Int

    val channels: Int

    val readFrames: Int

    /** Changes at disconnect and reopen, including reconnects with an unchanged format. */
    val generation: Int get() = 0

    /**
     * Blocks for the next chunk and fills the front of [dst]. Returns the frames read, 0 when nothing
     * arrived yet, negative when the capture has failed for good. [sampleRateHz] may change between
     * reads when the source reopens on a different device.
     */
    fun read(dst: FloatArray): Int

    /** Called from another thread to cut a blocked [read] short. */
    fun interrupt()

    /** Called once, on the thread that reads, after the last [read]. */
    fun release()
}
