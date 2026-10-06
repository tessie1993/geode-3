package dev.geode.analysis

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisWorkerTest {
    @Test
    fun `restart waits for a cancelled native call to return`() =
        runTest {
            val nativeReturned = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            var starts = 0
            val worker =
                AnalysisWorker(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    loop = {
                        val pass = ++starts
                        events += "start $pass"
                        try {
                            if (pass == 1) {
                                // JNI cannot observe coroutine cancellation mid-call.
                                withContext(NonCancellable) { nativeReturned.await() }
                            } else {
                                awaitCancellation()
                            }
                        } finally {
                            events += "end $pass"
                        }
                    },
                    release = { events += "release" },
                )
            try {
                worker.start(this)
                runCurrent()
                worker.stop()
                worker.start(this)
                worker.start(this)
                runCurrent()
                assertEquals(listOf("start 1"), events)

                nativeReturned.complete(Unit)
                runCurrent()
                assertEquals(listOf("start 1", "end 1", "start 2"), events)
                worker.close().await()
                assertEquals(listOf("start 1", "end 1", "start 2", "end 2", "release"), events)
            } finally {
                nativeReturned.complete(Unit)
                worker.close().await()
            }
        }

    @Test
    fun `close waits for stopped work even after a restart was queued`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val owner = CoroutineScope(Job() + dispatcher)
            val nativeReturned = CompletableDeferred<Unit>()
            var insideNative = false
            var starts = 0
            var releases = 0
            val worker =
                AnalysisWorker(
                    dispatcher = dispatcher,
                    loop = {
                        starts++
                        insideNative = true
                        try {
                            withContext(NonCancellable) { nativeReturned.await() }
                        } finally {
                            insideNative = false
                        }
                    },
                    release = {
                        assertFalse("native destroy raced a native call", insideNative)
                        releases++
                    },
                )
            try {
                worker.start(owner)
                runCurrent()
                worker.stop()
                worker.start(owner)
                runCurrent()

                val closing = worker.close()
                owner.cancel()
                worker.start(this)
                runCurrent()
                assertFalse("close must wait without blocking the caller", closing.isCompleted)
                assertEquals(0, releases)
                assertEquals(1, starts)

                nativeReturned.complete(Unit)
                closing.await()
                assertEquals(1, releases)
                assertSame(closing, worker.close())
                worker.start(this)
                runCurrent()
                assertEquals(1, starts)
            } finally {
                nativeReturned.complete(Unit)
                owner.cancel()
                worker.close().await()
            }
        }

    @Test
    fun `close releases an engine that has never started exactly once`() =
        runTest {
            var releases = 0
            val worker =
                AnalysisWorker(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    loop = { error("a closed engine must not start") },
                    release = { releases++ },
                )
            val closing = worker.close()
            worker.stop()
            worker.start(this)
            assertSame(closing, worker.close())
            closing.await()
            assertEquals(1, releases)
        }
}
