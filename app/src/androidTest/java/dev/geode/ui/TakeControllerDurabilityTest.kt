package dev.geode.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.geode.data.PerformanceTake
import dev.geode.data.TakeInfo
import dev.geode.data.TakeRepository
import dev.geode.data.TakeWrite
import dev.geode.render.scene.SceneParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TakeControllerDurabilityTest {
    @Test
    fun failedSaveRetainsExactCaptureAndBlocksReplacementUntilRetrySucceeds() {
        val fixture = Fixture()
        try {
            fixture.recordChange()
            fixture.now = 1_000L
            fixture.controller.stopRecording("My control take")
            assertTrue(fixture.controller.state.value.pendingSave)
            assertFalse(fixture.controller.state.value.saving)
            fixture.controller.startRecording()
            assertFalse(fixture.controller.state.value.recording)
            fixture.host.uri = "content://different-song"
            fixture.host.offset = 99_000L
            fixture.now = 90_000L
            fixture.repo.result = TakeWrite.Saved("My control take")
            fixture.controller.retryTakeSave()
            assertEquals(2, fixture.repo.payloads.size)
            assertEquals(fixture.repo.payloads.first(), fixture.repo.payloads.last())
            val stored = PerformanceTake.Timeline(fixture.repo.payloads.last())
            assertEquals("content://original-song", stored.trackUri)
            assertEquals(4_500L, stored.trackOffsetMs)
            assertEquals(1_000L, stored.durationMs)
            assertFalse(fixture.controller.state.value.pendingSave)
            fixture.controller.startRecording()
            assertTrue(fixture.controller.state.value.recording)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun changingTrackStopsCaptureAndKeepsOriginalSourceAndOffset() {
        val fixture = Fixture()
        try {
            fixture.recordChange()
            fixture.now = 800L
            fixture.host.uri = "content://next-song"
            fixture.host.offset = 100L
            fixture.controller.onTrackChanged()
            assertFalse(fixture.controller.state.value.recording)
            val stored = PerformanceTake.Timeline(fixture.repo.payloads.single())
            assertEquals("content://original-song", stored.trackUri)
            assertEquals(4_500L, stored.trackOffsetMs)
            assertEquals(800L, stored.durationMs)
            fixture.repo.result = TakeWrite.Saved("Take")
            fixture.controller.retryTakeSave()
            assertTrue(fixture.controller.state.value.note.orEmpty().contains("audio source changed"))
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        val host = Host()
        var now = 0L
        val controller = TakeController(repo, scope, scope, host, nowMs = { now })

        fun recordChange() {
            controller.startRecording()
            now = 200L
            host.vizState.value = host.vizState.value.copy(params = SceneParams(speed = 2f))
            assertEquals(2, controller.state.value.recordedEvents)
        }

        fun close() = scope.cancel()
    }

    private class Host : TakeController.Host {
        override val vizState = MutableStateFlow(VizUiState())
        override val activeMilkPath: String? = null
        var uri = "content://original-song"
        var offset = 4_500L
        override val trackUri: String get() = uri
        override val trackPositionMs: Long get() = offset

        override fun selectScene(sceneId: String) = Unit

        override fun setSceneParams(params: SceneParams) = Unit

        override fun applyMilk(
            path: String,
            sceneId: String,
        ) = Unit
    }

    private class Repository : TakeRepository {
        var result: TakeWrite = TakeWrite.Failed
        val payloads = mutableListOf<String>()

        override suspend fun list(): List<TakeInfo> = emptyList()

        override suspend fun load(name: String): PerformanceTake.Timeline? = null

        override suspend fun save(
            name: String,
            json: String,
        ): TakeWrite {
            payloads += json
            return result
        }

        override suspend fun delete(name: String) = Unit

        override suspend fun rename(
            from: String,
            to: String,
        ): Boolean = false
    }
}
