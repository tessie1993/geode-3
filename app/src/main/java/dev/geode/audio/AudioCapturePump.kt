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
    private val stateLock = Any()

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
        fallback: (() -> CaptureSource?)? = null,
    ) {
        synchronized(stateLock) {
            val generation = ++runGeneration
            activeSource = source
            running = true
            lastAudibleAtMs = 0L
            resetLevel()
            try {
                sink.discontinuity()
                sampleRateHz = source.sampleRateHz
                onSampleRate(sampleRateHz)
                worker =
                    thread(name = threadName, isDaemon = true) {
                        val pumpRun = PumpRun(source, generation, onSampleRate, fallback)
                        try {
                            applyThreadPriority()
                            while (running && runGeneration == generation && pumpRun.step()) {
                                // The blocking source paces the loop.
                            }
                        } finally {
                            pumpRun.close()
                        }
                    }
            } catch (error: Throwable) {
                activeSource = null
                running = false
                runCatching { source.release() }
                throw error
            }
        }
    }

    @AnyThread
    @Synchronized
    fun stop() {
        synchronized(stateLock) {
            running = false
            // Publication and invalidation use the same lock: an old worker cannot pass a
            // generation check and then write after stop or a replacement session's reset.
            runGeneration++
            activeSource?.let { runCatching { it.interrupt() } }
            activeSource = null
            sink.discontinuity()
            resetLevel()
        }
        // The worker needs stateLock to unwind. Never hold that lock while joining it.
        worker?.let { runCatching { it.join(500) } }
        worker = null
    }

    private fun applyThreadPriority() {
        val priority = threadPriority
        if (priority == Process.THREAD_PRIORITY_DEFAULT) return
        bestEffort(TAG, "Process.setThreadPriority") { Process.setThreadPriority(priority) }
    }

    private inner class PumpRun(
        initialSource: CaptureSource,
        private val generation: Int,
        private val onSampleRate: (Int) -> Unit,
        private var fallback: (() -> CaptureSource?)?,
    ) {
        private var source: CaptureSource? = initialSource
        private var buffer = FloatArray(initialSource.readFrames * initialSource.channels)
        private val startedAt = nowMs()
        private var sourceGeneration = initialSource.generation
        private var reportedRate = initialSource.sampleRateHz

        private fun isCurrent(): Boolean = running && runGeneration == generation

        /** Reads/opening/closing belong to this worker; only publication uses stateLock. */
        fun step(): Boolean {
            val current = source ?: return false
            val samples = current.readFrames * current.channels
            if (buffer.size != samples) buffer = FloatArray(samples)
            val frames = runCatching { current.read(buffer) }.getOrDefault(-1)
            synchronized(stateLock) {
                if (!isCurrent()) return false
                if (current.generation != sourceGeneration || current.sampleRateHz != reportedRate || frames < 0) {
                    sink.discontinuity()
                    resetLevel()
                    sourceGeneration = current.generation
                }
                if (frames >= 0) {
                    reportRate(current)
                    if (frames > 0) {
                        require(frames <= buffer.size / current.channels)
                        sink.write(buffer, frames, current.channels)
                        noteLevel(buffer, frames * current.channels, startedAt)
                    }
                    return true
                }
            }
            android.util.Log.w(TAG, "capture read failed ($frames); fallback available=${fallback != null}")
            return replaceSource()
        }

        private fun replaceSource(): Boolean {
            val open = fallback
            fallback = null
            detachAndRelease()
            if (open == null || !isCurrent()) return false
            // This may block. stop() can invalidate the run while no active source is installed.
            val replacement = runCatching { open() }.getOrNull() ?: return false
            source = replacement
            synchronized(stateLock) {
                if (!isCurrent()) return false // close() releases the late replacement.
                activeSource = replacement
                sourceGeneration = replacement.generation
                sink.discontinuity()
                resetLevel()
                reportRate(replacement)
            }
            return true
        }

        private fun reportRate(current: CaptureSource) {
            val rate = current.sampleRateHz
            if (rate == reportedRate) return
            reportedRate = rate
            sampleRateHz = rate
            onSampleRate(rate)
        }

        private fun detachAndRelease() {
            val owned = source ?: return
            synchronized(stateLock) {
                if (runGeneration == generation && activeSource === owned) activeSource = null
                source = null
            }
            runCatching { owned.release() }
        }

        fun close() {
            try {
                synchronized(stateLock) {
                    if (runGeneration == generation) {
                        sink.discontinuity()
                        resetLevel()
                        running = false
                    }
                }
            } finally {
                detachAndRelease()
            }
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
