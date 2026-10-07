package dev.geode.export

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAdmissionTest {
    @Test
    fun `analysis cannot start before foreground promotion`() =
        runTest {
            val admission = ExportAdmission()
            var analyzed = false
            launch {
                admission.bind(currentCoroutineContext().job)
                admission.awaitPromotion()
                analyzed = true
            }
            runCurrent()
            assertFalse(analyzed)
            admission.promoted()
            runCurrent()
            assertTrue(analyzed)
        }

    @Test
    fun `admission timeout fails without starting analysis`() =
        runTest {
            val admission = ExportAdmission()
            var failure: String? = null
            var analyzed = false
            launch {
                try {
                    admission.awaitPromotion(100L)
                    analyzed = true
                } catch (expected: IllegalStateException) {
                    failure = expected.message
                }
            }
            advanceTimeBy(101L)
            runCurrent()
            assertFalse(analyzed)
            assertTrue(failure.orEmpty().contains("did not become ready"))
        }

    @Test
    fun `service failure cancels running analysis and invokes cleanup`() =
        runTest {
            val admission = ExportAdmission()
            var cleaned = false
            val worker = launch {
                admission.bind(currentCoroutineContext().job)
                admission.awaitPromotion()
                try {
                    awaitCancellation()
                } finally {
                    cleaned = true
                }
            }
            admission.promoted()
            runCurrent()
            admission.cancel("Foreground service stopped")
            runCurrent()
            assertTrue(worker.isCancelled)
            assertTrue(cleaned)
            assertEquals("Foreground service stopped", admission.failure)
        }

    @Test
    fun `old service callbacks cannot admit or cancel the next run`() =
        runTest {
            val old = ExportAdmission()
            val next = ExportAdmission()
            var analyzed = false
            val worker = launch {
                next.bind(currentCoroutineContext().job)
                next.awaitPromotion()
                analyzed = true
            }
            old.promoted()
            old.cancel("Late destruction")
            runCurrent()
            assertFalse(analyzed)
            assertTrue(worker.isActive)
            next.promoted()
            runCurrent()
            assertTrue(analyzed)
        }

    @Test
    fun `failure before job attachment cannot allow work`() =
        runTest {
            val admission = ExportAdmission()
            admission.cancel("Promotion failed")
            var analyzed = false
            val worker = launch {
                admission.bind(currentCoroutineContext().job)
                admission.promoted()
                admission.awaitPromotion()
                analyzed = true
            }
            runCurrent()
            assertTrue(worker.isCancelled)
            assertFalse(analyzed)
        }
}
