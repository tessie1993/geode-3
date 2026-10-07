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
    override val readFrames: Int,
    initialRateHz: Int,
) : CaptureSource {
    @Volatile
    private var rateHz: Int = initialRateHz

    override val sampleRateHz: Int get() = rateHz

    override val channels: Int = 1

    private var generation = GeodeNative.micGeneration(handle)

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
        Log.i(TAG, "stream reopened at $rateHz Hz")
    }

    // The blocking read returns within READ_TIMEOUT_NANOS on its own, and closing a stream another thread
    // is reading is not safe, so stopping waits for that read instead of cutting it.
    override fun interrupt() = Unit

    override fun release() {
        GeodeNative.micStop(handle)
        GeodeNative.micDestroy(handle)
    }

    companion object {
        /** Opens and starts a stream, or returns null so the caller can fall back to AudioRecord. */
        fun open(preferUnprocessed: Boolean): AAudioMicSource? {
            val handle = GeodeNative.micCreate(preferUnprocessed)
            if (handle == 0L) return null
            val rate = if (GeodeNative.micStart(handle)) GeodeNative.micSampleRate(handle) else 0
            if (rate <= 0) {
                Log.w(TAG, "AAudio input did not open (error ${GeodeNative.micLastError(handle)})")
                GeodeNative.micDestroy(handle)
                return null
            }
            val readFrames = MicSourcePlan.aaudioReadFrames(GeodeNative.micFramesPerBurst(handle))
            return AAudioMicSource(handle, readFrames, rate)
        }

        private const val READ_TIMEOUT_NANOS = 40_000_000L
    }
}

private const val TAG = "AAudioMicSource"
