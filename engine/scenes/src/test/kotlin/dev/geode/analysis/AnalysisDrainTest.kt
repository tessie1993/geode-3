package dev.geode.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AnalysisDrainTest {
    @Test
    fun `cancelled analysis never enters the native pull`() {
        val job = Job()
        job.cancel()
        var pulls = 0
        try {
            drainAnalysisFrames(
                stillWanted = { job.isActive },
                pull = {
                    pulls++
                    true
                },
                consume = { error("no result expected") },
            )
            fail("cancelled drain must throw")
        } catch (expected: CancellationException) {
            assertEquals(0, pulls)
        }
    }

    @Test
    fun `cancellation during native pull cannot retain the returned frame or pull again`() {
        val job = Job()
        var pulls = 0
        var consumed = 0
        try {
            drainAnalysisFrames(
                stillWanted = { job.isActive },
                pull = {
                    pulls++
                    if (pulls == 2) job.cancel()
                    true
                },
                consume = { consumed++ },
            )
            fail("cancelled drain must throw")
        } catch (expected: CancellationException) {
            assertEquals(2, pulls)
            assertEquals(1, consumed)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `completed native drain consumes each available frame once`() {
        var pulls = 0
        var consumed = 0
        drainAnalysisFrames({ true }, { ++pulls <= 2 }) { consumed++ }
        assertEquals(3, pulls)
        assertEquals(2, consumed)
    }
}
