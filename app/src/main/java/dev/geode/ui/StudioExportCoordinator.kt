package dev.geode.ui

import dev.geode.export.StudioClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns one Studio export until its coroutine, including cancellation cleanup, has finished. */
internal class StudioExportCoordinator(
    private val scope: CoroutineScope,
    private val loadClips: suspend () -> List<StudioClip>,
) {
    private class ActiveExport(
        val token: Any,
        val job: Job,
    )

    private val lock = Any()
    private val mutableState = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = mutableState
    private var activeExport: ActiveExport? = null
    private var libraryRevision = 0L

    fun refreshClips() {
        if (!scope.isActive) return
        val revision =
            synchronized(lock) {
                mutableState.update { it.copy(clipsLoading = true, clipsError = null) }
                ++libraryRevision
            }
        scope.launch {
            try {
                val clips = loadClips()
                synchronized(lock) {
                    if (revision == libraryRevision) mutableState.update { it.copy(clips = clips) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(lock) {
                    if (revision == libraryRevision) {
                        mutableState.update { it.copy(clipsError = error.message ?: error.javaClass.simpleName) }
                    }
                }
            } finally {
                synchronized(lock) {
                    if (revision == libraryRevision) mutableState.update { it.copy(clipsLoading = false) }
                }
            }
        }
    }

    fun start(export: suspend (onProgress: (Float) -> Unit) -> ExportPhase): Boolean {
        val token = Any()
        val job =
            synchronized(lock) {
                if (activeExport != null) return false
                val created =
                    scope.launch(start = CoroutineStart.LAZY) {
                        val result =
                            try {
                                export { progress -> publishProgress(token, progress) }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                ExportPhase.Failed(error.message ?: error.javaClass.simpleName)
                            }
                        synchronized(lock) {
                            if (activeExport?.token === token) mutableState.update { it.copy(phase = result) }
                        }
                        refreshClips()
                    }
                activeExport = ActiveExport(token, created)
                mutableState.update { it.copy(phase = ExportPhase.Running(0f)) }
                created.invokeOnCompletion { cause ->
                    var refreshAfterCancel = false
                    synchronized(lock) {
                        if (activeExport?.token === token) {
                            if (cause is CancellationException && mutableState.value.phase.isRunning) {
                                mutableState.update { it.copy(phase = ExportPhase.Idle) }
                            }
                            refreshAfterCancel = cause is CancellationException
                            activeExport = null
                        }
                    }
                    // Publishing a MediaStore row is the commit point. Cancellation can win
                    // delivery of Saved afterwards, so reconcile the library to discover it.
                    if (refreshAfterCancel) refreshClips()
                }
                created
            }
        job.start()
        return true
    }

    fun cancel() {
        // Do not release ownership until the export's finally blocks have released its resources.
        synchronized(lock) { activeExport?.job?.cancel() }
    }

    fun clearResult() {
        synchronized(lock) {
            if (activeExport == null) mutableState.update { it.copy(phase = ExportPhase.Idle) }
        }
    }

    private fun publishProgress(
        token: Any,
        progress: Float,
    ) {
        synchronized(lock) {
            if (activeExport?.token === token && mutableState.value.phase.isRunning && progress.isFinite()) {
                mutableState.update { it.copy(phase = ExportPhase.Running(progress.coerceIn(0f, 1f))) }
            }
        }
    }
}
