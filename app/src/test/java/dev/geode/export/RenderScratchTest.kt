package dev.geode.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RenderScratchTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `delayed sweep preserves files created by the current process`() {
        val cache = temporary.newFolder()
        val oldDirectory = File(cache, "geode-render-previous").apply { mkdirs() }
        val oldFrame = File(oldDirectory, "studio-old.mp4").apply { writeText("old") }
        val legacy = File(cache, "geode_aac_old.bin").apply { writeText("old") }
        val unrelated = File(cache, "cover.jpg").apply { writeText("keep") }

        // Simulate the cleanup task being queued, then a render starting before it executes.
        val active = File(RenderScratch.directory(cache), "studio-current.mp4").apply { writeText("active") }
        RenderScratch.sweepPreviousRuns(cache)

        assertTrue(active.exists())
        assertTrue(unrelated.exists())
        assertFalse(oldFrame.exists())
        assertFalse(oldDirectory.exists())
        assertFalse(legacy.exists())
    }
}
