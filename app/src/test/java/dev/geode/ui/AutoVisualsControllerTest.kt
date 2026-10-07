package dev.geode.ui

import android.content.SharedPreferences
import dev.geode.analysis.AudioFeatures
import dev.geode.data.Preset
import dev.geode.render.scene.SceneIds
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.random.Random

class AutoVisualsControllerTest {
    @Test
    fun `Hold blocks all automated paths and preserves playlist cursor and remaining dwell`() {
        val f = Fixture()
        f.host.vizState.value =
            f.host.vizState.value.copy(
                vizPlaylist = entries(),
                vizPlaylistEnabled = true,
                vizPlaylistIntervalSec = 10,
            )
        f.now = 4_000L
        f.host.presetLocked = true
        f.controller.advanceVizPlaylist()
        f.now = 104_000L
        f.controller.advanceVizPlaylist()
        f.controller.advanceRandomMode()
        f.controller.advanceSectionStaging()
        f.controller.randomStepNow()
        assertTrue(f.host.selections.isEmpty())
        assertTrue(f.host.vizState.value.vizPlaylistEnabled)

        f.host.presetLocked = false
        f.controller.advanceVizPlaylist()
        assertTrue(f.host.selections.isEmpty())
        f.now = 109_999L
        f.controller.advanceVizPlaylist()
        assertTrue(f.host.selections.isEmpty())
        f.now = 110_000L
        f.controller.advanceVizPlaylist()
        assertEquals(listOf("b"), f.host.selections)
        f.now = 120_000L
        f.controller.advanceVizPlaylist()
        assertEquals(listOf("b", "c"), f.host.selections)
    }

    @Test
    fun `random dwell does not expire during Hold`() {
        val f = Fixture()
        f.host.vizState.value = f.host.vizState.value.copy(randomEnabled = true, randomOnBeat = false, randomIntervalSec = 10)
        f.now = 10_000L
        f.controller.advanceRandomMode()
        val first = f.host.selections.single()
        f.now = 13_000L
        f.host.presetLocked = true
        f.controller.advanceRandomMode()
        f.now = 90_000L
        f.controller.advanceRandomMode()
        f.host.presetLocked = false
        f.controller.advanceRandomMode()
        assertEquals(listOf(first), f.host.selections)
        f.now = 97_000L
        f.controller.advanceRandomMode()
        assertEquals(2, f.host.selections.size)
        assertTrue(f.host.selections[0] != f.host.selections[1])
    }

    @Test
    fun `section staging skips missed boundaries after Hold and resumes at the next one`() {
        val f = Fixture()
        f.host.vizState.value =
            f.host.vizState.value.copy(
                vizPlaylist = entries(),
                sectionStaging = true,
                sections = listOf(10_000L, 20_000L, 30_000L),
            )
        f.controller.advanceSectionStaging()
        assertEquals(listOf("a"), f.host.selections)
        f.host.presetLocked = true
        f.controller.advanceSectionStaging()
        f.now = 25_000L
        f.host.positionMs = 25_000L
        f.controller.advanceSectionStaging()
        f.host.presetLocked = false
        f.controller.advanceSectionStaging()
        assertEquals(listOf("a"), f.host.selections)
        f.now = 30_000L
        f.host.positionMs = 30_000L
        f.controller.advanceSectionStaging()
        assertEquals(listOf("a", "a"), f.host.selections)
    }

    @Test
    fun `stopped playback does not advance automatic journeys`() {
        val f = Fixture()
        f.host.isPlaying = false
        f.host.vizState.value =
            f.host.vizState.value.copy(
                randomEnabled = true,
                vizPlaylistEnabled = true,
                sectionStaging = true,
                vizPlaylist = entries(),
            )
        f.now = 100_000L
        f.controller.advanceRandomMode()
        f.controller.advanceVizPlaylist()
        f.controller.advanceSectionStaging()
        assertTrue(f.host.selections.isEmpty())
    }

    @Test
    fun `silent playing track uses bounded timed fallback instead of inventing a hit`() {
        val f = Fixture()
        f.host.vizState.value = f.host.vizState.value.copy(randomEnabled = true, randomOnBeat = true, randomIntervalSec = 10)
        f.now = 19_999L
        f.controller.advanceRandomMode()
        assertTrue(f.host.selections.isEmpty())
        f.now = 20_000L
        f.controller.advanceRandomMode()
        assertEquals(1, f.host.selections.size)
    }

    @Test
    fun `catalog becoming available enables the next due choice without a fallback family list`() {
        val f = Fixture()
        f.catalog = emptyList()
        f.controller.randomStepNow()
        assertTrue(f.host.selections.isEmpty())
        f.catalog = listOf("new_native_family")
        f.controller.randomStepNow()
        assertEquals(listOf("new_native_family"), f.host.selections)
    }

    @Test
    fun `restored MilkDrop only selection loads its pool without toggling the option`() {
        val f = Fixture()
        f.catalog = listOf(SceneIds.MILKDROP)
        f.host.milkFiles = listOf(MilkFile("Restored", "/milk/restored.milk"))
        f.host.vizState.value =
            f.host.vizState.value.copy(randomIncludeStyles = false, randomIncludeMilk = true)
        f.controller.randomStepNow()
        assertEquals(listOf(SceneIds.MILKDROP), f.host.selections)
        assertEquals(listOf("/milk/restored.milk"), f.host.milkSelections)
    }

    @Test
    fun `manual MilkDrop selection in the same scene is excluded from the next shuffle`() {
        val f = Fixture()
        f.catalog = listOf(SceneIds.MILKDROP)
        f.host.milkFiles = listOf(MilkFile("First", "/first.milk"), MilkFile("Second", "/second.milk"))
        f.host.vizState.value = f.host.vizState.value.copy(randomIncludeStyles = false, randomIncludeMilk = true)
        f.controller.randomStepNow()
        val automatic = f.host.milkSelections.last()
        val manual = f.host.milkFiles.first { it.path != automatic }.path
        f.host.applyMilk(manual, SceneIds.MILKDROP)
        f.controller.randomStepNow()
        assertEquals(automatic, f.host.milkSelections.last())
    }

    private class Fixture {
        var now = 0L
        var catalog = listOf("a", "b", "c")
        val host = Host()
        private val unusedPreferences =
            Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, _ -> error("Unexpected preference access: ${method.name}") } as SharedPreferences
        val controller =
            AutoVisualsController(
                AutoVisualsPrefsStore(unusedPreferences),
                host,
                clockMs = { now },
                sceneIds = { catalog },
                randomRng = Random(3),
            )
    }

    private class Host : AutoVisualsController.Host {
        override val vizState = MutableStateFlow(VizUiState(sceneId = "a", randomIncludePresets = false))
        override var isPlaying = true
        override var positionMs = 0L
        override var presetLocked = false
        override var currentJourney = VisualJourneyKey("a")
        val selections = mutableListOf<String>()
        var milkFiles = emptyList<MilkFile>()
        val milkSelections = mutableListOf<String>()

        override fun updateViz(transform: (VizUiState) -> VizUiState) {
            vizState.value = transform(vizState.value)
        }

        override fun features(): AudioFeatures = AudioFeatures.empty()

        override fun selectScene(sceneId: String) {
            currentJourney = VisualJourneyKey(sceneId)
            selections += sceneId
            updateViz { it.copy(sceneId = sceneId) }
        }

        override fun applyPreset(preset: Preset) {
            currentJourney = VisualJourneyKey(preset.sceneId, presetName = preset.name)
            updateViz { it.copy(sceneId = preset.sceneId, params = preset.params) }
        }

        override fun applyMilk(
            path: String,
            sceneId: String,
        ) {
            currentJourney = VisualJourneyKey(sceneId, milkPath = path)
            milkSelections += path
        }

        override fun analyzeCurrentTrack() = Unit

        override fun milkFilesAsync(onDone: (List<MilkFile>) -> Unit) = onDone(milkFiles)
    }

    private fun entries(): List<VizPlaylistEntry> = listOf("a", "b", "c").map { VizPlaylistEntry(it, label = it) }
}
