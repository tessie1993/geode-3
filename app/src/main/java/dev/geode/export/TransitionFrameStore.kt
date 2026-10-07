package dev.geode.export

import java.nio.ByteBuffer

/** One boundary's CPU frame. A successful take transfers ownership and permanently closes the handoff. */
class TransitionFrameStore {
    private var frame: CapturedFrame? = null
    private var closed = false

    /** The supplier is skipped after cancellation/consumption, avoiding an unnecessary readback. */
    @Synchronized
    fun publish(capture: () -> CapturedFrame): Boolean {
        if (closed || frame != null) return false
        try {
            frame = capture()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
        return true
    }

    /** A missing frame leaves the handoff open: capture may finish after consumer configuration. */
    @Synchronized
    fun take(): CapturedFrame? {
        val captured = frame ?: return null
        frame = null
        closed = true
        return captured
    }

    /** Consumer completion/cancellation rejects any subsequent producer publication. */
    @Synchronized
    fun close() {
        frame = null
        closed = true
    }

    class CapturedFrame(
        val width: Int,
        val height: Int,
        val rgba: ByteBuffer,
    ) {
        init {
            require(rgba.capacity() >= rgbaByteCount(width, height)) { "Incomplete transition frame" }
        }
    }

    companion object {
        private const val BYTES_PER_PIXEL = 4

        fun rgbaByteCount(
            width: Int,
            height: Int,
        ): Int {
            require(width > 0 && height > 0) { "Transition dimensions must be positive" }
            val pixels = width.toLong() * height.toLong()
            require(pixels <= Int.MAX_VALUE / BYTES_PER_PIXEL) { "Transition frame exceeds buffer capacity" }
            return (pixels * BYTES_PER_PIXEL).toInt()
        }
    }
}
