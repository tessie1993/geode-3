package dev.geode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.geode.ui.theme.LocalMaterialResumed
import dev.geode.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.collect

/** Existing press events drive perspective without adding a touch or gesture interceptor. */
@Composable
internal fun Modifier.jellySpatialPress(
    interaction: InteractionSource,
    reducedMotion: Boolean,
): Modifier {
    val disabled = reducedMotion || LocalReducedMotion.current || !LocalMaterialResumed.current
    val pressed by interaction.collectIsPressedAsState()
    var point by remember { mutableStateOf(Offset.Unspecified) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(interaction, disabled) {
        if (disabled) {
            point = Offset.Unspecified
            return@LaunchedEffect
        }
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Press) point = event.pressPosition
        }
    }
    val position = normalizedJellyPress(point, measured)
    val engaged = pressed && !disabled
    val spec = if (disabled) tween<Float>(0) else spring(dampingRatio = 0.7f, stiffness = 420f)
    val pitch = animateFloatAsState(if (engaged) -4f - position.y * 4f else 0f, spec, label = "jellyPressPitch")
    val yaw = animateFloatAsState(if (engaged) position.x * 7f else 0f, spec, label = "jellyPressYaw")
    val depth = animateFloatAsState(if (engaged) 1f else 0f, spec, label = "jellyPressDepth")
    return onSizeChanged { measured = it }
        .graphicsLayer {
            rotationX = pitch.value
            rotationY = yaw.value
            translationY = depth.value * 2.dp.toPx()
            scaleX = 1f - depth.value * 0.018f
            scaleY = 1f + depth.value * 0.024f
            cameraDistance = size.maxDimension * 4f
            transformOrigin = TransformOrigin.Center
        }
}

internal fun normalizedJellyPress(point: Offset, size: IntSize): Offset {
    if (point == Offset.Unspecified || size.width <= 0 || size.height <= 0) return Offset.Zero
    return Offset(
        (point.x / size.width * 2f - 1f).coerceIn(-1f, 1f),
        (point.y / size.height * 2f - 1f).coerceIn(-1f, 1f),
    )
}

/** Animate only the current composition, so navigation never duplicates a player or GL view. */
@Composable
internal fun Modifier.jellySceneEntrance(
    destination: Int,
    enabled: Boolean,
): Modifier {
    // Native video/GL destinations do not receive even an identity offscreen layer.
    if (!enabled) return this
    val running = enabled && !LocalReducedMotion.current && LocalMaterialResumed.current
    val settle = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(destination) }
    var direction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(destination, running) {
        val changed = previous != destination
        direction = if (destination >= previous) 1f else -1f
        previous = destination
        if (!running) {
            settle.snapTo(1f)
        } else if (changed) {
            settle.snapTo(0f)
            settle.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val remaining = if (running) 1f - settle.value else 0f
        alpha = 1f - remaining * 0.35f
        translationX = remaining * direction * 18.dp.toPx()
        translationY = remaining * 10.dp.toPx()
        scaleX = 1f - remaining * 0.035f
        scaleY = scaleX
        rotationY = remaining * direction * -5f
        rotationX = remaining * 2f
        cameraDistance = size.maxDimension * 4f
        transformOrigin = TransformOrigin.Center
    }
}
