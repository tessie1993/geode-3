package dev.geode.ui

import dev.geode.data.Preset
import dev.geode.render.scene.SceneIds
import kotlin.random.Random

/** The label is presentation metadata; it must not give an entry a second turn. */
internal data class VisualJourneyKey(
    val sceneId: String,
    val presetName: String? = null,
    val milkPath: String? = null,
)

internal fun VizPlaylistEntry.journeyKey(): VisualJourneyKey = VisualJourneyKey(sceneId, presetName, milkPath)

/** Uses the native registry published by the active renderer, including newly added families. */
internal fun visualJourneyChoices(
    sceneIds: List<String>,
    presets: List<Preset>,
    milkFiles: List<MilkFile>,
    includeStyles: Boolean,
    includePresets: Boolean,
    includeMilk: Boolean,
): List<VizPlaylistEntry> {
    val available = sceneIds.toSet()
    return buildList {
        if (includeStyles) {
            sceneIds.filter { it != SceneIds.MILKDROP }.forEach { add(VizPlaylistEntry(it, label = it)) }
        }
        if (includePresets) {
            presets.filter { it.sceneId in available }.forEach {
                add(VizPlaylistEntry(it.sceneId, presetName = it.name, label = it.name))
            }
        }
        if (includeMilk && SceneIds.MILKDROP in available) {
            milkFiles.forEach { add(VizPlaylistEntry(SceneIds.MILKDROP, milkPath = it.path, label = it.name)) }
        }
    }.distinctBy { it.journeyKey() }
}

/** A live shuffle bag. Pool edits retain the unplayed entries and admit newly available ones. */
internal class VisualJourneyShuffle(
    private val random: Random = Random.Default,
) {
    private val visited = mutableSetOf<VisualJourneyKey>()
    private val remaining = mutableListOf<VisualJourneyKey>()
    private var members = emptySet<VisualJourneyKey>()

    fun next(
        entries: List<VizPlaylistEntry>,
        current: VisualJourneyKey?,
    ): VizPlaylistEntry? {
        val entriesByKey = entries.associateBy { it.journeyKey() }
        val keys = entriesByKey.keys
        if (keys != members) {
            remaining.retainAll(keys)
            visited.retainAll(keys)
            remaining.addAll(keys.filter { it !in visited && it !in remaining })
            remaining.shuffle(random)
            members = keys.toSet()
        }
        if (keys.isEmpty()) return null
        if (remaining.isEmpty()) refill(keys)
        if (keys.size > 1 && remaining.all { it == current }) {
            // A manual selection already displayed the only remaining entry.
            refill(keys)
        }
        val index = remaining.indexOfLast { keys.size == 1 || it != current }
        if (index < 0) return null
        val picked = remaining.removeAt(index)
        visited += picked
        return entriesByKey[picked]
    }

    private fun refill(keys: Set<VisualJourneyKey>) {
        visited.clear()
        remaining.clear()
        remaining.addAll(keys)
        remaining.shuffle(random)
    }
}
