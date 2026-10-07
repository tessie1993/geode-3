package dev.geode.ui

import dev.geode.data.Preset
import dev.geode.render.scene.SceneIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VisualJourneySelectionTest {
    @Test
    fun `native catalog entries join without a family allowlist`() {
        val nativeIds = listOf("fluid_ink", "cymatics_sand", "water", "curlflow", "future_native_style", SceneIds.MILKDROP)
        val choices = choices(nativeIds)
        assertEquals(nativeIds.filter { it != SceneIds.MILKDROP }, choices.map { it.sceneId })
    }

    @Test
    fun `selected source categories bound the pool and unsupported presets are omitted`() {
        val presets = listOf(preset("allowed", "fluid_ink"), preset("missing", "unknown"))
        val choices =
            visualJourneyChoices(
                listOf("fluid_ink", SceneIds.MILKDROP),
                presets,
                listOf(MilkFile("Milk", "/milk/one.milk")),
                includeStyles = false,
                includePresets = true,
                includeMilk = false,
            )
        assertEquals(listOf("allowed"), choices.map { it.presetName })
    }

    @Test
    fun `MilkDrop files require native support and an enabled source`() {
        val files = listOf(MilkFile("Milk", "/milk/one.milk"))
        assertTrue(visualJourneyChoices(emptyList(), emptyList(), files, false, false, true).isEmpty())
        val choices = visualJourneyChoices(listOf(SceneIds.MILKDROP), emptyList(), files, false, false, true)
        assertEquals("/milk/one.milk", choices.single().milkPath)
        assertTrue(visualJourneyChoices(listOf(SceneIds.MILKDROP), emptyList(), files, false, false, false).isEmpty())
    }

    @Test
    fun `empty bag has no choice and a singleton remains usable`() {
        val bag = VisualJourneyShuffle(Random(7))
        assertNull(bag.next(emptyList(), null))
        val only = entry("only")
        repeat(4) { assertEquals(only, bag.next(listOf(only), only.journeyKey())) }
    }

    @Test
    fun `each cycle visits every entry and boundaries never immediately repeat`() {
        val pool = (1..12).map { entry("scene-$it") }
        val bag = VisualJourneyShuffle(Random(7))
        var current: VisualJourneyKey? = null
        repeat(8) {
            val cycle =
                List(pool.size) {
                    val picked = requireNotNull(bag.next(pool, current))
                    assertNotEquals(current, picked.journeyKey())
                    current = picked.journeyKey()
                    picked.journeyKey()
                }
            assertEquals(pool.map { it.journeyKey() }.toSet(), cycle.toSet())
        }
    }

    @Test
    fun `two entries always alternate without probabilistic rerolls`() {
        val pool = listOf(entry("a"), entry("b"))
        val bag = VisualJourneyShuffle(Random(13))
        var current = pool.first().journeyKey()
        repeat(20) {
            val next = requireNotNull(bag.next(pool, current)).journeyKey()
            assertNotEquals(current, next)
            current = next
        }
    }

    @Test
    fun `pool changes remove unavailable entries and admit new ones`() {
        val pool = listOf(entry("a"), entry("b"), entry("c"))
        val bag = VisualJourneyShuffle(Random(19))
        val first = requireNotNull(bag.next(pool, null))
        val updated = pool.filterNot { it == first } + entry("new")
        var current = first.journeyKey()
        val remaining =
            List(updated.size) {
                val next = requireNotNull(bag.next(updated, current))
                current = next.journeyKey()
                next
            }
        assertEquals(updated.toSet(), remaining.toSet())
    }

    @Test
    fun `label edits do not create duplicate identities`() {
        val first = entry("a")
        val alias = first.copy(label = "Renamed")
        val bag = VisualJourneyShuffle(Random(3))
        val pool = listOf(first, alias, entry("b"))
        val picked = requireNotNull(bag.next(pool, null))
        val second = requireNotNull(bag.next(pool, picked.journeyKey()))
        assertNotEquals(picked.journeyKey(), second.journeyKey())
    }

    @Test
    fun `presets sharing one renderer remain separate looks`() {
        val choices =
            visualJourneyChoices(
                listOf("rod_tunnel"),
                listOf(preset("Night", "rod_tunnel"), preset("Aurora", "rod_tunnel")),
                emptyList(),
                includeStyles = false,
                includePresets = true,
                includeMilk = false,
            )
        val bag = VisualJourneyShuffle(Random(2))
        val first = requireNotNull(bag.next(choices, null))
        val next = requireNotNull(bag.next(choices, first.journeyKey()))
        assertEquals(first.sceneId, next.sceneId)
        assertNotEquals(first.presetName, next.presetName)
    }

    private fun choices(nativeIds: List<String>): List<VizPlaylistEntry> =
        visualJourneyChoices(nativeIds, emptyList(), emptyList(), true, false, false)

    private fun entry(id: String): VizPlaylistEntry = VizPlaylistEntry(id, label = id)

    private fun preset(
        name: String,
        scene: String,
    ): Preset = Preset(name, scene, 0.6f, 0.12f)
}
