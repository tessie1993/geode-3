package dev.geode.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.geode.ui.theme.LocalBackgroundDim
import dev.geode.ui.theme.LocalMaterialResumed
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val WaterLight = Color(0xFFCDEDE8)
private val DeepWater = Color(0xFF072A2A)
private val TidalBitmapResources =
    setOf(
        R.drawable.tidal_forest,
        R.drawable.tidal_glass_pebble,
        R.drawable.tidal_glass_capsule,
        R.drawable.tidal_glass_orb,
    )
private val TidalBitmapCache = LruCache<Int, ImageBitmap>(4)

/** These four nodpi assets share immutable bitmaps without retaining an activity. */
@Composable
internal fun rememberTidalBitmap(
    @DrawableRes resource: Int,
): ImageBitmap {
    val resources = LocalContext.current.applicationContext.resources
    return remember(resource) {
        require(resource in TidalBitmapResources) { "Only Tidal UI assets belong in this cache" }
        synchronized(TidalBitmapCache) {
            TidalBitmapCache.get(resource)
                ?: run {
                    val options =
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inScaled = false
                        }
                    val bitmap =
                        checkNotNull(BitmapFactory.decodeResource(resources, resource, options)) {
                            "Unable to decode Tidal UI asset $resource"
                        }.asImageBitmap()
                    TidalBitmapCache.put(resource, bitmap)
                    bitmap
                }
        }
    }
}

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

/** Read the clock from draw/layer lambdas; the frame loop never recomposes a screen. */
@Composable
private fun rememberWaterTime(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
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
internal fun TidalForestBackground(
    modifier: Modifier,
    reducedMotion: Boolean,
) {
    val time = rememberWaterTime(rememberTidalMotionRunning(reducedMotion))
    val dim = LocalBackgroundDim.current.coerceIn(0f, 1f)
    val forest = rememberTidalBitmap(R.drawable.tidal_forest)
    Box(modifier) {
        Image(
            bitmap = forest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0x18071417),
                    0.45f to Color.Transparent,
                    1f to Color(0xAA051619),
                ),
            )
            drawRect(
                Brush.horizontalGradient(
                    0f to Color(0x44041518),
                    0.5f to Color.Transparent,
                    1f to Color(0x39041518),
                ),
            )
            drawCreekLight(time.value)
            if (dim > 0f) {
                drawRect(Color.Black.copy(alpha = dim))
            }
        }
    }
}

private fun DrawScope.drawCreekLight(time: Float) {
    // Keep the moving highlights in the creek plane; the forest and text never flash.
    val water = Offset(size.width * 0.56f, size.height * 0.79f)
    for (i in 0..3) {
        val phase = (time * 0.10f + i * 0.25f) % 1f
        val width = size.width * (0.10f + phase * 0.50f)
        val height = width * 0.17f
        drawOval(
            WaterLight.copy(alpha = (sin(phase * PI).toFloat() * 0.085f).coerceAtLeast(0f)),
            topLeft = water - Offset(width / 2f, height / 2f),
            size = Size(width, height),
            style = Stroke(0.8.dp.toPx()),
        )
    }
    for (i in 0..5) {
        val x = size.width * (0.22f + i * 0.11f)
        val y = size.height * (0.68f + i % 3 * 0.075f)
        val drift = sin(time * 0.42f + i).toFloat() * 5.dp.toPx()
        drawOval(
            Brush.radialGradient(listOf(WaterLight.copy(alpha = 0.055f), Color.Transparent)),
            topLeft = Offset(x + drift, y),
            size = Size(size.width * 0.19f, 7.dp.toPx()),
        )
    }
}

/** Thick glass relief stays procedural, while the original capsule art supplies reflections. */
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
        14.dp,
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
                        WaterLight.copy(alpha = 0.64f * light),
                        sheen.copy(alpha = 0.24f * light),
                        Color(0x70030D11),
                        glow.copy(alpha = 0.28f * light),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            val interior =
                Brush.linearGradient(
                    0f to tint.copy(alpha = opacity.coerceIn(0f, 1f)),
                    0.40f to DeepWater.copy(alpha = opacity.coerceIn(0f, 1f) * 0.70f),
                    1f to tint.copy(alpha = opacity.coerceIn(0f, 1f) * 0.96f),
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
            onDrawBehind {
                drawRoundRect(interior, cornerRadius = CornerRadius(corner.toPx()))
                drawFittedArt(capsule, 0.07f * relief)
                drawRoundRect(reflection, cornerRadius = CornerRadius(corner.toPx()))
                drawRoundRect(
                    edge,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(1.4.dp.toPx()),
                )
                drawRoundRect(
                    WaterLight.copy(alpha = 0.12f * light),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size =
                        Size(
                            (size.width - 6.dp.toPx()).coerceAtLeast(0f),
                            (size.height - 6.dp.toPx()).coerceAtLeast(0f),
                        ),
                    cornerRadius = CornerRadius((corner.toPx() - 3.dp.toPx()).coerceAtLeast(0f)),
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
) {
    val round =
        when (component) {
            StoneComponent.ICON_BUTTON,
            StoneComponent.KNOB,
            StoneComponent.SLIDER_THUMB,
            StoneComponent.PROGRESS_RING,
            -> true
            else -> false
        }
    val resource = if (round) R.drawable.tidal_glass_pebble else R.drawable.tidal_glass_capsule
    val art = rememberTidalBitmap(resource)
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
                        DeepWater,
                        WaterLight,
                        22.dp,
                        if (disabled) 0.25f else intensity,
                        0.7f,
                        false,
                        WaterLight,
                    ),
            )
        }
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha =
                when {
                    disabled -> if (round) 0.42f else 0.12f
                    state == StoneState.PRESSED -> if (round) 0.82f else 0.20f
                    else -> if (round) 1f else 0.28f
                },
            modifier = Modifier.matchParentSize(),
        )
        Canvas(Modifier.matchParentSize().clip(shape)) {
            if (!disabled && state != StoneState.DEFAULT) {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(WaterLight.copy(alpha = intensity * 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.3f, 0f),
                        radius = size.width.coerceAtLeast(1f),
                    ),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                )
                if (state == StoneState.FOCUSED || state == StoneState.SELECTED) {
                    drawRoundRect(
                        WaterLight.copy(alpha = intensity * 0.55f),
                        cornerRadius =
                            CornerRadius(if (round) size.minDimension / 2f else 22.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
internal fun Modifier.tidalPressRipple(
    interaction: InteractionSource,
    reducedMotion: Boolean,
): Modifier {
    val running = rememberTidalMotionRunning(reducedMotion)
    val ripple = remember { Animatable(1f) }
    val position = remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interaction, running) {
        if (!running) {
            ripple.snapTo(1f)
            return@LaunchedEffect
        }
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Press) {
                position.value = event.pressPosition
                launch {
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
                val radius = size.maxDimension * (0.04f + progress * 0.90f)
                drawCircle(
                    WaterLight.copy(alpha = (1f - progress) * 0.34f),
                    radius,
                    origin,
                    style = Stroke((1.3f - progress * 0.6f).dp.toPx()),
                )
                drawCircle(
                    WaterLight.copy(alpha = (1f - progress) * 0.12f),
                    radius * 0.82f,
                    origin,
                    style = Stroke(0.7.dp.toPx()),
                )
            }
        }
    }
}

/** Decorative material only: no GL surface, analyzer, player or input listener. */
@Composable
fun TidalWaterOrb(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    energy: Float = 0f,
) {
    TidalWaterOrb(modifier, reducedMotion, energy = { energy })
}

/** The signal supplier is read in drawing only, so audio need not recompose the hero. */
@Composable
fun TidalWaterOrb(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    energy: () -> Float,
) {
    val running = rememberTidalMotionRunning(reducedMotion)
    val time = rememberWaterTime(running)
    val signal = rememberUpdatedState(energy)
    val orb = rememberTidalBitmap(R.drawable.tidal_glass_orb)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val level = if (running) signal.value().coerceIn(0f, 1f) else 0f
            drawOrbReflection(orb)
            drawOrbWater(time.value, level)
        }
        Image(
            bitmap = orb,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val level = if (running) signal.value().coerceIn(0f, 1f) else 0f
                        val breath = sin(time.value * 0.45f)
                        translationY = -sin(time.value * 0.65f) * 3.dp.toPx()
                        translationX = sin(time.value * 0.33f) * 2.dp.toPx()
                        rotationZ = sin(time.value * 0.27f) * 1.2f
                        scaleX = 1f + breath * 0.006f + level * 0.010f
                        scaleY = 1f - breath * 0.005f + level * 0.006f
                    },
        )
    }
}

private fun DrawScope.drawOrbReflection(orb: ImageBitmap) {
    val width = (size.width * 0.66f).roundToInt().coerceAtLeast(1)
    val height = (size.height * 0.14f).roundToInt().coerceAtLeast(1)
    val surface = Offset(size.width * 0.5f, size.height * 0.88f)
    scale(1f, -1f, pivot = surface) {
        drawImage(
            orb,
            dstOffset =
                IntOffset(
                    ((size.width - width) / 2f).roundToInt(),
                    (surface.y - height).roundToInt(),
                ),
            dstSize = IntSize(width, height),
            alpha = 0.11f,
        )
    }
}

private fun DrawScope.drawOrbWater(
    time: Float,
    energy: Float,
) {
    val water = Offset(size.width * 0.5f, size.height * 0.88f)
    for (i in 0..2) {
        val phase = (time * 0.18f + i / 3f) % 1f
        val width = size.width * (0.36f + phase * 0.54f)
        val height = width * 0.13f
        drawOval(
            WaterLight.copy(alpha = (1f - phase) * (0.17f + energy * 0.05f)),
            water - Offset(width / 2f, height / 2f),
            Size(width, height),
            style = Stroke(0.8.dp.toPx()),
        )
    }
    for (i in 0..2) {
        drawFallingDrop(time, i, water)
    }
}

private fun DrawScope.drawFallingDrop(
    time: Float,
    index: Int,
    water: Offset,
) {
    val phase = (time * 0.18f + index / 3f) % 1f
    val x = size.width * (0.38f + index * 0.12f)
    if (phase < 0.64f) {
        val fall = phase / 0.64f
        val point = Offset(x, size.height * (0.25f + fall * fall * 0.63f))
        drawOval(
            Brush.radialGradient(
                listOf(WaterLight.copy(alpha = sin(fall * PI).toFloat() * 0.62f), Color.Transparent),
                center = point,
                radius = 4.dp.toPx(),
            ),
            topLeft = point - Offset(2.dp.toPx(), 4.dp.toPx()),
            size = Size(4.dp.toPx(), 8.dp.toPx()),
        )
    } else {
        val spread = (phase - 0.64f) / 0.36f
        val width = size.width * spread * 0.36f
        val height = width * 0.13f
        drawOval(
            WaterLight.copy(alpha = (1f - spread) * 0.26f),
            topLeft = Offset(x - width / 2f, water.y - height / 2f),
            size = Size(width, height),
            style = Stroke(0.7.dp.toPx()),
        )
    }
}
