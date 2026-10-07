package dev.geode.export

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ExportRun {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class State(
        val running: Boolean = false,
        val runId: Long? = null,
        val isLoop: Boolean = false,
        val failure: String? = null,
        val progress: Float? = null,
        val label: String = "",
        val secondsRemaining: Long? = null,
    )

    private val eta = RenderEta()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val running: Boolean get() = _state.value.running

    @Volatile
    var cancelRequested: Boolean = false
        private set

    private var admission: ExportAdmission? = null

    @Synchronized
    internal fun current(id: Long): ExportAdmission? = admission?.takeIf { it.id == id }

    internal fun requestCancel(id: Long?) {
        val lease = synchronized(this) {
            if (id == null || admission?.id != id) return
            cancelRequested = true
            admission
        }
        // Cancellation may synchronously invoke completion handlers; release the run lock first.
        lease?.cancel()
    }

    @Synchronized
    internal fun begin(label: String, isLoop: Boolean = false): ExportAdmission? {
        if (admission != null) return null
        val lease = ExportAdmission()
        admission = lease
        eta.reset()
        cancelRequested = false
        _state.value = State(running = true, runId = lease.id, isLoop = isLoop, progress = null, label = label)
        return lease
    }

    internal fun track(lease: ExportAdmission, job: Job, onCancelledBeforeStart: () -> Unit) {
        job.invokeOnCompletion {
            // A coroutine cancelled before dispatch never enters its try/finally.
            if (current(lease.id) === lease) {
                try {
                    onCancelledBeforeStart()
                } finally {
                    finish(lease)
                }
            }
        }
    }

    fun publish(
        progress: Float,
        atMs: Long = android.os.SystemClock.elapsedRealtime(),
    ) {
        val current = _state.value
        if (!current.running) return
        val clamped = progress.coerceIn(0f, 1f)
        _state.value = current.copy(progress = clamped, secondsRemaining = eta.sample(clamped, atMs))
    }

    @Synchronized
    internal fun finish(lease: ExportAdmission) {
        if (admission !== lease) return
        admission = null
        eta.reset()
        _state.value = State(failure = lease.failure)
    }
}
