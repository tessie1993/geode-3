package dev.geode.export

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RenderScratchTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `delayed startup sweep cannot delete current-run exports`() =
        runTest {
            val cache = temporary.newFolder()
            val prior = File(cache, "geode-render-prior-run").apply { mkdirs() }
            val orphan = File(prior, "studio-old.mp4").apply { writeText("orphan") }
            val legacy = File(cache, "geode_aac_old.bin").apply { writeText("orphan") }
            val unrelated = File(cache, "other-cache.bin").apply { writeText("keep") }
            val sweep = launch { RenderScratch.sweepPreviousRuns(cache) }
            // The IO coroutine has not started; a render can already have created scratch.
            val active = File(RenderScratch.directory(cache), "studio-current.mp4").apply { writeText("active") }
            runCurrent()
            sweep.join()
            assertFalse(orphan.exists())
            assertFalse(legacy.exists())
            assertTrue(active.exists())
            assertTrue(unrelated.exists())
            RenderScratch.sweepPreviousRuns(cache)
            assertTrue(active.exists())
        }
}
