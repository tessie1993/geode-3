package dev.geode.engine.audio

fun interface PcmSink {
    /** Invalidates PCM from before a capture boundary; implementations must be thread safe. */
    fun discontinuity() = Unit

    fun write(
        interleaved: FloatArray,
        frameCount: Int,
        sourceChannelCount: Int,
    )
}
