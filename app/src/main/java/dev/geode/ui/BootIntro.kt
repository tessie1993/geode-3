package dev.geode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.geode.R
import dev.geode.ui.lake.lakeFrostedPanel
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.isLivingLake
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val RING_COUNT = 3
private const val RING_STAGGER_MS = 160
private const val RING_TRAVEL_MS = 900
private const val TEXT_IN_MS = 450
private const val FADE_START_MS = 1100L
private const val FADE_OUT_MS = 300

@Composable
fun BootIntro(onDone: () -> Unit) {
    if (LocalThemePack.current.isLivingLake) {
        LakeBootIntro(onDone)
        return
    }
    val reducedMotion = LocalReducedMotion.current
    val overlayAlpha = remember { Animatable(1f) }
    val textAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val textScale = remember { Animatable(if (reducedMotion) 1f else 0.7f) }
    val rings = remember { List(RING_COUNT) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        // Motion policy changes do not restart the intro or delay the setup gates.
        delay(FADE_START_MS + FADE_OUT_MS)
        onDone()
    }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            overlayAlpha.snapTo(1f)
            textAlpha.snapTo(1f)
            textScale.snapTo(1f)
            rings.forEach { it.snapTo(0f) }
            return@LaunchedEffect
        }
        launch { textAlpha.animateTo(1f, tween(TEXT_IN_MS, easing = LinearOutSlowInEasing)) }
        launch { textScale.animateTo(1f, tween(TEXT_IN_MS + 100, easing = FastOutSlowInEasing)) }
        rings.forEachIndexed { i, ring ->
            launch {
                delay(i * RING_STAGGER_MS.toLong())
                ring.animateTo(1f, tween(RING_TRAVEL_MS, easing = FastOutSlowInEasing))
            }
        }
        delay(FADE_START_MS)
        overlayAlpha.animateTo(0f, tween(FADE_OUT_MS))
    }

    val primary = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val glint = lerp(primary, onBackground, 0.72f)
    val wordmark = LocalFontColor.current ?: glint
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(lerp(Color(0xFFEEEAE3), primary, 0.08f))
            .pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val startRadius = min(size.width, size.height) * 0.16f
            val endRadius = max(size.width, size.height) * 0.72f
            val strokeWidth = 2.dp.toPx()
            val gem = textAlpha.value
            if (gem > 0f) {
                val settle = 1f - textScale.value

                fun gemOutline(
                    radius: Float,
                    baseDeg: Float,
                    color: Color,
                    alpha: Float,
                    width: Float,
                ) {
                    rotate(baseDeg + 24f * settle, center) {
                        drawRect(
                            color = color.copy(alpha = alpha * gem),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = width),
                        )
                    }
                }
                val r = startRadius * 1.15f * textScale.value
                gemOutline(r, 45f, primary, 0.10f, strokeWidth * 5f)
                gemOutline(r, 45f, glint, 0.35f, strokeWidth)
                gemOutline(r * 0.72f, 15f, primary, 0.45f, strokeWidth)
            }
            rings.forEach { ring ->
                val p = ring.value
                if (p > 0f && p < 1f) {
                    val radius = startRadius + (endRadius - startRadius) * p
                    val fade = 1f - p
                    drawCircle(
                        color = primary.copy(alpha = fade * 0.12f),
                        radius = radius,
                        style = Stroke(width = strokeWidth * 7f),
                    )
                    drawCircle(
                        color = primary.copy(alpha = fade * 0.28f),
                        radius = radius,
                        style = Stroke(width = strokeWidth * 3f),
                    )
                    drawCircle(
                        color = glint.copy(alpha = fade * 0.5f),
                        radius = radius,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    scaleX = textScale.value
                    scaleY = textScale.value
                },
        ) {
            Text(
                "Geode",
                color = wordmark,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        shadow = Shadow(color = primary, blurRadius = 36f),
                    ),
            )
            Text(
                "VISUALIZE THE INVISIBLE",
                color = primary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.5.sp),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Shares the shell's persistent world; the intro adds only native branding and a skip target. */
@Composable
private fun LakeBootIntro(onDone: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            onDone()
        } else {
            alpha.animateTo(1f, tween(220))
            delay(650)
            alpha.animateTo(0f, tween(260))
            onDone()
        }
    }
    val skip = stringResource(R.string.ui2_skip_intro)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClickLabel = skip, onClick = onDone)
            .semantics { contentDescription = skip }
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .graphicsLayer { this.alpha = alpha.value }
                .lakeFrostedPanel()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.ui2_intro_tagline), style = MaterialTheme.typography.labelLarge)
        }
    }
}
