package dev.geode.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import dev.geode.util.bestEffort
import kotlin.math.abs

class MicCapture(
    private val context: Context,
    sink: dev.geode.engine.audio.PcmSink,
    nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : AudioCapturePump(sink, DEFAULT_RATE, nowMs) {
    enum class Failure {
        PERMISSION,

        UNAVAILABLE,
    }

    @Volatile
    var peakLevel: Float = 0f
        private set

    @Volatile
    var silenceLikely: Boolean = false
        private set

    override val threadName = "geode-mic"

    override val threadPriority = Process.THREAD_PRIORITY_URGENT_AUDIO

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(onSampleRate: (Int) -> Unit = {}): Failure? {
        if (active) return null
        if (!hasPermission()) return Failure.PERMISSION
        val opened = MicSourcePlan.openFirst(Build.VERSION.SDK_INT, this::openSource) ?: return Failure.UNAVAILABLE
        Log.i(TAG, "microphone through ${opened.backend}")
        startPump(opened.source, onSampleRate)
        return null
    }

    override fun noteLevel(
        buffer: FloatArray,
        count: Int,
        startedAtMs: Long,
    ) {
        var peak = 0f
        for (i in 0 until count) {
            val v = abs(buffer[i])
            if (v > peak) peak = v
        }
        peakLevel = peak
        val now = nowMs()
        if (peak > SILENCE_EPSILON) {
            lastAudibleAtMs = now
            silenceLikely = false
        } else {
            val quietSince = if (lastAudibleAtMs == 0L) startedAtMs else lastAudibleAtMs
            if (now - quietSince > SILENCE_GRACE_MS) silenceLikely = true
        }
    }

    override fun resetLevel() {
        peakLevel = 0f
        silenceLikely = false
    }

    private fun openSource(backend: MicBackend): CaptureSource? =
        when (backend) {
            MicBackend.AAUDIO -> AAudioMicSource.open(unprocessedSupported())
            MicBackend.AUDIO_RECORD -> openRecord()
        }

    private fun openRecord(): AudioRecordSource? {
        val audioSource = preferredSource()
        for (rate in MicSourcePlan.recordRates(nativeRateHz())) {
            for (encoding in intArrayOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)) {
                val rec = createRecord(audioSource, rate, encoding)
                if (rec != null) return AudioRecordSource.started(rec, rate, 1, MicSourcePlan.RECORD_READ_FRAMES)
            }
        }
        return null
    }

    private fun createRecord(
        audioSource: Int,
        rate: Int,
        encoding: Int,
    ): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
        if (min <= 0) return null
        val bytesPerFrame = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val bufferBytes = MicSourcePlan.recordBufferBytes(min, MicSourcePlan.RECORD_READ_FRAMES, bytesPerFrame)
        val rec =
            runCatching {
                @Suppress("MissingPermission")
                AudioRecord(audioSource, rate, AudioFormat.CHANNEL_IN_MONO, encoding, bufferBytes)
            }.getOrNull()
        if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) return rec
        bestEffort(TAG, "rec?.release()") { rec?.release() }
        return null
    }

    private fun preferredSource(): Int =
        if (unprocessedSupported()) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

    private fun unprocessedSupported(): Boolean {
        val supported = audioProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return supported == "true"
    }

    private fun nativeRateHz(): Int? = audioProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()

    private fun audioProperty(key: String): String? =
        runCatching {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.getProperty(key)
        }.getOrNull()

    private companion object {
        const val DEFAULT_RATE = 48_000
    }
}

private const val TAG = "MicCapture"
