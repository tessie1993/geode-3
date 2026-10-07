package dev.geode.ui

import dev.geode.analysis.AudioFeatures
import dev.geode.data.PaletteStore
import dev.geode.data.Preset
import dev.geode.render.LiveSignal
import dev.geode.render.scene.SceneParams
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** A switch waits for a hit this strong — the live transient, not a tracked beat. */
private const val STRONG_MOMENT_IMPULSE = 0.6f

internal class AutoVisualsController(
    private val prefsStore: AutoVisualsPrefsStore,
    private val host: Host,
    private val clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val sceneIds: () -> List<String> = { LayersBus.availableScenes.value },
    private val randomRng: Random = Random.Default,
) {
    interface Host {
        val vizState: StateFlow<VizUiState>

        fun updateViz(transform: (VizUiState) -> VizUiState)

        val isPlaying: Boolean
        val positionMs: Long

        fun features(): AudioFeatures

        val presetLocked: Boolean
        val currentJourney: VisualJourneyKey

        fun selectScene(sceneId: String)

        fun applyPreset(preset: Preset)

        fun applyMilk(
            path: String,
            sceneId: String,
        )

        fun analyzeCurrentTrack()

        fun milkFilesAsync(onDone: (List<MilkFile>) -> Unit)
    }

    private var lastVizSwitchMs = 0L
    private var vizPlaylistIndex = 0
    private var lastRandomSwitchMs = 0L
    private val shuffle = VisualJourneyShuffle(randomRng)
    private var heldSinceMs: Long? = null

    private var cachedMilkFiles: List<MilkFile> = emptyList()
    private var milkCacheLoaded = false
    private var milkCacheLoading = false

    fun addToVizPlaylist(entry: VizPlaylistEntry) {
        val s = host.vizState.value
        val duplicate =
            s.vizPlaylist.any {
                it == entry || (entry.presetName != null && it.presetName == entry.presetName)
            }
        if (duplicate) return
        host.updateViz { it.copy(vizPlaylist = it.vizPlaylist + entry) }
        persistAutoVisuals()
    }

    fun removeVizPlaylistAt(index: Int) {
        val s = host.vizState.value
        if (index in s.vizPlaylist.indices) {
            host.updateViz { it.copy(vizPlaylist = it.vizPlaylist.filterIndexed { i, _ -> i != index }) }
            persistAutoVisuals()
        }
    }

    fun setVizPlaylistEnabled(enabled: Boolean) {
        host.updateViz {
            it.copy(
                vizPlaylistEnabled = enabled,
                randomEnabled = if (enabled) false else it.randomEnabled,
            )
        }
        lastVizSwitchMs = clockMs()
        persistAutoVisuals()
    }

    fun setVizPlaylistIntelligent(enabled: Boolean) {
        host.updateViz { it.copy(vizPlaylistIntelligent = enabled) }
        persistAutoVisuals()
    }

    fun setVizPlaylistInterval(seconds: Int) {
        host.updateViz { it.copy(vizPlaylistIntervalSec = seconds.coerceIn(AutoVisualsPrefsStore.INTERVAL_SEC)) }
        persistAutoVisuals()
    }

    fun advanceVizPlaylist() {
        if (syncHold()) return
        val s = host.vizState.value
        if (!s.vizPlaylistEnabled || s.vizPlaylist.size < 2 || !host.isPlaying) return
        val now = clockMs()
        val elapsed = now - lastVizSwitchMs
        val intervalMs = s.vizPlaylistIntervalSec * 1000L
        val due =
            if (s.vizPlaylistIntelligent) {
                val f = host.features()
                val minDwell = maxOf(8_000L, intervalMs / 2)
                (elapsed >= minDwell && LiveSignal.hit(f) >= STRONG_MOMENT_IMPULSE) || elapsed >= intervalMs * 2
            } else {
                elapsed >= intervalMs
            }
        if (!due) return
        lastVizSwitchMs = now
        vizPlaylistIndex = (vizPlaylistIndex + 1) % s.vizPlaylist.size
        applyVizEntry(s.vizPlaylist[vizPlaylistIndex])
    }

    fun setRandomEnabled(enabled: Boolean) {
        host.updateViz {
            it.copy(
                randomEnabled = enabled,
                vizPlaylistEnabled = if (enabled) false else it.vizPlaylistEnabled,
            )
        }
        lastRandomSwitchMs = clockMs()
        if (enabled && host.vizState.value.randomIncludeMilk) refreshMilkCache()
        if (enabled) randomStepNow()
        persistAutoVisuals()
    }

    fun setRandomInterval(seconds: Int) {
        host.updateViz { it.copy(randomIntervalSec = seconds.coerceIn(AutoVisualsPrefsStore.INTERVAL_SEC)) }
        persistAutoVisuals()
    }

    fun setRandomOnBeat(enabled: Boolean) {
        host.updateViz { it.copy(randomOnBeat = enabled) }
        persistAutoVisuals()
    }

    fun setRandomIncludeStyles(enabled: Boolean) {
        host.updateViz { it.copy(randomIncludeStyles = enabled) }
        persistAutoVisuals()
    }

    fun setRandomIncludePresets(enabled: Boolean) {
        host.updateViz { it.copy(randomIncludePresets = enabled) }
        persistAutoVisuals()
    }

    fun setRandomIncludeMilk(enabled: Boolean) {
        host.updateViz { it.copy(randomIncludeMilk = enabled) }
        if (enabled) refreshMilkCache()
        persistAutoVisuals()
    }

    fun setRandomizeColors(enabled: Boolean) {
        host.updateViz { it.copy(randomizeColors = enabled) }
        persistAutoVisuals()
    }

    /** Hold pauses dwell and keeps the playlist cursor; released section staging waits for a new section. */
    private fun syncHold(): Boolean {
        val now = clockMs()
        if (host.presetLocked) {
            if (heldSinceMs == null) heldSinceMs = now
            return true
        }
        heldSinceMs?.let { heldAt ->
            lastVizSwitchMs += (now - maxOf(heldAt, lastVizSwitchMs)).coerceAtLeast(0L)
            lastRandomSwitchMs += (now - maxOf(heldAt, lastRandomSwitchMs)).coerceAtLeast(0L)
            lastStagedSection = currentSectionIndex()
            heldSinceMs = null
        }
        return false
    }

    private fun persistAutoVisuals() {
        prefsStore.save(host.vizState.value)
    }

    private fun refreshMilkCache() {
        if (milkCacheLoading) return
        milkCacheLoading = true
        host.milkFilesAsync {
            cachedMilkFiles = it
            milkCacheLoaded = true
            milkCacheLoading = false
        }
    }

    private fun currentSectionIndex(): Int {
        val sections = host.vizState.value.sections
        if (sections.isEmpty()) return 0
        val pos = host.positionMs
        var idx = 0
        for (boundary in sections) {
            if (boundary <= pos) idx++ else break
        }
        return idx
    }

    private var lastStagedSection = -1

    fun onTrackChanged() {
        lastStagedSection = -1
    }

    fun advanceSectionStaging() {
        if (syncHold()) return
        val s = host.vizState.value
        if (!s.sectionStaging || !host.isPlaying) return
        val index = currentSectionIndex()
        if (index == lastStagedSection) return
        lastStagedSection = index
        if (s.vizPlaylist.isNotEmpty()) {
            applyVizEntry(s.vizPlaylist[index % s.vizPlaylist.size])
            return
        }
        val pool = s.presets.filter { it.sceneId == s.sceneId }
        if (pool.isNotEmpty()) {
            val preset = pool[index % pool.size]
            applyVizEntry(VizPlaylistEntry(preset.sceneId, presetName = preset.name, label = preset.name))
        }
    }

    fun setSectionStaging(enabled: Boolean) {
        host.updateViz { it.copy(sectionStaging = enabled) }
        lastStagedSection = -1
        if (enabled &&
            host.vizState.value.sections
                .isEmpty()
        ) {
            host.analyzeCurrentTrack()
        }
        persistAutoVisuals()
    }

    fun advanceRandomMode() {
        if (syncHold()) return
        val s = host.vizState.value
        if (!s.randomEnabled || !host.isPlaying) return
        val now = clockMs()
        val elapsed = now - lastRandomSwitchMs
        val intervalMs = s.randomIntervalSec * 1000L
        val due =
            if (s.randomOnBeat) {
                val f = host.features()
                val minDwell = maxOf(6_000L, intervalMs / 2)
                (elapsed >= minDwell && LiveSignal.hit(f) >= STRONG_MOMENT_IMPULSE) || elapsed >= intervalMs * 2
            } else {
                elapsed >= intervalMs
            }
        if (!due) return
        randomStepNow()
    }

    fun randomStepNow() {
        if (syncHold()) return
        val s = host.vizState.value
        if (s.randomIncludeMilk && !milkCacheLoaded) refreshMilkCache()
        val choices =
            visualJourneyChoices(
                sceneIds(),
                s.presets,
                cachedMilkFiles,
                s.randomIncludeStyles,
                s.randomIncludePresets,
                s.randomIncludeMilk,
            )
        val pick = shuffle.next(choices, host.currentJourney) ?: return
        lastRandomSwitchMs = clockMs()
        applyVizEntry(pick)
        if (s.randomizeColors) {
            val palette = randomRng.nextInt(SceneParams.PALETTES.size)
            val palette2 = randomRng.nextInt(SceneParams.PALETTES.size)
            val paletteMix = if (randomRng.nextBoolean()) randomRng.nextFloat() * 0.6f else 0f
            val colorShift = randomRng.nextFloat()
            host.updateViz { cur ->
                val rolled =
                    cur.params.copy(
                        palette = palette,
                        palette2 = palette2,
                        paletteMix = paletteMix,
                        colorShift = colorShift,
                    )
                cur.copy(params = PaletteStore.clear(PaletteStore.clear(rolled), second = true))
            }
        }
    }

    fun applyVizEntry(entry: VizPlaylistEntry) {
        host.selectScene(entry.sceneId)
        if (entry.presetName != null) {
            host.vizState.value.presets
                .firstOrNull { it.name == entry.presetName && it.sceneId == entry.sceneId }
                ?.let { host.applyPreset(it) }
        }
        if (entry.milkPath != null) {
            host.applyMilk(entry.milkPath, entry.sceneId)
        }
    }
}
