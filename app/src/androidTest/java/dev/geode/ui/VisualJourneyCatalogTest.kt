package dev.geode.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.render.bridge.NativeViz
import dev.geode.render.scene.SceneIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualJourneyCatalogTest {
    @Test
    fun everyRegisteredNativeStyleCanJoinTheJourney() {
        val native = NativeViz(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertTrue(native.create())
            val registry = native.sceneIds()
            val styles = visualJourneyChoices(registry, emptyList(), emptyList(), true, false, false)
            assertTrue(registry.containsAll(listOf(SceneIds.ROD_TUNNEL, SceneIds.FLUID, SceneIds.CYMATICS, SceneIds.WATER)))
            assertEquals(registry.filter { it != SceneIds.MILKDROP }.toSet(), styles.map { it.sceneId }.toSet())
            val milk =
                visualJourneyChoices(
                    registry,
                    emptyList(),
                    listOf(MilkFile("Test", "/test.milk")),
                    false,
                    false,
                    true,
                )
            assertEquals(SceneIds.MILKDROP, milk.single().sceneId)
        } finally {
            native.destroy()
        }
    }
}
