package dev.geode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.isJellyGlass
import kotlin.math.max
import kotlin.math.min

/** A quiet neutral under text; the coloured glass and moving highlights stay around its rim. */
internal fun jellyMatteTint(
    foreground: Color,
    tint: Color,
): Color {
    val dark = lerp(Color(0xFF05080D), tint.copy(alpha = 1f), 0.045f)
    val light = lerp(Color(0xFFF9FBFE), tint.copy(alpha = 1f), 0.045f)
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
    opacity: Float = 0.88f,
): Modifier {
    if (!LocalThemePack.current.isJellyGlass) return this
    val colors = MaterialTheme.colorScheme
    val backing = jellyMatteTint(LocalFontColor.current ?: colors.onSurface, colors.surfaceVariant)
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(backing.copy(alpha = opacity.coerceIn(0.86f, 0.98f)))
        .border(0.75.dp, colors.outlineVariant.copy(alpha = 0.24f), shape)
}
