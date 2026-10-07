package dev.geode.ui

import dev.geode.export.StudioClip
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StudioExportCoordinatorTest {
    @Test
    fun `reopening library and dismissing result cannot admit a second running export`() =
        runTest {
            val finish = CompletableDeferred<ExportPhase>()
            val coordinator = StudioExportCoordinator(this) { listOf(clip("existing")) }
            var starts = 0
            assertTrue(
                coordinator.start { progress ->
                    starts++
                    progress(0.4f)
                    finish.await()
                },
            )
            runCurrent()
            coordinator.refreshClips()
            coordinator.clearResult()
            runCurrent()
            assertEquals(ExportPhase.Running(0.4f), coordinator.state.value.phase)
            assertEquals(listOf(clip("existing")), coordinator.state.value.clips)
            assertFalse(coordinator.state.value.clipsLoading)
            assertFalse(coordinator.start { error("second export must not run") })
            assertEquals(1, starts)

            val terminal = ExportPhase.Failed("encoder rejected size")
            finish.complete(terminal)
            runCurrent()
            assertEquals(terminal, coordinator.state.value.phase)
            coordinator.refreshClips()
            runCurrent()
            assertEquals(terminal, coordinator.state.value.phase)
            coordinator.clearResult()
            assertEquals(ExportPhase.Idle, coordinator.state.value.phase)
        }

    @Test
    fun `cancel retains ownership until encoder cleanup returns`() =
        runTest {
            val releaseEncoder = CompletableDeferred<Unit>()
            val coordinator = StudioExportCoordinator(this) { emptyList() }
            var cleanupStarted = false
            coordinator.start {
                try {
                    awaitCancellation()
                } finally {
                    cleanupStarted = true
                    withContext(NonCancellable) { releaseEncoder.await() }
                }
            }
            runCurrent()
            coordinator.cancel()
            runCurrent()
            assertTrue(cleanupStarted)
            coordinator.clearResult()
            coordinator.refreshClips()
            runCurrent()
            assertTrue(coordinator.state.value.phase.isRunning)
            assertFalse(coordinator.start { error("old encoder still owns its resources") })

            releaseEncoder.complete(Unit)
            runCurrent()
            assertEquals(ExportPhase.Idle, coordinator.state.value.phase)
            assertTrue(coordinator.start { ExportPhase.Failed("next export") })
            runCurrent()
            assertEquals(ExportPhase.Failed("next export"), coordinator.state.value.phase)
        }

    @Test
    fun `preparation exceptions become terminal errors and permit retry`() =
        runTest {
            val coordinator = StudioExportCoordinator(this) { emptyList() }
            coordinator.start { throw IOException("source permission lost") }
            runCurrent()
            assertEquals(ExportPhase.Failed("source permission lost"), coordinator.state.value.phase)
            assertTrue(coordinator.start { ExportPhase.Failed("retry result") })
            runCurrent()
            assertEquals(ExportPhase.Failed("retry result"), coordinator.state.value.phase)
        }

    @Test
    fun `late encoder callbacks cannot replace terminal or subsequent results`() =
        runTest {
            val coordinator = StudioExportCoordinator(this) { emptyList() }
            var oldProgress: ((Float) -> Unit)? = null
            coordinator.start { progress ->
                oldProgress = progress
                ExportPhase.Failed("first result")
            }
            runCurrent()
            oldProgress?.invoke(0.9f)
            assertEquals(ExportPhase.Failed("first result"), coordinator.state.value.phase)

            val next = CompletableDeferred<ExportPhase>()
            coordinator.start { progress ->
                progress(0.2f)
                next.await()
            }
            runCurrent()
            oldProgress?.invoke(1f)
            assertEquals(ExportPhase.Running(0.2f), coordinator.state.value.phase)
            next.complete(ExportPhase.Failed("second result"))
            runCurrent()
        }

    @Test
    fun `older library query cannot overwrite a newer refresh`() =
        runTest {
            val older = CompletableDeferred<List<StudioClip>>()
            var loads = 0
            val coordinator = StudioExportCoordinator(this) { if (++loads == 1) older.await() else listOf(clip("new")) }
            coordinator.refreshClips()
            runCurrent()
            coordinator.refreshClips()
            runCurrent()
            older.complete(listOf(clip("old")))
            runCurrent()
            assertEquals(listOf(clip("new")), coordinator.state.value.clips)
            assertFalse(coordinator.state.value.clipsLoading)
        }

    @Test
    fun `scope cancellation before dispatch does not strand running state`() =
        runTest {
            val owner = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val coordinator = StudioExportCoordinator(owner) { emptyList() }
            coordinator.start { error("cancelled owner must not start export") }
            owner.cancel()
            runCurrent()
            assertEquals(ExportPhase.Idle, coordinator.state.value.phase)
        }

    @Test
    fun `cancellation after publication still reconciles the saved clip library`() =
        runTest {
            var library = emptyList<StudioClip>()
            val coordinator = StudioExportCoordinator(this) { library }
            coordinator.start {
                // The provider committed, but dispatch back to the UI has not delivered Saved.
                library = listOf(clip("committed"))
                awaitCancellation()
            }
            runCurrent()
            coordinator.cancel()
            runCurrent()
            assertEquals(listOf(clip("committed")), coordinator.state.value.clips)
            assertFalse(coordinator.state.value.clipsLoading)
        }

    private fun clip(name: String) = StudioClip("content://video/$name", name, 1000L, 100L)
}
