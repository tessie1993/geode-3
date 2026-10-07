package dev.geode.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import dev.geode.ui.TidalSurfaceArt
import dev.geode.ui.jellySpatialPress
import dev.geode.ui.tidalPressRipple

@Composable
fun StoneSurfaceArt(
    component: StoneComponent,
    state: StoneState,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val pack = LocalThemePack.current
    if (pack.isJellyGlass) {
        TidalSurfaceArt(component, state, modifier, reducedMotion)
        return
    }
    val art = pack.surface(component)
    val motion = pack.motion

    @Composable
    fun fade(target: StoneState): Float {
        val visible = state == target
        val durationMs =
            when {
                reducedMotion || LocalReducedMotion.current -> 0
                target == StoneState.PRESSED || state == StoneState.PRESSED -> motion.pressDurationMs
                target == StoneState.FOCUSED || state == StoneState.FOCUSED -> motion.focusDurationMs
                else -> motion.selectedDurationMs
            }
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMs),
            label = "stone-state-$target",
        )
        return alpha
    }

    Box(modifier = modifier) {
        Image(
            painter = painterResource(art.default),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        for (s in listOf(StoneState.FOCUSED, StoneState.SELECTED, StoneState.PRESSED, StoneState.DISABLED)) {
            val alpha = fade(s)
            if (alpha > 0.01f) {
                Image(
                    painter = painterResource(art.forState(s)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    alpha = alpha,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Composable
fun Modifier.stonePress(
    interaction: InteractionSource,
    reducedMotion: Boolean = false,
): Modifier {
    val pack = LocalThemePack.current
    val motion = pack.motion
    val motionDisabled =
        reducedMotion ||
            LocalReducedMotion.current ||
            (pack.isJellyGlass && !LocalMaterialResumed.current)
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performStoneHaptic(StoneHapticCue.TAP)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motionDisabled) motion.pressScale else 1f,
        animationSpec =
            if (motionDisabled) {
                tween(0)
            } else if (pressed) {
                tween(motion.pressDurationMs)
            } else {
                spring(dampingRatio = 0.78f, stiffness = 380f)
            },
        label = "stone-press",
    )
    val relief = scale(scale)
    return if (pack.isJellyGlass) {
        relief.jellySpatialPress(interaction, motionDisabled).tidalPressRipple(interaction, motionDisabled)
    } else {
        relief
    }
}

@Composable
fun rememberStoneState(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    selected: Boolean = false,
): StoneState {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    return stoneStateOf(enabled = enabled, pressed = pressed, selected = selected, focused = focused)
}

@Composable
fun rememberStoneInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
