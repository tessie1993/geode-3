package dev.geode.analysis

import dev.geode.engine.audio.MidSideWindow
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

    private val publication = AnalysisPublicationGate()

    @Volatile
    var sampleRateHz: Int = 44100
        set(value) {
            require(value > 0) { "sampleRateHz must be positive" }
            publication.invalidate {
                if (field != value) resetPending.set(true)
                field = value
                _features.value = AudioFeatures.empty(bandCount)
            }
        }

    @Volatile
    var attack: Float = DEFAULT_ATTACK
        set(value) {
            publication.invalidate { field = value }
        }

    @Volatile
    var decay: Float = DEFAULT_DECAY
        set(value) {
            publication.invalidate { field = value }
        }

    @Volatile
    var beatSensitivity: Float = BeatTuning.SENSITIVITY_DEFAULT
        set(value) {
            publication.invalidate { field = BeatTuning.clampSensitivity(value) }
        }

    @Volatile
    var beatMinIntervalMs: Float = BeatTuning.INTERVAL_MS_DEFAULT
        set(value) {
            publication.invalidate { field = BeatTuning.clampIntervalMs(value) }
        }

    private val _features = MutableStateFlow(AudioFeatures.empty(bandCount))
    val features: StateFlow<AudioFeatures> = _features

    private val resetPending = AtomicBoolean(false)

    fun reset() {
        publication.invalidate {
            resetPending.set(true)
            _features.value = AudioFeatures.empty(bandCount)
        }
    }

    /**
     * A one-hop pulse held for the hops a display frame can span.
     *
     * The analyser fires `beat`, `transient`, `kick`, `snare`, `hat`, `downbeat`, `sectionBoundary`,
     * `drop` and `arrival` for exactly one 16 ms hop. [features] is a StateFlow, which conflates,
     * and the renderer reads it once per display frame, so at 30 fps (the thermal governor's
     * paced rate) every other pulse used to be lost before any scene saw it. Each pulse now
     * stays in the emitted frame for [PULSE_HOLD_HOPS] hops, long enough for any consumer
     * sampling at 20 Hz or better. Consumers that must fire once per pulse edge-detect already
     * (`live::Edge` in the fluid emitters); the rest take a max-envelope, for which a held
     * value is the same value.
     */
    private class PulseHold {
        var level = 0f
            private set
        private var hopsLeft = 0

        fun step(value: Float): Float {
            if (value > 0f) {
                level = maxOf(level, value)
                hopsLeft = PULSE_HOLD_HOPS
            } else if (hopsLeft > 0) {
                hopsLeft--
                if (hopsLeft == 0) level = 0f
            }
            return level
        }

        fun reset() {
            level = 0f
            hopsLeft = 0
        }
    }

    private inner class Pass {
        private val window = MidSideWindow(ring, fftSize)
        private val beat = PulseHold()
        private val beatStrength = PulseHold()
        private val transient = PulseHold()
        private val kick = PulseHold()
        private val snare = PulseHold()
        private val hat = PulseHold()
        private val downbeat = PulseHold()
        private val sectionBoundary = PulseHold()
        private val drop = PulseHold()
        private val arrival = PulseHold()
        private var sourceEpoch = ring.epoch
        private var sourceRate = sampleRateHz

        private var lastInputNs = System.nanoTime()
        private var quiet = true

        init {
            window.discardExisting()
            reset()
        }

        fun reset() {
            analyzer.reset()
            listOf(beat, beatStrength, transient, kick, snare, hat, downbeat, sectionBoundary, drop, arrival)
                .forEach(PulseHold::reset)
            _features.value = AudioFeatures.empty(bandCount)
            quiet = true
        }

        fun discardInput() = window.discardExisting()

        fun tick(configuration: Configuration): Boolean {
            if (resetPending.get()) return false
            val now = System.nanoTime()
            val epoch = ring.epoch
            val rate = configuration.rate
            if (sourceEpoch != epoch || sourceRate != rate) {
                reset()
                sourceEpoch = epoch
                sourceRate = rate
            }
            if (!window.refresh()) {
                if (!quiet && now - lastInputNs >= INPUT_IDLE_NS) reset()
                return false
            }
            val position = checkNotNull(window.position)
            if (position.epoch != sourceEpoch) {
                reset()
                sourceEpoch = position.epoch
            }
            analyzer.analyze(window.mid, window.side, DT_SECONDS)

            // A seek/source switch can happen while JNI analyzes the old window.
            if (ring.epoch != position.epoch || !publication.isCurrent(configuration.generation)) {
                reset()
                return false
            }
            lastInputNs = now
            quiet = false

            val next =
                AudioFeatures(
                    bands = analyzer.bands.copyOf(),
                    waveform = analyzer.waveform.copyOf(),
                    rms = analyzer.rms,
                    bass = analyzer.bass,
                    mid = analyzer.mid,
                    treble = analyzer.treble,
                    onset = analyzer.onset,
                    beat = beat.step(if (analyzer.beat) 1f else 0f) > 0f,
                    bpm = analyzer.bpm,
                    centroid = analyzer.centroid,
                    flux = analyzer.fluxValue,
                    beatStrength = beatStrength.step(analyzer.beatStrength),
                    transient = transient.step(analyzer.transient),
                    beatPhase = analyzer.beatPhase,
                    pulseConfidence = analyzer.pulseConfidence,
                    macroEnergy = analyzer.macroEnergy,
                    kick = kick.step(analyzer.kick),
                    snare = snare.step(analyzer.snare),
                    hat = hat.step(analyzer.hat),
                    chroma = analyzer.chroma.copyOf(),
                    chromaConfidence = analyzer.chromaConfidence,
                    stereoWidth = analyzer.stereoWidth,
                    stereoCorrelation = analyzer.stereoCorrelation,
                    stereoPan = analyzer.stereoPan,
                    tempoStability = analyzer.tempoStability,
                    barPhase = analyzer.barPhase,
                    beatInBar = analyzer.beatInBar,
                    downbeat = downbeat.step(if (analyzer.downbeat) 1f else 0f) > 0f,
                    downbeatConfidence = analyzer.downbeatConfidence,
                    novelty = analyzer.novelty,
                    sectionBoundary = sectionBoundary.step(if (analyzer.sectionBoundary) 1f else 0f) > 0f,
                    buildup = analyzer.buildup,
                    drop = drop.step(if (analyzer.drop) 1f else 0f) > 0f,
                    arrival = arrival.step(if (analyzer.arrival) 1f else 0f) > 0f,
                    harmonicity = analyzer.harmonicity,
                    warmup = analyzer.warmup,
                )
            return publication.publish(configuration.generation) {
                if (ring.epoch == position.epoch && !resetPending.get()) _features.value = next
            }
        }
    }

    private val worker = AnalysisWorker(loop = ::runAnalysis, release = analyzer::close)

    // Settings are published by UI/audio threads, but only this worker touches
    // ReactiveAnalyzer: its native handle and tuning are not thread safe.
    private data class Configuration(
        val generation: Long,
        val rate: Int,
        val attackSeconds: Float,
        val releaseSeconds: Float,
        val sensitivity: Float,
        val intervalMs: Float,
    )

    private fun configuration(): Configuration =
        publication.snapshot { generation ->
            Configuration(
                generation,
                sampleRateHz,
                BeatTuning.envelopeSeconds(attack),
                BeatTuning.envelopeSeconds(decay),
                beatSensitivity,
                beatMinIntervalMs,
            )
        }

    private fun applyConfiguration(configuration: Configuration) {
        val (_, rate, attackSeconds, releaseSeconds, sensitivity, intervalMs) = configuration
        if (analyzer.sampleRateHz != rate) analyzer.sampleRateHz = rate
        if (analyzer.attackSeconds != attackSeconds) analyzer.attackSeconds = attackSeconds
        if (analyzer.releaseSeconds != releaseSeconds) analyzer.releaseSeconds = releaseSeconds
        if (analyzer.sensitivity != sensitivity) analyzer.sensitivity = sensitivity
        if (analyzer.refractoryMs != intervalMs) analyzer.refractoryMs = intervalMs
    }

    private suspend fun runAnalysis() {
        val pass = Pass()
        var deadlineNs = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            if (resetPending.getAndSet(false)) {
                pass.discardInput()
                pass.reset()
            }
            val configuration = configuration()
            applyConfiguration(configuration)
            pass.tick(configuration)
            deadlineNs += TICK_NS
            val now = System.nanoTime()
            if (deadlineNs < now) deadlineNs = now
            // A positive delay is a cancellation point even when a tick overruns.
            delay(maxOf(1L, (deadlineNs - now) / 1_000_000))
        }
    }

    fun start(scope: CoroutineScope) = worker.start(scope)

    fun stop() {
        reset()
        worker.stop()
    }

    /** Schedules safe native teardown without blocking the caller on an in-flight FFT. */
    fun close() {
        reset()
        worker.close()
    }

    /** Waits for the same teardown scheduled by [close], including native destruction. */
    suspend fun closeAndJoin() {
        reset()
        worker.close().await()
    }

    companion object {
        private const val TICK_NS = 16_000_000L
        // Covers ordinary decoder block jitter, but never sustains a paused hit.
        private const val INPUT_IDLE_NS = 250_000_000L

        // Three hops is 48 ms: one 30 fps display frame plus scheduling jitter.
        private const val PULSE_HOLD_HOPS = 3

        internal const val HOP_RATE_HZ = 1000f / 16f
        internal const val DT_SECONDS = 16f / 1000f

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

/** Serializes configuration invalidation with publication, never with JNI analysis. */
internal class AnalysisPublicationGate {
    private val lock = Any()
    private var generation = 0L

    fun invalidate(change: () -> Unit) {
        synchronized(lock) {
            change()
            generation++
        }
    }

    fun <T> snapshot(read: (Long) -> T): T = synchronized(lock) { read(generation) }

    fun isCurrent(expected: Long): Boolean = synchronized(lock) { generation == expected }

    fun publish(expected: Long, action: () -> Unit): Boolean =
        synchronized(lock) {
            if (generation != expected) return false
            action()
            true
        }
}
