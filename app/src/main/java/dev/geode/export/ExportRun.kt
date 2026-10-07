package dev.geode.export

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object ExportRun {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class State(
        val running: Boolean = false,
        val runId: Long? = null,
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

    fun requestCancel() {
        val lease = synchronized(this) {
            cancelRequested = true
            admission
        }
        // Job cancellation may synchronously invoke completion handlers. Do not
        // hold the run lock while taking the admission's lock.
        lease?.cancel()
    }

    @Synchronized
    internal fun begin(label: String): ExportAdmission? {
        if (admission != null) return null
        val lease = ExportAdmission()
        admission = lease
        eta.reset()
        cancelRequested = false
        _state.value = State(running = true, runId = lease.id, progress = null, label = label)
        return lease
    }

    /** Attach cleanup before dispatch: cancellation can prevent the body and its finally from running. */
    internal fun launch(
        lease: ExportAdmission,
        workerScope: CoroutineScope = scope,
        onCancelled: () -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val worker = workerScope.launch(start = CoroutineStart.LAZY, block = block)
        worker.invokeOnCompletion { cause -> complete(lease, cause, onCancelled) }
        lease.bind(worker)
        worker.start()
        return worker
    }

    @Synchronized
    private fun complete(
        lease: ExportAdmission,
        cause: Throwable?,
        onCancelled: () -> Unit,
    ) {
        if (admission !== lease) return
        try {
            if (cause is CancellationException) onCancelled()
        } finally {
            finish(lease)
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
        _state.value = State()
    }
}
