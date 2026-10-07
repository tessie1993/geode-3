package dev.geode.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.geode.R
import dev.geode.ui.theme.LocalMaterialResumed
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val StillWaterTime: State<Float> = mutableFloatStateOf(0f)

/** One scene clock is provided by the shell; glass rows never create their own frame loops. */
internal val LocalTidalSceneTime = staticCompositionLocalOf<State<Float>?> { null }

/** Observe the system setting once at the theme root, including changes while open. */
@Composable
internal fun rememberSystemMotionDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val disabled = remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    disabled.value = !ValueAnimator.areAnimatorsEnabled()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled.value
}

@Composable
internal fun rememberMaterialResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed =
        remember(lifecycle) {
            mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, _ ->
                resumed.value = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed.value
}

@Composable
internal fun rememberTidalMotionRunning(reducedMotion: Boolean): Boolean =
    LocalMaterialResumed.current && !reducedMotion && !LocalReducedMotion.current

/** Read this shared clock from draw/layer lambdas; the loop never recomposes a screen. */
@Composable
internal fun rememberTidalSceneTime(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) {
            return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                // 30 Hz is enough for ambient water, leaving the GL scene its frame budget.
                val elapsed = now - previous
                if (elapsed >= 33_333_333L) {
                    time.floatValue += min(elapsed / 1_000_000_000f, 0.05f)
                    previous = now
                }
            }
        }
    }
    return time
}

@Composable
internal fun rememberSpatialWaterTime(running: Boolean): State<Float> =
    if (running) LocalTidalSceneTime.current ?: StillWaterTime else StillWaterTime

@Composable
internal fun TidalForestBackground(
    modifier: Modifier,
    reducedMotion: Boolean,
) {
    SpatialWaterBackground(modifier, reducedMotion)
}

/** A bounded atmospheric layer for the mineral palettes, drawn behind all readable content. */
@Composable
internal fun JellyAmbientParticles(
    modifier: Modifier,
    reducedMotion: Boolean,
) {
    val running = rememberTidalMotionRunning(reducedMotion)
    val time = rememberSpatialWaterTime(running)
    val pack = LocalThemePack.current
    if (running) {
        Canvas(modifier) {
            drawSpatialMotes(time.value, pack.palette.glow, if (pack.isLight) 0.45f else 0.85f)
        }
    }
}

/** A quiet text core is enclosed by the new material plate and sculpted edge refraction. */
internal fun Modifier.tidalPanel(
    capsule: ImageBitmap,
    opacity: Float,
    tint: Color,
    glow: Color,
    corner: Dp,
    glowStrength: Float,
    facets: Float,
    prismatic: Boolean,
    sheen: Color,
): Modifier {
    val shape = RoundedCornerShape(corner)
    return shadow(
        20.dp,
        shape,
        clip = false,
        ambientColor = Color.Black,
        spotColor = Color(0xFF001214),
    ).clip(shape)
        .drawWithCache {
            val relief = facets.coerceIn(0f, 1.5f)
            val light = glowStrength.coerceIn(0f, 1.5f)
            val edge =
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.66f * light),
                        sheen.copy(alpha = 0.40f * light),
                        Color.Black.copy(alpha = 0.62f),
                        glow.copy(alpha = 0.48f * light),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            val interior =
                Brush.linearGradient(
                    0f to tint.copy(alpha = opacity.coerceIn(0f, 1f)),
                    0.40f to tint.copy(alpha = opacity.coerceIn(0f, 1f) * 0.96f),
                    1f to tint.copy(alpha = opacity.coerceIn(0f, 1f)),
                )
            val reflection =
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.07f * relief),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.025f * relief),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.65f, size.height),
                )
            val refraction =
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f * light),
                    0.40f to Color.Transparent,
                    0.72f to Color.Black.copy(alpha = 0.05f),
                    1f to Color.Black.copy(alpha = 0.38f),
                )
            onDrawBehind {
                drawRoundRect(interior, cornerRadius = CornerRadius(corner.toPx()))
                drawFittedArt(capsule, 0.055f * relief)
                drawRoundRect(reflection, cornerRadius = CornerRadius(corner.toPx()))
                drawRoundRect(
                    refraction,
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size =
                        Size(
                            (size.width - 6.dp.toPx()).coerceAtLeast(0f),
                            (size.height - 6.dp.toPx()).coerceAtLeast(0f),
                        ),
                    cornerRadius = CornerRadius((corner.toPx() - 3.dp.toPx()).coerceAtLeast(0f)),
                    style = Stroke(6.dp.toPx()),
                )
                drawRoundRect(
                    edge,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(2.2.dp.toPx()),
                )
                drawRoundRect(
                    sheen.copy(alpha = 0.28f * light),
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size =
                        Size(
                            (size.width - 8.dp.toPx()).coerceAtLeast(0f),
                            (size.height - 8.dp.toPx()).coerceAtLeast(0f),
                        ),
                    cornerRadius = CornerRadius((corner.toPx() - 4.dp.toPx()).coerceAtLeast(0f)),
                    style = Stroke(0.7.dp.toPx()),
                )
                if (prismatic) {
                    drawLine(
                        sheen.copy(alpha = 0.25f * light),
                        Offset(size.width * 0.10f, 1.dp.toPx()),
                        Offset(size.width * 0.70f, 1.dp.toPx()),
                        1.dp.toPx(),
                    )
                }
            }
        }
}

private fun DrawScope.drawFittedArt(
    image: ImageBitmap,
    alpha: Float,
) {
    val scale = min(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    drawImage(
        image,
        dstOffset =
            IntOffset(
                ((size.width - width) / 2f).roundToInt(),
                ((size.height - height) / 2f).roundToInt(),
            ),
        dstSize = IntSize(width, height),
        alpha = alpha,
    )
}

@Composable
internal fun TidalSurfaceArt(
    component: StoneComponent,
    state: StoneState,
    modifier: Modifier,
    reducedMotion: Boolean = false,
) {
    val pack = LocalThemePack.current
    val palette = pack.palette
    val running = rememberTidalMotionRunning(reducedMotion)
    val time = rememberSpatialWaterTime(running)
    val round =
        when (component) {
            StoneComponent.ICON_BUTTON,
            StoneComponent.KNOB,
            StoneComponent.SLIDER_THUMB,
            StoneComponent.PROGRESS_RING,
            -> true
            else -> false
        }
    val resource = if (round) R.drawable.spatial_glass_pebble else R.drawable.spatial_glass_capsule
    val art = rememberTidalBitmap(resource)
    val glassTint = rememberSpatialGlassTint()
    val disabled = state == StoneState.DISABLED
    val intensity =
        when (state) {
            StoneState.PRESSED -> 0.60f
            StoneState.SELECTED -> 1.0f
            StoneState.FOCUSED -> 0.85f
            else -> 0.30f
        }
    val shape = RoundedCornerShape(if (round) 100.dp else 22.dp)
    Box(modifier) {
        if (!round) {
            Box(
                Modifier
                    .matchParentSize()
                    .tidalPanel(
                        art,
                        if (disabled) 0.58f else 0.80f,
                        palette.surface,
                        palette.glow,
                        22.dp,
                        if (disabled) 0.25f else intensity,
                        0.7f,
                        false,
                        palette.onSurface,
                    ),
            )
        }
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = glassTint,
            alpha =
                when {
                    disabled -> if (round) 0.42f else 0.12f
                    state == StoneState.PRESSED -> if (round) 0.82f else 0.20f
                    else -> if (round) 1f else 0.28f
                },
            modifier = Modifier.matchParentSize(),
        )
        Canvas(Modifier.matchParentSize().clip(shape)) {
            if (!disabled && running) {
                drawGlassEdgeSheen(time.value, palette.glow, intensity, round)
            }
            if (!disabled && state != StoneState.DEFAULT) {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(palette.glow.copy(alpha = intensity * 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.3f, 0f),
                        radius = size.width.coerceAtLeast(1f),
                    ),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                )
                if (state == StoneState.FOCUSED || state == StoneState.SELECTED) {
                    drawRoundRect(
                        palette.glow.copy(alpha = intensity * 0.65f),
                        cornerRadius =
                            CornerRadius(if (round) size.minDimension / 2f else 22.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGlassEdgeSheen(
    time: Float,
    light: Color,
    intensity: Float,
    round: Boolean,
) {
    val inset = 3.dp.toPx()
    if (round) {
        drawArc(
            light.copy(alpha = 0.26f + intensity * 0.20f),
            startAngle = 205f + sin(time * 0.30f) * 35f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size =
                Size(
                    (size.width - inset * 2f).coerceAtLeast(0f),
                    (size.height - inset * 2f).coerceAtLeast(0f),
                ),
            style = Stroke(1.7.dp.toPx()),
        )
    } else {
        val phase = 0.35f + sin(time * 0.25f) * 0.22f
        val start = Offset(size.width * phase, inset)
        val end = Offset(size.width * (phase + 0.22f), inset)
        drawLine(light.copy(alpha = 0.10f), start, end, 4.dp.toPx())
        drawLine(light.copy(alpha = 0.24f + intensity * 0.15f), start, end, 1.dp.toPx())
    }
}

@Composable
internal fun Modifier.tidalPressRipple(
    interaction: InteractionSource,
    reducedMotion: Boolean,
): Modifier {
    val running = rememberTidalMotionRunning(reducedMotion)
    val light = LocalThemePack.current.palette.glow
    val ripple = remember { Animatable(1f) }
    val position = remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interaction, running) {
        if (!running) {
            ripple.snapTo(1f)
            return@LaunchedEffect
        }
        var rippleJob: Job? = null
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Press) {
                position.value = event.pressPosition
                rippleJob?.cancel()
                rippleJob = launch {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(850))
                }
            }
        }
    }
    return drawWithContent {
        drawContent()
        val progress = ripple.value
        if (running && progress < 1f) {
            val origin = position.value.takeIf { it != Offset.Unspecified } ?: center
            clipRect {
                drawLiquidPress(progress, origin, light)
            }
        }
    }
}

private fun DrawScope.drawLiquidPress(
    progress: Float,
    origin: Offset,
    light: Color,
) {
    val fade = 1f - progress
    val radius = size.maxDimension * (0.04f + progress * 0.90f)
    drawCircle(light.copy(alpha = fade * 0.035f), radius, origin)
    repeat(3) { index ->
        drawCircle(
            light.copy(alpha = fade * (0.38f - index * 0.10f)),
            radius * (1f - index * 0.18f),
            origin,
            style = Stroke((1.6f - progress * 0.7f - index * 0.25f).dp.toPx()),
        )
    }
    repeat(8) { index ->
        val angle = index * PI.toFloat() / 4f + 0.22f
        val travel = radius * (0.68f + index % 3 * 0.10f)
        val point = origin + Offset(cos(angle) * travel, sin(angle) * travel - progress * 7.dp.toPx())
        drawSpatialLightMote(point, (1.1f + index % 2 * 0.45f).dp.toPx() * fade, light, fade * 0.60f)
    }
}

/** Decorative layered volume only: no analyzer, player or native GL ownership. */
@Composable
fun TidalWaterOrb(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    energy: Float = 0f,
    artwork: @Composable () -> Unit = {},
) {
    SpatialGlassVolume(modifier, reducedMotion, energy = { energy }, artwork = artwork)
}

/** The signal supplier is read in drawing; the real artwork lives between the glass plates. */
@Composable
fun TidalWaterOrb(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    energy: () -> Float,
    artwork: @Composable () -> Unit = {},
) {
    SpatialGlassVolume(modifier, reducedMotion, energy, artwork)
}
