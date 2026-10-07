package dev.geode.ui

import dev.geode.data.PerformanceTake
import dev.geode.data.TakeInfo
import dev.geode.data.TakeRepository
import dev.geode.data.TakeWrite
import dev.geode.render.scene.SceneParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAKE_REPLAY_HZ = 30L

data class TakeUiState(
    val takes: List<TakeInfo> = emptyList(),
    val recording: Boolean = false,
    val recordedEvents: Int = 0,
    val recordedMs: Long = 0L,
    val replaying: String? = null,
    val replayMs: Long = 0L,
    val replayEndMs: Long = 0L,
    val exportTake: String? = null,
    val note: String? = null,
    val pendingSave: Boolean = false,
    val saving: Boolean = false,
)

internal class TakeController(
    private val takes: TakeRepository,
    private val scope: CoroutineScope,
    private val storeScope: CoroutineScope,
    private val host: Host,
    private val nowMs: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    interface Host {
        val vizState: StateFlow<VizUiState>
        val activeMilkPath: String?

        val trackUri: String?

        val trackPositionMs: Long

        fun selectScene(sceneId: String)

        fun setSceneParams(params: SceneParams)

        fun applyMilk(
            path: String,
            sceneId: String,
        )
    }

    private val _state = MutableStateFlow(TakeUiState())

    val state: StateFlow<TakeUiState> = _state

    private var recorder: PerformanceTake.Recorder? = null
    private var recordStartMs = 0L

    private var recordTrackOffsetMs = 0L
    private var recordTrackUri: String? = null

    private data class PendingTake(
        val name: String,
        val recorder: PerformanceTake.Recorder,
        val trackUri: String?,
        val trackOffsetMs: Long,
        val durationMs: Long,
        val sourceChanged: Boolean,
        var json: String? = null,
    )

    @Volatile
    private var pendingTake: PendingTake? = null
    private val saveInFlight = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var recordTickJob: Job? = null
    private var replayJob: Job? = null

    fun startRecording() {
        if (_state.value.recording) return
        if (pendingTake != null || saveInFlight.get()) {
            _state.update { it.copy(note = if (it.saving) "Saving control take…" else TAKE_SAVE_FAILED_NOTE) }
            return
        }
        stopReplay()
        val s = host.vizState.value
        recorder = PerformanceTake.Recorder(s.sceneId, s.params, host.activeMilkPath)
        recordStartMs = nowMs()
        recordTrackUri = host.trackUri
        recordTrackOffsetMs = host.trackPositionMs
        _state.update { it.copy(recording = true, recordedEvents = 1, recordedMs = 0L, note = null) }
        recordJob =
            scope.launch {
                host.vizState.collect { live ->
                    if (sourceChanged()) return@collect
                    val rec = recorder ?: return@collect
                    val at = nowMs() - recordStartMs
                    rec.append(at, live.sceneId, live.params, host.activeMilkPath)
                    _state.update { it.copy(recordedEvents = rec.size, recordedMs = at) }
                    if (!rec.hasRoom) stopRecording()
                }
            }
        recordTickJob =
            scope.launch {
                while (true) {
                    delay(1_000L)
                    if (sourceChanged()) break
                    val at = nowMs() - recordStartMs
                    _state.update { if (it.recording) it.copy(recordedMs = at) else it }
                }
            }
    }

    fun onTrackChanged() {
        sourceChanged()
    }

    private fun sourceChanged(): Boolean {
        if (recorder == null || host.trackUri == recordTrackUri) return false
        finishRecording(null, sourceChanged = true)
        return true
    }

    fun stopRecording(name: String? = null) {
        finishRecording(name, sourceChanged = host.trackUri != recordTrackUri)
    }

    private fun finishRecording(
        name: String?,
        sourceChanged: Boolean,
    ) {
        val rec = recorder ?: return
        recordJob?.cancel()
        recordJob = null
        recordTickJob?.cancel()
        recordTickJob = null
        recorder = null
        val durationMs = (nowMs() - recordStartMs).coerceAtLeast(0L)
        _state.update { it.copy(recording = false, recordedEvents = 0, recordedMs = 0L) }
        if (rec.size <= 1) {
            _state.update { it.copy(note = TAKE_DISCARDED_NOTE) }
            scope.launch {
                delay(TAKE_NOTE_MS)
                _state.update { if (it.note == TAKE_DISCARDED_NOTE) it.copy(note = null) else it }
            }
            refresh()
            return
        }
        pendingTake =
            PendingTake(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Take",
                recorder = rec,
                trackUri = recordTrackUri,
                trackOffsetMs = recordTrackOffsetMs,
                durationMs = durationMs,
                sourceChanged = sourceChanged,
            )
        _state.update { it.copy(pendingSave = true) }
        retryTakeSave()
    }

    fun retryTakeSave() {
        val pending = pendingTake ?: return
        if (!saveInFlight.compareAndSet(false, true)) return
        _state.update { it.copy(saving = true, note = "Saving control take…") }
        storeScope.launch {
            try {
                val json =
                    pending.json ?: pending.recorder
                        .finish(pending.name, pending.trackUri, pending.durationMs, pending.trackOffsetMs)
                        .also { pending.json = it }
                when (val result = takes.save(pending.name, json)) {
                    is TakeWrite.Saved -> {
                        pendingTake = null
                        _state.update {
                            it.copy(
                                pendingSave = false,
                                note =
                                    if (pending.sourceChanged) {
                                        "Saved ${result.name}. Recording stopped when the audio source changed."
                                    } else {
                                        "Saved ${result.name}."
                                    },
                            )
                        }
                        refresh()
                    }
                    TakeWrite.Failed -> _state.update { it.copy(note = TAKE_SAVE_FAILED_NOTE) }
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(note = TAKE_SAVE_FAILED_NOTE) }
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(note = TAKE_SAVE_FAILED_NOTE) }
            }
        }.invokeOnCompletion { failure ->
            if (failure != null && pendingTake === pending) {
                _state.update { it.copy(note = TAKE_SAVE_FAILED_NOTE) }
            }
            _state.update { it.copy(saving = false) }
            saveInFlight.set(false)
        }
    }

    fun playTake(name: String) {
        if (_state.value.recording) stopRecording()
        stopReplay()
        replayJob =
            scope.launch {
                val timeline = takes.load(name) ?: return@launch
                if (timeline.isEmpty) return@launch
                val endMs = maxOf(timeline.lastEventMs(), timeline.durationMs)
                _state.update { it.copy(replaying = name, replayMs = 0L, replayEndMs = endMs) }
                val startedAt = nowMs()
                while (true) {
                    val at = nowMs() - startedAt
                    timeline.stateAt(at)?.let { state ->
                        if (state.sceneId.isNotEmpty() && state.sceneId != host.vizState.value.sceneId) {
                            host.selectScene(state.sceneId)
                        }
                        if (state.params != host.vizState.value.params) host.setSceneParams(state.params)
                        state.milkPath?.takeIf { it != host.activeMilkPath }?.let { path ->
                            host.applyMilk(path, state.sceneId)
                        }
                    }
                    _state.update { it.copy(replayMs = at) }
                    if (at >= endMs) break
                    delay(1000L / TAKE_REPLAY_HZ)
                }
                _state.update { it.copy(replaying = null, replayMs = 0L, replayEndMs = 0L) }
            }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        _state.update { it.copy(replaying = null, replayMs = 0L, replayEndMs = 0L) }
    }

    fun deleteTake(name: String) {
        if (_state.value.replaying == name) stopReplay()
        storeScope.launch {
            takes.delete(name)
            val listed = takes.list()
            _state.update { it.copy(takes = listed) }
        }
    }

    fun renameTake(
        from: String,
        to: String,
    ) {
        storeScope.launch {
            if (!takes.rename(from, to)) return@launch
            if (_state.value.replaying == from) stopReplay()
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            val listed = takes.list()
            _state.update { it.copy(takes = listed) }
        }
    }

    fun setExportTake(name: String?) {
        _state.update { it.copy(exportTake = name) }
    }

    suspend fun loadExportTake(): PerformanceTake.Timeline? =
        _state.value.exportTake
            ?.let { takes.load(it) }
            ?.takeUnless { it.isEmpty }

    private companion object {
        const val TAKE_SAVE_FAILED_NOTE = "Could not save this control take. It is retained in this session. Retry save."

        const val TAKE_DISCARDED_NOTE = "Nothing changed — take not saved"

        const val TAKE_NOTE_MS = 4_000L
    }
}
