package dev.geode.audio

import android.media.AudioRecord
import android.os.Process
import androidx.annotation.AnyThread
import dev.geode.util.bestEffort
import kotlin.concurrent.thread

abstract class AudioCapturePump(
    private val sink: dev.geode.engine.audio.PcmSink,
    defaultRateHz: Int,
    protected val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private var activeSource: CaptureSource? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var runGeneration = 0

    val active: Boolean get() = running

    @Volatile
    var sampleRateHz: Int = defaultRateHz
        protected set

    @Volatile
    protected var lastAudibleAtMs: Long = 0L

    protected abstract val threadName: String

    protected open val threadPriority: Int = Process.THREAD_PRIORITY_DEFAULT

    protected abstract fun noteLevel(
        buffer: FloatArray,
        count: Int,
        startedAtMs: Long,
    )

    protected abstract fun resetLevel()

    @AnyThread
    @Synchronized
    protected fun startPump(
        rec: AudioRecord,
        channels: Int,
        onSampleRate: (Int) -> Unit,
    ): Boolean {
        val source = AudioRecordSource.started(rec, sampleRateHz, channels, READ_FRAMES) ?: return false
        startPump(source, onSampleRate)
        return true
    }

    @AnyThread
    @Synchronized
    protected fun startPump(
        source: CaptureSource,
        onSampleRate: (Int) -> Unit,
    ) {
        activeSource = source
        val generation = ++runGeneration
        running = true
        lastAudibleAtMs = 0L
        resetLevel()
        sampleRateHz = source.sampleRateHz
        onSampleRate(source.sampleRateHz)
        worker =
            thread(name = threadName, isDaemon = true) {
                applyThreadPriority()
                val pumpRun = PumpRun(source, generation, onSampleRate)
                var live = true
                while (live && running && runGeneration == generation) live = pumpRun.step()
                // This worker is the sole owner of the source; release it regardless of whether the
                // generation moved on, since nobody else will.
                source.release()
                if (runGeneration == generation) running = false
            }
    }

    @AnyThread
    @Synchronized
    fun stop() {
        running = false
        // Invalidate the generation the worker captured at start. If join() below times out
        // with the worker still inside a blocking read, this lets it notice on return
        // and exit without writing into the sink or touching a source a later run now owns.
        runGeneration++
        activeSource?.let { runCatching { it.interrupt() } }
        worker?.let { runCatching { it.join(500) } }
        worker = null
        activeSource = null
        resetLevel()
    }

    private fun applyThreadPriority() {
        val priority = threadPriority
        if (priority == Process.THREAD_PRIORITY_DEFAULT) return
        bestEffort(TAG, "Process.setThreadPriority") { Process.setThreadPriority(priority) }
    }

    private inner class PumpRun(
        private val source: CaptureSource,
        private val generation: Int,
        private val onSampleRate: (Int) -> Unit,
    ) {
        private val channels = source.channels
        private val buffer = FloatArray(source.readFrames * channels)
        private val startedAt = nowMs()
        private var reportedRate = source.sampleRateHz

        /** One read and what follows from it; false once this run is over. */
        fun step(): Boolean {
            val frames = source.read(buffer)
            // stop() bumps runGeneration before it returns, so a read that was already blocked
            // when stop() was called but only unblocks afterwards lands here with a stale
            // generation — skip writing into a sink this run no longer owns.
            if (runGeneration != generation) return false
            if (frames < 0) {
                android.util.Log.w(this@AudioCapturePump.javaClass.simpleName, "capture read error $frames")
                return false
            }
            reportRateChange()
            if (frames > 0) {
                sink.write(buffer, frames, channels)
                noteLevel(buffer, frames * channels, startedAt)
            }
            return true
        }

        // A source that reopens on another device can come back at another rate; the analysis has to
        // be told before the first chunk at the new rate reaches the sink.
        private fun reportRateChange() {
            val rate = source.sampleRateHz
            if (rate == reportedRate) return
            reportedRate = rate
            sampleRateHz = rate
            onSampleRate(rate)
        }
    }

    protected companion object {
        const val READ_FRAMES = 1024

        const val BUFFER_MULTIPLIER = 4

        const val SILENCE_EPSILON = 1e-6f

        const val SILENCE_GRACE_MS = 4_000L
    }
}

private const val TAG = "AudioCapturePump"
