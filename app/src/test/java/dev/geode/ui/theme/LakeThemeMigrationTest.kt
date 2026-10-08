package dev.geode.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LakeThemeMigrationTest {
    @Test
    fun installedBuiltinPreferencesMoveToLivingLake() {
        val storedNames =
            listOf(
                "tidal-glass",
                "lapis-lazuli",
                "sugilite",
                "amethyst",
                "clear-quartz",
                "azurite",
                "firestone",
                "kyanite",
                "malachite",
                "mookaite",
                "onyx",
                "CLEAR_QUARTZ",
                "SUGILITE",
            )
        for (storedName in storedNames) {
            assertSame("Saved theme $storedName must migrate", ThemePackCatalog.livingLake, ThemePackCatalog.bySlug(storedName))
        }
    }

    @Test
    fun chooserOffersTheCurrentWorldAndFallbackRestoresIt() {
        assertEquals(listOf(LakeThemePack.SLUG), ThemePackCatalog.all.map { it.slug })
        assertSame(ThemePackCatalog.livingLake, ThemePackCatalog.bySlug(null))
        assertSame(ThemePackCatalog.livingLake, ThemePackCatalog.bySlug("removed-or-invalid-theme"))
        assertSame(ThemePackCatalog.livingLake, ThemePackCatalog.bySlug(LakeThemePack.SLUG))
        assertTrue(ThemePackCatalog.livingLake.isLight)
        assertTrue(ThemePackCatalog.livingLake.isLivingLake)
        assertTrue(ThemePackCatalog.livingLake.isJellyGlass)
    }

    @Test
    fun authoredPacksRetainTheirSurfaceDrawingPath() {
        val authored = ThemePackCatalog.livingLake.copy(slug = "imported-custom-world", name = "Authored world")
        assertFalse(authored.isLivingLake)
        assertFalse(authored.isJellyGlass)
        for (component in StoneComponent.entries) {
            assertSame(authored.surfaces[component], authored.surface(component))
        }
    }
}
