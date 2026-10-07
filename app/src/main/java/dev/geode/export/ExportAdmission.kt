package dev.geode.export

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** One foreground-service lease. A previous service callback cannot admit a new export. */
internal class ExportAdmission {
    val id: Long = nextId.incrementAndGet()
    private val ready = CompletableDeferred<Unit>()

    @Volatile
    private var job: Job? = null

    @Volatile
    private var cancelled = false

    /** Codec/render loops do not suspend; they must observe service and job cancellation here. */
    val isCancelled: Boolean get() = cancelled || job?.isCancelled == true

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
        try {
            withTimeout(timeoutMs) { ready.await() }
            currentCoroutineContext().ensureActive()
        } catch (timeout: TimeoutCancellationException) {
            throw IllegalStateException("Export foreground service did not become ready in time", timeout)
        }
    }

    private companion object {
        val nextId = AtomicLong()
        const val ADMISSION_TIMEOUT_MS = 10_000L
    }
}
