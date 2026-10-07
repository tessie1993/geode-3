package dev.geode.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.render.scene.SceneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TakeStoreDurabilityTest {
    @Test
    fun failedWriteDoesNotReportSavedOrReplaceExistingTake() {
        withDirectory { dir ->
            val good = TakeStore(dir)
            val body = capture("First")
            assertEquals(TakeWrite.Saved("First"), good.save("First", body))
            val failing = TakeStore(dir) { _, _ -> false }
            assertEquals(TakeWrite.Failed, failing.save("First", capture("Replacement")))
            assertEquals(listOf("First"), good.list().map { it.name })
            assertEquals("First", good.load("First")?.name)
            assertEquals(TakeWrite.Saved("First 2"), good.save("First", body))
        }
    }

    @Test
    fun concurrentStoreInstancesAllocateDifferentNamesAndMatchingPayloads() {
        withDirectory { dir ->
            val stores = listOf(TakeStore(dir), TakeStore(dir))
            val executor = Executors.newFixedThreadPool(2)
            try {
                val results =
                    executor.invokeAll(
                        (0 until 12).map { i ->
                            Callable { stores[i % stores.size].save("Take", capture("Take", i.toLong())) }
                        },
                    ).map { it.get(10, TimeUnit.SECONDS) }
                assertTrue(results.all { it is TakeWrite.Saved })
                val names = results.map { (it as TakeWrite.Saved).name }
                assertEquals(12, names.toSet().size)
                val loaded = names.map { stores.first().load(it)!! }
                assertEquals(names, loaded.map { it.name })
                assertEquals((0L until 12L).toSet(), loaded.map { it.trackOffsetMs }.toSet())
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun capture(
        name: String,
        offset: Long = 0L,
    ): String = PerformanceTake.Recorder("fluid", SceneParams(), null).finish(name, "content://song", 500, offset)

    private fun withDirectory(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "take-test-${UUID.randomUUID()}")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
