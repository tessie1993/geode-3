package dev.geode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.geode.ui.theme.ThemePackCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class JellyMatteContrastTest {
    @Test
    fun everyMineralHasReadableMatteTextOverBrightAndDarkScenery() {
        for (theme in ThemePackCatalog.all) {
            for (ink in listOf(theme.palette.onSurface, theme.palette.onBackground)) {
                val sheet = jellyMatteTint(ink, theme.palette.surface)
                for (scenery in listOf(Color.White, Color.Black)) {
                    val backing = sheet.copy(alpha = 0.86f).compositeOver(scenery)
                    assertTrue("${theme.name} text must remain readable", contrast(ink, backing) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun fontOverridesChooseReadableLightOrDarkBacking() {
        for (ink in listOf(Color.Black, Color.White, Color(0xFFFFE08A), Color(0xFF173462))) {
            for (theme in ThemePackCatalog.all) {
                val backing = jellyMatteTint(ink, theme.palette.primary)
                assertTrue("${theme.name} backing must support the chosen ink", contrast(ink, backing) >= 4.5f)
            }
        }
    }

    private fun contrast(ink: Color, paper: Color): Float {
        val a = ink.luminance()
        val b = paper.luminance()
        return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
    }
}
