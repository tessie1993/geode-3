package dev.geode.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import dev.geode.util.bestEffort

/** An initialised [AudioRecord] that is recording, read with blocking calls. */
class AudioRecordSource private constructor(
    private val record: AudioRecord,
    override val sampleRateHz: Int,
    override val channels: Int,
    override val readFrames: Int,
) : CaptureSource {
    private val asFloat = record.audioFormat == AudioFormat.ENCODING_PCM_FLOAT

    private val shorts = ShortArray(if (asFloat) 0 else readFrames * channels)

    override fun read(dst: FloatArray): Int {
        val samples = if (asFloat) record.read(dst, 0, dst.size, AudioRecord.READ_BLOCKING) else readShorts(dst)
        return if (samples > 0) samples / channels else samples
    }

    private fun readShorts(dst: FloatArray): Int {
        val count = record.read(shorts, 0, minOf(shorts.size, dst.size))
        for (i in 0 until count) dst[i] = shorts[i] / SHORT_FULL_SCALE
        return count
    }

    override fun interrupt() {
        runCatching { record.stop() }
    }

    override fun release() {
        bestEffort(TAG, "record.stop()") { record.stop() }
        bestEffort(TAG, "record.release()") { record.release() }
    }

    companion object {
        /** Starts [record] and wraps it; a refused start releases it and returns null. */
        fun started(
            record: AudioRecord,
            sampleRateHz: Int,
            channels: Int,
            readFrames: Int,
        ): AudioRecordSource? {
            val recording =
                runCatching { record.startRecording() }.isSuccess &&
                    record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (recording) return AudioRecordSource(record, sampleRateHz, channels, readFrames)
            Log.w(TAG, "startRecording refused")
            bestEffort(TAG, "record.stop()") { record.stop() }
            bestEffort(TAG, "record.release()") { record.release() }
            return null
        }

        private const val SHORT_FULL_SCALE = 32768f
    }
}

private const val TAG = "AudioRecordSource"
