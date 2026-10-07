package dev.geode.export

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportRunTest {
    @Test
    fun `cancellation before dispatch releases admission and clears busy UI without starting work`() =
        runTest {
            val lease = checkNotNull(ExportRun.begin("queued"))
            var uiBusy = true
            var serviceStarts = 0
            var analysisStarts = 0
            val worker =
                ExportRun.launch(lease, workerScope = this, onCancelled = { uiBusy = false }) {
                    serviceStarts++
                    lease.awaitPromotion()
                    analysisStarts++
                }
            try {
                worker.cancel()
                runCurrent()
                assertFalse(ExportRun.running)
                assertFalse(uiBusy)
                assertEquals(0, serviceStarts)
                assertEquals(0, analysisStarts)
                val next = checkNotNull(ExportRun.begin("next"))
                ExportRun.finish(next)
            } finally {
                worker.cancel()
                worker.join()
                ExportRun.finish(lease)
            }
        }

    @Test
    fun `cancel before launch attachment also invokes completion cleanup`() =
        runTest {
            val lease = checkNotNull(ExportRun.begin("cancelled"))
            lease.cancel("Foreground promotion failed")
            var cancelledUi = false
            val worker =
                ExportRun.launch(lease, workerScope = this, onCancelled = { cancelledUi = true }) {
                    error("cancelled admission must not start service or analysis")
                }
            worker.join()
            assertTrue(cancelledUi)
            assertFalse(ExportRun.running)
        }

    @Test
    fun `late completion cannot clear another lease or its UI`() =
        runTest {
            val old = checkNotNull(ExportRun.begin("old"))
            var oldUiChanges = 0
            val worker =
                ExportRun.launch(old, workerScope = this, onCancelled = { oldUiChanges++ }) {
                    error("queued job was cancelled")
                }
            ExportRun.finish(old)
            val next = checkNotNull(ExportRun.begin("next"))
            try {
                worker.cancel()
                runCurrent()
                assertEquals(0, oldUiChanges)
                assertTrue(ExportRun.running)
                assertEquals(next.id, checkNotNull(ExportRun.state.value.runId))
            } finally {
                worker.cancel()
                worker.join()
                ExportRun.finish(next)
            }
        }

    @Test
    fun `service loss ends a nonsuspending render polling loop and releases admission`() =
        runTest {
            val lease = checkNotNull(ExportRun.begin("render"))
            var frames = 0
            var failureUi: String? = null
            lease.promoted()
            val worker =
                ExportRun.launch(lease, workerScope = this, onCancelled = { failureUi = lease.failure }) {
                    lease.awaitPromotion()
                    // Same lease predicate supplied to VideoExporter, LoopRender and LoopExtend.
                    // There is deliberately no suspension point inside this codec-style loop.
                    while (!lease.isCancelled) {
                        frames++
                        if (frames == 2) lease.cancel("Foreground service stopped")
                        check(frames < 3) { "blocking renderer ignored service cancellation" }
                    }
                }
            worker.join()
            assertEquals(2, frames)
            assertTrue(worker.isCancelled)
            assertEquals("Foreground service stopped", failureUi)
            assertFalse(ExportRun.running)
        }
}
