package dev.geode.analysis

import dev.geode.engine.audio.ReactiveAnalyzer
import dev.geode.engine.audio.SampleRing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class AnalysisEngine(
    private val ring: SampleRing,
    val bandCount: Int = DEFAULT_BAND_COUNT,
    private val fftSize: Int = DEFAULT_FFT_SIZE,
) {
    private val analyzer =
        ReactiveAnalyzer(
            bandCount = bandCount,
            fftSize = fftSize,
            hopRateHz = HOP_RATE_HZ,
        )

    private val publicationLock = Any()

    @Volatile
    var sampleRateHz: Int = 44100
        set(value) {
            synchronized(publicationLock) {
                if (field != value) {
                    field = value
                    requestReset()
                }
            }
        }

    @Volatile
    var attack: Float = DEFAULT_ATTACK

    @Volatile
    var decay: Float = DEFAULT_DECAY

    @Volatile
    var beatSensitivity: Float = BeatTuning.SENSITIVITY_DEFAULT
        set(value) {
            field = BeatTuning.clampSensitivity(value)
        }

    @Volatile
    var beatMinIntervalMs: Float = BeatTuning.INTERVAL_MS_DEFAULT
        set(value) {
            field = BeatTuning.clampIntervalMs(value)
        }

    private val _features = MutableStateFlow(AudioFeatures.empty(bandCount))
    val features: StateFlow<AudioFeatures> = _features

    private val resetPending = AtomicBoolean(false)

    private data class ResetRequest(
        val position: SampleRing.Position,
        val sampleRateHz: Int,
    )

    private var pendingReset: ResetRequest? = null

    fun reset() {
        synchronized(publicationLock) { requestReset() }
    }

    private fun requestReset() {
        pendingReset = ResetRequest(ring.position(), sampleRateHz)
        resetPending.set(true)
        _features.value = AudioFeatures.empty(bandCount)
    }

    private inner class Pass {
        private val input = AnalysisInput(ring, fftSize)
        private val frameGate =
            AnalysisFrameGate(publicationLock, { sampleRateHz }) {
                !resetPending.get() && ring.epoch == input.window.epoch
            }
        private var silent = false
        private val events = AnalysisEvents()

        private fun resetAnalyzer() {
            analyzer.reset()
            events.reset()
        }

        fun reset(request: ResetRequest) {
            input.reset(request.position, request.sampleRateHz)
            resetAnalyzer()
            silent = false
        }

        fun tick(configuredRateHz: Int): Boolean {
            val nowNs = System.nanoTime()
            events.beginBatch()
            var newest: AudioFeatures? = null
            var hopsRemaining = AnalysisInput.MAX_HOPS_PER_WAKE
            while (hopsRemaining > 0) {
                hopsRemaining--
                val state = input.poll(nowNs, configuredRateHz)
                if (input.discontinuity) {
                    // The cursor already selected a retained window. Reset DSP
                    // history without discarding that new epoch's fresh PCM.
                    resetAnalyzer()
                    newest = null
                    silent = false
                    synchronized(publicationLock) {
                        _features.value = AudioFeatures.empty(bandCount)
                    }
                }
                if (state == AnalysisInput.State.SILENT) {
                    if (!silent) {
                        resetAnalyzer()
                        synchronized(publicationLock) {
                            _features.value = AudioFeatures.empty(bandCount)
                        }
                        silent = true
                    }
                    return false
                }
                if (state == AnalysisInput.State.WAITING) break
                silent = false
                val window = input.window
                val accepted =
                    frameGate.run(
                        configuredRateHz,
                        analyze = {
                            analyzer.analyze(window.mid, window.side, input.dtSeconds)
                            snapshotFeatures()
                        },
                        publish = { frame ->
                            newest = frame
                            events.add(frame)
                        },
                    )
                if (!accepted) {
                    resetAnalyzer()
                    return false
                }
            }
            // Only continuous values come from the newest window. Event maxima
            // across all drained hops survive StateFlow/display conflation.
            // WAITING advances these wall-time holds without a native FFT.
            val frame = events.apply(newest ?: _features.value, nowNs)
            return frameGate.run(
                configuredRateHz,
                analyze = { frame },
                publish = { _features.value = it },
            )
        }

        private fun snapshotFeatures(): AudioFeatures =
            AudioFeatures(
                bands = analyzer.bands.copyOf(),
                waveform = analyzer.waveform.copyOf(),
                rms = analyzer.rms,
                bass = analyzer.bass,
                mid = analyzer.mid,
                treble = analyzer.treble,
                onset = analyzer.onset,
                beat = analyzer.beat,
                bpm = analyzer.bpm,
                centroid = analyzer.centroid,
                flux = analyzer.fluxValue,
                beatStrength = analyzer.beatStrength,
                transient = analyzer.transient,
                beatPhase = analyzer.beatPhase,
                pulseConfidence = analyzer.pulseConfidence,
                macroEnergy = analyzer.macroEnergy,
                kick = analyzer.kick,
                snare = analyzer.snare,
                hat = analyzer.hat,
                chroma = analyzer.chroma.copyOf(),
                chromaConfidence = analyzer.chromaConfidence,
                stereoWidth = analyzer.stereoWidth,
                stereoCorrelation = analyzer.stereoCorrelation,
                stereoPan = analyzer.stereoPan,
                tempoStability = analyzer.tempoStability,
                barPhase = analyzer.barPhase,
                beatInBar = analyzer.beatInBar,
                downbeat = analyzer.downbeat,
                downbeatConfidence = analyzer.downbeatConfidence,
                novelty = analyzer.novelty,
                sectionBoundary = analyzer.sectionBoundary,
                buildup = analyzer.buildup,
                drop = analyzer.drop,
                arrival = analyzer.arrival,
                harmonicity = analyzer.harmonicity,
                warmup = analyzer.warmup,
            )
    }

    private val worker = AnalysisWorker(loop = ::runAnalysis, release = analyzer::close)

    // Settings are published by UI/audio threads, but only this worker touches
    // ReactiveAnalyzer: its native handle and tuning are not thread safe.
    private fun applyConfiguration(): Int {
        val rate = sampleRateHz
        val attackSeconds = BeatTuning.envelopeSeconds(attack)
        val releaseSeconds = BeatTuning.envelopeSeconds(decay)
        val sensitivity = beatSensitivity
        val intervalMs = beatMinIntervalMs
        if (analyzer.sampleRateHz != rate) analyzer.sampleRateHz = rate
        if (analyzer.attackSeconds != attackSeconds) analyzer.attackSeconds = attackSeconds
        if (analyzer.releaseSeconds != releaseSeconds) analyzer.releaseSeconds = releaseSeconds
        if (analyzer.sensitivity != sensitivity) analyzer.sensitivity = sensitivity
        if (analyzer.refractoryMs != intervalMs) analyzer.refractoryMs = intervalMs
        return rate
    }

    private suspend fun runAnalysis() {
        val pass = Pass()
        var deadlineNs = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            val configuredRateHz = applyConfiguration()
            val resetRequest =
                synchronized(publicationLock) {
                    if (resetPending.getAndSet(false)) {
                        pendingReset.also { pendingReset = null }
                    } else {
                        null
                    }
                }
            if (resetRequest != null) pass.reset(resetRequest)
            if (configuredRateHz == sampleRateHz) pass.tick(configuredRateHz)
            deadlineNs += TICK_NS
            val now = System.nanoTime()
            if (deadlineNs < now) deadlineNs = now
            // A positive delay is a cancellation point even when a tick overruns.
            delay(maxOf(1L, (deadlineNs - now) / 1_000_000))
        }
    }

    fun start(scope: CoroutineScope) = worker.start(scope)

    fun stop() = worker.stop()

    /** Schedules safe native teardown without blocking the caller on an in-flight FFT. */
    fun close() {
        worker.close()
    }

    /** Waits for the same teardown scheduled by [close], including native destruction. */
    suspend fun closeAndJoin() {
        worker.close().await()
    }

    companion object {
        private const val TICK_NS = 16_000_000L

        internal const val HOP_RATE_HZ = 1000f / 16f
        const val DEFAULT_BAND_COUNT = 64

        const val DEFAULT_FFT_SIZE = 2048

        const val DEFAULT_ATTACK = 0.6f
        const val DEFAULT_DECAY = 0.12f
    }
}

/** Serializes native resource ownership across cancelled loops, restarts and teardown. */
internal class AnalysisWorker(
    private val loop: suspend () -> Unit,
    private val release: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val jobLock = Any()
    private val resourceLock = Mutex()
    private var job: Job? = null
    private var closing: Deferred<Unit>? = null

    @Volatile
    private var closed = false

    fun start(scope: CoroutineScope) {
        synchronized(jobLock) {
            if (closed || job?.isActive == true) return
            job =
                scope.launch(dispatcher) {
                    // Cancellation is cooperative: a previous job can still be in JNI.
                    // Keep ownership until its entire loop has unwound before restart.
                    resourceLock.withLock {
                        currentCoroutineContext().ensureActive()
                        if (!closed) loop()
                    }
                }
        }
    }

    fun stop() {
        synchronized(jobLock) {
            job?.cancel()
            job = null
        }
    }

    fun close(): Deferred<Unit> =
        synchronized(jobLock) {
            closing ?: run {
                closed = true
                job?.cancel()
                job = null
                // PlaybackSession cancels its scope immediately after close(). This
                // finite cleanup job must survive that cancellation, and never blocks
                // the main thread or destroys a handle still owned by a stopped loop.
                CoroutineScope(dispatcher)
                    .async {
                        resourceLock.withLock { release() }
                    }.also { closing = it }
            }
        }
}
