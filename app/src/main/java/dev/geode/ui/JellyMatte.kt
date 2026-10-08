package dev.geode.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.geode.ui.lake.LakeMaterials
import dev.geode.ui.lake.lakeFrostedPanel
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.isLivingLake
import kotlin.math.max
import kotlin.math.min

/** Contrast follows the chosen native ink; the default lake always reads on pale mineral frost. */
internal fun jellyMatteTint(
    foreground: Color,
    tint: Color,
): Color {
    val dark = lerp(Color(0xFF05080D), tint.copy(alpha = 1f), 0.045f)
    val light = lerp(LakeMaterials.Frost, tint.copy(alpha = 1f), 0.045f)
    val ink = foreground.copy(alpha = 1f).luminance()

    fun contrast(backing: Color): Float {
        val paper = backing.luminance()
        return (max(ink, paper) + 0.05f) / (min(ink, paper) + 0.05f)
    }

    return if (contrast(dark) >= contrast(light)) dark else light
}

/** Preserves the text's measured size, semantics and gestures while shielding it from scenery. */
@Composable
internal fun Modifier.jellyMatteSheet(
    corner: Dp = 16.dp,
    opacity: Float = LakeMaterials.READING_OPACITY,
): Modifier {
    if (!LocalThemePack.current.isLivingLake) return this
    val colors = MaterialTheme.colorScheme
    val backing = jellyMatteTint(LocalFontColor.current ?: colors.onSurface, colors.surfaceVariant)
    return lakeFrostedPanel(tint = backing, opacity = opacity, corner = corner, rim = colors.outlineVariant)
}
