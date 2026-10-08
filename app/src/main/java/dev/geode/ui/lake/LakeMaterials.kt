package dev.geode.ui.lake

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.geode.ui.LocalFontColor
import dev.geode.ui.jellyMatteTint
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StonePalette
import dev.geode.ui.theme.StoneState

/** Shared local reading surfaces; volume, water and navigation lenses belong to the 3D world. */
object LakeMaterials {
    val MinimumTouchTarget = 48.dp
    val ControlSize = 56.dp
    val ButtonCorner = 20.dp
    val PanelCorner = 24.dp
    val SheetCorner = 28.dp
    val SearchCorner = 20.dp
    val ArtworkCorner = 14.dp
    val PanelElevation = 6.dp
    val ControlElevation = 3.dp
    val Hairline = 0.8.dp
    val SeekThumbRadius = 12.dp
    const val READING_OPACITY = 0.84f
    const val MINIMUM_READING_OPACITY = 0.80f

    val Ink = Color(0xFF163C40)
    val MutedInk = Color(0xFF476567)
    val Frost = Color(0xFFF2FAF6)
    val Water = Color(0xFFDDEEE8)
    val Teal = Color(0xFF246A66)
    val Dawn = Color(0xFFE8D8BC)

    val palette =
        StonePalette(
            background = Frost,
            backgroundDeep = Color(0xFFC8E0DC),
            surface = Frost,
            surfaceHigh = Water,
            primary = Teal,
            secondary = Color(0xFF5C6750),
            accent = Color(0xFF27625D),
            glow = Color(0xFF90D8CE),
            onBackground = Ink,
            onSurface = Ink,
            muted = MutedInk,
            outline = Color(0xFF749C97),
            danger = Color(0xFF9C4239),
        )
}

/** Native seek marker uses the same pale material as controls, with no baked optical sprite. */
fun DrawScope.drawLakeSeekThumb(
    position: Offset,
    pressed: Boolean,
    accent: Color = LakeMaterials.Teal,
) {
    val radius = LakeMaterials.SeekThumbRadius.toPx()
    drawCircle(
        brush =
            Brush.radialGradient(
                listOf(Color.White, LakeMaterials.Frost, if (pressed) LakeMaterials.Water else LakeMaterials.Frost),
                center = position - Offset(radius * 0.35f, radius * 0.35f),
                radius = radius * 1.7f,
            ),
        radius = radius,
        center = position,
    )
    drawCircle(accent.copy(alpha = if (pressed) 0.75f else 0.4f), radius, position, style = Stroke(LakeMaterials.Hairline.toPx()))
    drawCircle(accent, radius = 2.5.dp.toPx(), center = position)
}

/** A pale, local optical veil leaves the lake visible around native sharp text. */
fun Modifier.lakeFrostedPanel(
    tint: Color = LakeMaterials.Frost,
    opacity: Float = LakeMaterials.READING_OPACITY,
    corner: Dp = LakeMaterials.PanelCorner,
    rim: Color = LakeMaterials.Water,
    readable: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(corner)
    val alpha = if (readable) opacity.coerceIn(LakeMaterials.MINIMUM_READING_OPACITY, 0.96f) else opacity.coerceIn(0f, 1f)
    return shadow(
        elevation = LakeMaterials.PanelElevation,
        shape = shape,
        clip = false,
        ambientColor = LakeMaterials.Teal.copy(alpha = 0.08f),
        spotColor = LakeMaterials.Teal.copy(alpha = 0.12f),
    ).clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    lerp(tint, Color.White, 0.3f).copy(alpha = alpha),
                    tint.copy(alpha = alpha),
                    lerp(tint, LakeMaterials.Water, 0.12f).copy(alpha = alpha),
                ),
            ),
        ).drawWithCache {
            val inset = 1.5.dp.toPx()
            val innerSize = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f))
            val innerEdge =
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.Transparent, rim.copy(alpha = 0.12f)),
                )
            onDrawBehind {
                drawRoundRect(
                    innerEdge,
                    topLeft = Offset(inset, inset),
                    size = innerSize,
                    cornerRadius = CornerRadius((corner.toPx() - inset).coerceAtLeast(0f)),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }.border(
            LakeMaterials.Hairline,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.82f), rim.copy(alpha = 0.34f), rim.copy(alpha = 0.58f)),
            ),
            shape,
        )
}

/** Album art stays undistorted; a shallow contact shadow separates its real cover from frost. */
fun Modifier.lakeArtworkFrame(corner: Dp = LakeMaterials.ArtworkCorner): Modifier {
    val shape = RoundedCornerShape(corner)
    return shadow(
        LakeMaterials.ControlElevation,
        shape,
        clip = false,
        ambientColor = LakeMaterials.Teal.copy(alpha = 0.08f),
        spotColor = LakeMaterials.Teal.copy(alpha = 0.14f),
    ).clip(shape).border(LakeMaterials.Hairline, Color.White.copy(alpha = 0.65f), shape)
}

/** State light is event-driven. No bitmap ring, private frame loop or recomposition clock. */
@Composable
fun LakeSurfaceArt(
    component: StoneComponent,
    state: StoneState,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val foreground = LocalFontColor.current ?: colors.onSurface
    val quiet = jellyMatteTint(foreground, colors.surface)
    val disabled = state == StoneState.DISABLED
    val selected = state == StoneState.SELECTED
    val focused = state == StoneState.FOCUSED
    val pressed = state == StoneState.PRESSED
    val motion = LocalThemePack.current.motion
    val stateDuration =
        when {
            pressed -> motion.pressDurationMs
            focused -> motion.focusDurationMs
            selected -> motion.selectedDurationMs
            else -> motion.releaseDurationMs
        }.coerceAtLeast(0)
    val fill by animateColorAsState(
        targetValue =
            when {
                pressed -> lerp(quiet, colors.primaryContainer, 0.28f)
                selected -> lerp(quiet, colors.primaryContainer, 0.4f)
                else -> quiet
            },
        animationSpec = tween(if (reducedMotion || LocalReducedMotion.current) 0 else stateDuration),
        label = "lake-material-state",
    )
    val round =
        when (component) {
            StoneComponent.ICON_BUTTON,
            StoneComponent.KNOB,
            StoneComponent.SLIDER_THUMB,
            StoneComponent.PROGRESS_RING,
            -> true
            else -> false
        }
    val corner =
        when {
            round -> 100.dp
            component == StoneComponent.SLIDER_TRACK -> 6.dp
            component == StoneComponent.BOTTOM_SHEET || component == StoneComponent.DIALOG -> LakeMaterials.SheetCorner
            component == StoneComponent.CHIP || component == StoneComponent.COMPACT_BUTTON -> 16.dp
            else -> LakeMaterials.ButtonCorner
        }
    val shape = RoundedCornerShape(corner)
    val alpha =
        when {
            disabled -> LakeMaterials.MINIMUM_READING_OPACITY
            component == StoneComponent.SECONDARY_BUTTON -> 0.80f
            component == StoneComponent.SLIDER_TRACK -> 0.6f
            else -> 0.9f
        }
    val rim = if (selected || focused) colors.primary.copy(alpha = 0.7f) else colors.outline.copy(alpha = 0.32f)
    Box(
        modifier
            .shadow(
                elevation = if (component == StoneComponent.SLIDER_TRACK || disabled) 0.dp else LakeMaterials.ControlElevation,
                shape = shape,
                clip = false,
                ambientColor = colors.primary.copy(alpha = 0.06f),
                spotColor = colors.primary.copy(alpha = 0.1f),
            ).clip(shape)
            .background(fill.copy(alpha = alpha))
            .drawWithCache {
                val radius = CornerRadius(if (round) size.minDimension / 2f else corner.toPx())
                val reflection =
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = if (disabled) 0.12f else 0.28f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.45f, size.height),
                    )
                onDrawBehind { drawRoundRect(reflection, cornerRadius = radius) }
            }.border(if (focused) 1.5.dp else LakeMaterials.Hairline, rim, shape),
    ) {
        if (selected || focused) {
            Canvas(Modifier.matchParentSize()) {
                val light = colors.primary.copy(alpha = if (focused) 0.16f else 0.1f)
                drawRoundRect(
                    light,
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size =
                        Size(
                            (size.width - 6.dp.toPx()).coerceAtLeast(0f),
                            (size.height - 6.dp.toPx()).coerceAtLeast(0f),
                        ),
                    cornerRadius = CornerRadius(if (round) size.minDimension / 2f else (corner - 3.dp).toPx()),
                    style = Stroke(LakeMaterials.Hairline.toPx()),
                )
            }
        }
    }
}
