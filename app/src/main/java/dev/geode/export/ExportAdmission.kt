package dev.geode.export

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/** One foreground-service lease. A previous service callback cannot admit a new export. */
internal class ExportAdmission {
    val id: Long = nextId.incrementAndGet()
    private val ready = CompletableDeferred<Unit>()
    private var job: Job? = null
    private var cancelled = false

    @Volatile
    var failure: String? = null
        private set

    @Synchronized
    fun bind(job: Job) {
        this.job = job
        if (cancelled) job.cancel()
    }

    @Synchronized
    fun promoted() {
        if (!cancelled) ready.complete(Unit)
    }

    @Synchronized
    fun cancel(reason: String? = null) {
        if (cancelled) return
        failure = reason
        cancelled = true
        ready.cancel()
        job?.cancel()
    }

    suspend fun awaitPromotion(timeoutMs: Long = ADMISSION_TIMEOUT_MS) {
        checkNotNull(withTimeoutOrNull(timeoutMs) { ready.await() }) {
            "Export foreground service did not become ready in time"
        }
        currentCoroutineContext().ensureActive()
    }

    private companion object {
        val nextId = AtomicLong()
        const val ADMISSION_TIMEOUT_MS = 10_000L
    }
}
