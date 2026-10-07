package dev.geode.audio

import android.util.Log
import dev.geode.engine.bridge.GeodeNative

/**
 * The microphone through a native AAudio input stream read with blocking calls.
 *
 * The stream is always mono. When the device route changes the native side closes and reopens the
 * stream inside [read], possibly at another sample rate; [sampleRateHz] follows it, and the pump
 * reports the change.
 */
class AAudioMicSource private constructor(
    private val handle: Long,
    initialReadFrames: Int,
    initialRateHz: Int,
) : CaptureSource {
    @Volatile
    private var rateHz: Int = initialRateHz

    override val sampleRateHz: Int get() = rateHz

    override val channels: Int = 1

    override var generation: Int = GeodeNative.micGeneration(handle)
        private set

    override var readFrames: Int = initialReadFrames
        private set

    override fun read(dst: FloatArray): Int {
        val frames = GeodeNative.micRead(handle, dst, dst.size, READ_TIMEOUT_NANOS)
        adoptReopenedFormat()
        return frames
    }

    private fun adoptReopenedFormat() {
        val current = GeodeNative.micGeneration(handle)
        if (current == generation) return
        generation = current
        rateHz = GeodeNative.micSampleRate(handle)
        readFrames = MicSourcePlan.aaudioReadFrames(GeodeNative.micFramesPerBurst(handle))
        Log.i(TAG, "stream generation $generation at $rateHz Hz, $readFrames frames per read")
    }

    // The blocking read returns within READ_TIMEOUT_NANOS on its own, and closing a stream another thread
    // is reading is not safe, so stopping waits for that read instead of cutting it.
    override fun interrupt() = Unit

    override fun release() {
        try {
            GeodeNative.micStop(handle)
        } finally {
            GeodeNative.micDestroy(handle)
        }
    }

    companion object {
        /** Opens and starts a stream, or returns null so the caller can fall back to AudioRecord. */
        fun open(preferUnprocessed: Boolean): AAudioMicSource? {
            val handle = GeodeNative.micCreate(preferUnprocessed)
            if (handle == 0L) return null
            var transferred = false
            try {
                val rate = if (GeodeNative.micStart(handle)) GeodeNative.micSampleRate(handle) else 0
                if (rate <= 0) {
                    Log.w(TAG, "AAudio input did not open (error ${GeodeNative.micLastError(handle)})")
                    return null
                }
                val readFrames = MicSourcePlan.aaudioReadFrames(GeodeNative.micFramesPerBurst(handle))
                val source = AAudioMicSource(handle, readFrames, rate)
                transferred = true
                return source
            } finally {
                if (!transferred) GeodeNative.micDestroy(handle)
            }
        }

        private const val READ_TIMEOUT_NANOS = 40_000_000L
    }
}

private const val TAG = "AAudioMicSource"
