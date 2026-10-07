package dev.geode.audio

enum class MicBackend { AAUDIO, AUDIO_RECORD }

data class OpenedMicSource<T : Any>(
    val backend: MicBackend,
    val source: T,
)

/**
 * Which capture backends the microphone tries, in order, and the sizes of the AudioRecord fallback.
 * Pure arithmetic and ordering, so it is unit-tested without a device.
 */
object MicSourcePlan {
    /** AAudio input is used from Android 9; Android 8.0 and 8.1 go straight to AudioRecord. */
    const val AAUDIO_MIN_API = 28

    /** Frames per AudioRecord read for the microphone, about 5 ms at 48 kHz. */
    const val RECORD_READ_FRAMES = 256

    private const val RECORD_BUFFER_MULTIPLIER = 2

    private const val MIN_AAUDIO_READ_FRAMES = 64

    private const val MAX_AAUDIO_READ_FRAMES = 1024

    private const val FALLBACK_NATIVE_RATE_HZ = 48_000

    fun order(apiLevel: Int): List<MicBackend> =
        if (apiLevel >= AAUDIO_MIN_API) {
            listOf(MicBackend.AAUDIO, MicBackend.AUDIO_RECORD)
        } else {
            listOf(MicBackend.AUDIO_RECORD)
        }

    /**
     * Tries each backend [order] gives for [apiLevel] until [open] returns a source. A backend whose
     * [open] returns null or throws counts as failed; the result says which one succeeded, or is null
     * when none did.
     */
    fun <T : Any> openFirst(
        apiLevel: Int,
        open: (MicBackend) -> T?,
    ): OpenedMicSource<T>? {
        for (backend in order(apiLevel)) {
            val source = runCatching { open(backend) }.getOrNull()
            if (source != null) return OpenedMicSource(backend, source)
        }
        return null
    }

    /**
     * The sample rates to try for AudioRecord: the device's native output rate first, since the input
     * HAL usually shares it and a matching rate skips the platform resampler, then 44.1 kHz, then the
     * other usual rates. A missing or implausible native rate counts as 48 kHz.
     */
    fun recordRates(nativeRateHz: Int?): List<Int> {
        val first = nativeRateHz?.takeIf { it in 8_000..48_000 } ?: FALLBACK_NATIVE_RATE_HZ
        return listOf(first, 44_100, 48_000, 22_050).distinct()
    }

    /** Two minimum buffers, but never less than two read chunks. */
    fun recordBufferBytes(
        minBufferBytes: Int,
        readFrames: Int,
        bytesPerFrame: Int,
    ): Int = (minBufferBytes * RECORD_BUFFER_MULTIPLIER).coerceAtLeast(readFrames * bytesPerFrame * 2)

    /** One AAudio burst per read, bounded so a strange burst size cannot starve or flood the sink. */
    fun aaudioReadFrames(framesPerBurst: Int): Int {
        if (framesPerBurst <= 0) return RECORD_READ_FRAMES
        return framesPerBurst.coerceIn(MIN_AAUDIO_READ_FRAMES, MAX_AAUDIO_READ_FRAMES)
    }
}
