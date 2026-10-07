package dev.geode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.geode.R
import dev.geode.ui.theme.LocalBackgroundDim
import dev.geode.ui.theme.LocalThemePack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ORBIT_SEGMENTS = 32
private const val ORBIT_DROPLETS = 8
private const val SCENE_MOTES = 18
private const val WORLD_WATER_HORIZON = 0.29f
private const val FAR_ATMOSPHERE_HEIGHT = 0.58f
private const val LAKE_SOURCE_HORIZON = 0.51f
private const val FAR_IMAGE_STRIPS = 64

/** A portable layered water world. Perspective and occlusion are explicit; no GL owner is added. */
@Composable
internal fun SpatialWaterBackground(
    modifier: Modifier,
    reducedMotion: Boolean,
) {
    val running = rememberTidalMotionRunning(reducedMotion) && LocalTidalSceneTime.current != null
    val time = rememberSpatialWaterTime(running)
    val pack = LocalThemePack.current
    val palette = pack.palette
    val dim = LocalBackgroundDim.current.coerceIn(0f, 1f)
    val lake = rememberTidalBitmap(R.drawable.spatial_lake_atmosphere)
    val ferns = rememberTidalBitmap(R.drawable.spatial_foreground_ferns)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawLakePlanes(lake, time.value)
            drawRect(palette.background.copy(alpha = if (pack.isLight) 0.62f else 0.30f))
            drawRect(
                Brush.verticalGradient(
                    0f to palette.backgroundDeep.copy(alpha = 0.32f),
                    0.42f to Color.Transparent,
                    1f to palette.backgroundDeep.copy(alpha = 0.72f),
                ),
            )
            drawSceneMist(time.value, palette.primary, pack.isLight)
            drawSceneWater(time.value, palette.glow, palette.backgroundDeep)
            val awake = if (running) (time.value / 1.8f).coerceIn(0f, 1f) else 0f
            drawSceneFilaments(time.value, palette.primary, 0.035f + awake * 0.065f)
            if (running) drawSpatialMotes(time.value, palette.glow, if (pack.isLight) 0.50f else 1f)
        }
        Image(
            bitmap = ferns,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            alpha = if (pack.isLight) 0.38f else 0.66f,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.24f)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        translationX = sin(time.value * 0.10f) * 6.dp.toPx()
                        translationY = sin(time.value * 0.09f) * 2.dp.toPx()
                    },
        )
        if (dim > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = dim)) }
    }
}

/** The far panorama and near source-water plane share a world horizon, with a soft overlap. */
private fun DrawScope.drawLakePlanes(
    lake: ImageBitmap,
    time: Float,
) {
    if (size.minDimension <= 0f) return
    val farWidth = (size.width * 1.045f).roundToInt().coerceAtLeast(1)
    val verticalPad = 2.dp.toPx()
    val farHeight =
        (size.height * FAR_ATMOSPHERE_HEIGHT + verticalPad * 2f)
            .roundToInt()
            .coerceAtLeast(1)
    val cropScale = max(farWidth.toFloat() / lake.width, farHeight.toFloat() / lake.height)
    val sourceWidth = min(lake.width, (farWidth / cropScale).roundToInt()).coerceAtLeast(1)
    val sourceHeight = min(lake.height, (farHeight / cropScale).roundToInt()).coerceAtLeast(1)
    val sourceLeft = (lake.width - sourceWidth) / 2
    val sourceTop =
        (lake.height * LAKE_SOURCE_HORIZON - sourceHeight * 0.5f)
            .roundToInt()
            .coerceIn(0, lake.height - sourceHeight)
    drawNearLakeWater(lake, time, sourceLeft, sourceWidth, farWidth)
    val drift = sin(time * 0.075f) * 2.dp.toPx()
    val topDrift = sin(time * 0.060f) * 1.5.dp.toPx()
    val destination =
        IntOffset(
            ((size.width - farWidth) / 2f + drift).roundToInt(),
            (-verticalPad + topDrift).roundToInt(),
        )
    val fadeTop = size.height * 0.40f
    val fadeBottom = size.height * FAR_ATMOSPHERE_HEIGHT
    clipRect(top = 0f, bottom = fadeTop) {
        drawImage(
            lake,
            srcOffset = IntOffset(sourceLeft, sourceTop),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstOffset = destination,
            dstSize = IntSize(farWidth, farHeight),
        )
    }
    repeat(FAR_IMAGE_STRIPS) { strip ->
        val top = fadeTop + (fadeBottom - fadeTop) * strip / FAR_IMAGE_STRIPS
        val bottom = fadeTop + (fadeBottom - fadeTop) * (strip + 1) / FAR_IMAGE_STRIPS
        val alpha = 1f - (strip + 0.5f) / FAR_IMAGE_STRIPS
        clipRect(top = top, bottom = bottom) {
            drawImage(
                lake,
                srcOffset = IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = destination,
                dstSize = IntSize(farWidth, farHeight),
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawNearLakeWater(
    lake: ImageBitmap,
    time: Float,
    sourceLeft: Int,
    sourceWidth: Int,
    destinationWidth: Int,
) {
    val sourceTop = (lake.height * LAKE_SOURCE_HORIZON).roundToInt()
    val top = (size.height * WORLD_WATER_HORIZON).roundToInt()
    val drift = sin(time * 0.11f) * 5.dp.toPx()
    drawImage(
        lake,
        srcOffset = IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceWidth, (lake.height - sourceTop).coerceAtLeast(1)),
        dstOffset = IntOffset(((size.width - destinationWidth) / 2f + drift).roundToInt(), top),
        dstSize = IntSize(destinationWidth, (size.height.roundToInt() - top).coerceAtLeast(1)),
    )
}

private fun DrawScope.drawSceneMist(
    time: Float,
    light: Color,
    isLight: Boolean,
) {
    repeat(3) { index ->
        val drift = sin(time * 0.085f + index * 1.8f) * 12.dp.toPx()
        drawOval(
            light.copy(alpha = if (isLight) 0.045f else 0.030f),
            topLeft = Offset(-size.width * 0.10f + drift, size.height * (0.26f + index * 0.06f)),
            size = Size(size.width * 1.20f, size.height * 0.065f),
        )
    }
}

private fun DrawScope.drawSceneWater(
    time: Float,
    light: Color,
    shade: Color,
) {
    val horizon = size.height * WORLD_WATER_HORIZON
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, shade.copy(alpha = 0.36f)), startY = horizon),
        topLeft = Offset(0f, horizon),
        size = Size(size.width, size.height - horizon),
    )
    repeat(12) { index ->
        val depth = (index + 1) / 12f
        val y = horizon + (size.height - horizon) * depth * depth
        val shift = sin(time * 0.22f + index * 0.85f) * (3f + depth * 10f).dp.toPx()
        drawOval(
            light.copy(alpha = 0.035f + depth * 0.015f),
            topLeft = Offset(-size.width * 0.08f + shift, y),
            size = Size(size.width * 1.15f, (1f + depth * 3f).dp.toPx()),
            style = Stroke((0.35f + depth * 0.30f).dp.toPx()),
        )
    }
}

private fun DrawScope.drawSceneFilaments(
    time: Float,
    light: Color,
    alpha: Float,
) {
    val path = Path()
    val source = Offset(size.width * 0.50f, size.height * 0.72f)
    repeat(6) { index ->
        val endX = size.width * (0.04f + index * 0.184f)
        val sway = sin(time * 0.28f + index) * 11.dp.toPx()
        path.reset()
        path.moveTo(source.x, source.y)
        path.cubicTo(
            source.x + (endX - source.x) * 0.20f + sway,
            size.height * 0.79f,
            endX - sway,
            size.height * 0.89f,
            endX,
            size.height * 1.02f,
        )
        drawPath(path, light.copy(alpha = alpha * 0.22f), style = Stroke(4.dp.toPx()))
        drawPath(path, light.copy(alpha = alpha), style = Stroke(0.65.dp.toPx()))
    }
}

internal fun DrawScope.drawSpatialMotes(
    time: Float,
    light: Color,
    intensity: Float,
) {
    repeat(SCENE_MOTES) { index ->
        val depth = 0.30f + index % 4 * 0.22f
        val seed = index * 1.71f
        val origin =
            Offset(
                size.width * (0.06f + (index * 37 % 89) / 100f),
                size.height * (0.06f + (index * 29 % 87) / 100f),
            )
        val drift =
            Offset(
                sin(time * 0.15f + seed) * 13.dp.toPx(),
                cos(time * 0.11f + seed) * 18.dp.toPx(),
            )
        val sparkle = 0.65f + sin(time * 0.46f + seed) * 0.35f
        drawSpatialLightMote(
            origin + drift * depth,
            (0.65f + depth * 1.25f).dp.toPx(),
            light,
            (0.10f + depth * 0.20f) * sparkle * intensity,
        )
    }
}

internal fun DrawScope.drawSpatialLightMote(
    point: Offset,
    radius: Float,
    light: Color,
    alpha: Float,
) {
    drawCircle(light.copy(alpha = alpha * 0.06f), radius * 5.2f, point)
    drawCircle(light.copy(alpha = alpha * 0.15f), radius * 2.6f, point)
    drawCircle(light.copy(alpha = alpha), radius, point)
    drawCircle(Color.White.copy(alpha = alpha * 0.64f), radius * 0.35f, point)
}

/** Front/back glass plates enclose real composable artwork; orbit segments respect that depth. */
@Composable
internal fun SpatialGlassVolume(
    modifier: Modifier,
    reducedMotion: Boolean,
    energy: () -> Float,
    artwork: @Composable () -> Unit,
) {
    val running = rememberTidalMotionRunning(reducedMotion) && LocalTidalSceneTime.current != null
    val time = rememberSpatialWaterTime(running)
    val signal = rememberUpdatedState(energy)
    val palette = LocalThemePack.current.palette
    val shell = rememberTidalBitmap(R.drawable.spatial_glass_orb_shell)
    val tint = rememberSpatialGlassTint()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val level = if (running) signal.value().coerceIn(0f, 1f) else 0f
            val pose = volumePose(time.value, running, level, density)
            drawGlassReflection(shell, tint, time.value, pose)
            drawVolumeWater(time.value, palette.glow, level, pose)
            if (running) {
                drawVolumeOrbit(time.value, palette.glow, pose, front = false)
                drawVolumeDrops(time.value, palette.glow, pose)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val level = if (running) signal.value().coerceIn(0f, 1f) else 0f
                    val pose = volumePose(time.value, running, level, density)
                    cameraDistance = size.maxDimension.coerceAtLeast(1f) * 4f
                    translationY = pose.y
                    translationX = pose.x
                    rotationX = if (running) sin(time.value * 0.29f) * 6f else 0f
                    rotationY = sin(time.value * 0.37f) * (9f + level * 2f)
                    rotationZ = sin(time.value * 0.27f) * 2f
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                    alpha = 0.55f + pose.reveal * 0.45f
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawVolumeCore(palette.primary, palette.glow) }
            Image(
                shell,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alpha = 0.26f,
                colorFilter = tint,
            )
            Box(
                Modifier
                    .fillMaxHeight(0.44f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        cameraDistance = size.maxDimension.coerceAtLeast(1f) * 3f
                        rotationX = sin(time.value * 0.27f) * 8f
                        rotationY = sin(time.value * 0.58f) * 52f
                        rotationZ = sin(time.value * 0.33f) * 7f
                        translationY = sin(time.value * 0.47f) * 2.dp.toPx()
                    }.clip(RoundedCornerShape(8.dp)),
            ) { artwork() }
            Image(
                shell,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alpha = 0.60f,
                colorFilter = tint,
            )
        }
        if (running) {
            Canvas(Modifier.fillMaxSize()) {
                val level = signal.value().coerceIn(0f, 1f)
                drawVolumeOrbit(
                    time.value,
                    palette.glow,
                    volumePose(time.value, running, level, density),
                    front = true,
                )
            }
        }
    }
}

private data class VolumePose(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val reveal: Float,
    val altitude: Float,
)

/** The same stateless pose drives the shell, water projection, reflection and droplet impacts. */
private fun volumePose(
    time: Float,
    running: Boolean,
    energy: Float,
    density: Float,
): VolumePose {
    val reveal = if (running) (time / 1.8f).coerceIn(0f, 1f) else 1f
    val breath = sin(time * 0.45f)
    val y = (1f - reveal) * 16f * density - sin(time * 0.65f) * 8f * density
    return VolumePose(
        x = sin(time * 0.33f) * 4f * density,
        y = y,
        scaleX = 0.92f + reveal * 0.08f + breath * 0.010f + energy * 0.018f,
        scaleY = 0.96f + reveal * 0.04f - breath * 0.012f + energy * 0.010f,
        reveal = reveal,
        altitude = ((8f * density - y) / (24f * density)).coerceIn(0f, 1f),
    )
}

private fun waterProjection(
    size: Size,
    pose: VolumePose,
): Offset = Offset(size.width * 0.50f + pose.x, size.height * 0.88f + pose.y * 0.18f)

private fun DrawScope.drawVolumeCore(
    primary: Color,
    light: Color,
) {
    val radius = (size.minDimension * 0.34f).coerceAtLeast(1f)
    drawCircle(light.copy(alpha = 0.035f), radius * 1.26f, center)
    drawCircle(primary.copy(alpha = 0.13f), radius, center)
    drawCircle(
        Brush.radialGradient(
            listOf(primary.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius,
        center,
    )
}

private data class OrbitPoint(
    val position: Offset,
    val depth: Float,
)

private fun orbitPoint(
    size: Size,
    time: Float,
    angle: Float,
    orbit: Int,
    pose: VolumePose,
): OrbitPoint {
    val radius = size.width * (0.38f + sin(angle * 3f + time * 0.75f) * 0.009f) * pose.scaleX
    val x = cos(angle) * radius
    val y = sin(angle) * radius * 0.22f
    val depth = sin(angle) * radius * 0.60f
    val tilt = -0.30f + orbit * 0.68f + sin(time * 0.11f) * 0.06f
    val distance = size.maxDimension.coerceAtLeast(1f) * 3f
    val perspective = distance / (distance - depth)
    return OrbitPoint(
        Offset(
            size.width * 0.50f + pose.x + (x * cos(tilt) - y * sin(tilt)) * perspective,
            size.height * 0.41f + pose.y + (x * sin(tilt) + y * cos(tilt)) * perspective * pose.scaleY,
        ),
        depth,
    )
}

private fun DrawScope.drawVolumeOrbit(
    time: Float,
    light: Color,
    pose: VolumePose,
    front: Boolean,
) {
    if (size.minDimension <= 0f) return
    val reveal = ((time - 0.35f) / 1.45f).coerceIn(0f, 1f) * pose.reveal
    val path = Path()
    repeat(2) { orbit ->
        var connected = false
        path.reset()
        for (segment in 0..ORBIT_SEGMENTS) {
            val angle = segment * 2f * PI.toFloat() / ORBIT_SEGMENTS + time * 0.14f
            val point = orbitPoint(size, time, angle, orbit, pose)
            if ((point.depth >= 0f) == front) {
                if (connected) {
                    path.lineTo(point.position.x, point.position.y)
                } else {
                    path.moveTo(point.position.x, point.position.y)
                }
                connected = true
            } else if (connected) {
                drawOrbitPath(path, light, reveal)
                path.reset()
                connected = false
            }
        }
        if (connected) drawOrbitPath(path, light, reveal)
    }
    repeat(ORBIT_DROPLETS) { index ->
        val angle = time * (0.48f + index % 3 * 0.045f) + index * PI.toFloat() / 4f
        val point = orbitPoint(size, time, angle, index % 2, pose)
        if ((point.depth >= 0f) == front) {
            val depth = point.depth / size.width + 0.5f
            drawSpatialLightMote(
                point.position,
                (1.8f + depth * 1.4f).dp.toPx(),
                light,
                reveal * (0.48f + depth * 0.20f),
            )
        }
    }
}

private fun DrawScope.drawOrbitPath(
    path: Path,
    light: Color,
    reveal: Float,
) {
    drawPath(path, light.copy(alpha = reveal * 0.09f), style = Stroke(4.dp.toPx()))
    drawPath(path, light.copy(alpha = reveal * 0.50f), style = Stroke(0.95.dp.toPx()))
}

private fun DrawScope.drawGlassReflection(
    shell: ImageBitmap,
    tint: ColorFilter,
    time: Float,
    pose: VolumePose,
) {
    val width =
        (size.width * 0.66f * pose.scaleX * (1f - pose.altitude * 0.12f))
            .roundToInt()
            .coerceAtLeast(1)
    val height =
        (size.height * 0.15f * pose.scaleY * (1f - pose.altitude * 0.25f))
            .roundToInt()
            .coerceAtLeast(1)
    val water = waterProjection(size, pose)
    repeat(8) { strip ->
        val top = water.y + height * strip / 8f
        clipRect(top = top, bottom = top + height / 8f + 1f) {
            translate(left = sin(time * 0.90f + strip * 0.72f) * 3.dp.toPx()) {
                scale(1f, -1f, pivot = water) {
                    drawImage(
                        shell,
                        dstOffset =
                            IntOffset(
                                (water.x - width / 2f).roundToInt(),
                                (water.y - height).roundToInt(),
                            ),
                        dstSize = IntSize(width, height),
                        alpha =
                            (0.20f - strip * 0.018f).coerceAtLeast(0f) *
                                pose.reveal * (1f - pose.altitude * 0.45f),
                        colorFilter = tint,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawVolumeDrops(
    time: Float,
    light: Color,
    pose: VolumePose,
) {
    val water = waterProjection(size, pose)
    repeat(3) { index ->
        val phase = (time * 0.16f + index / 3f) % 1f
        val impact = water + Offset(size.width * (index - 1) * 0.075f * pose.scaleX, 0f)
        if (phase < 0.68f) {
            val fall = phase / 0.68f
            val start = Offset(impact.x, size.height * 0.60f + pose.y)
            val point = start + (impact - start) * (fall * fall)
            drawOval(
                light.copy(alpha = sin(fall * PI).toFloat() * 0.54f * pose.reveal),
                topLeft = point - Offset(1.2.dp.toPx(), 2.8.dp.toPx()),
                size = Size(2.4.dp.toPx(), 5.6.dp.toPx()),
            )
        } else {
            val splash = (phase - 0.68f) / 0.32f
            val width = size.width * splash * 0.18f
            val height = width * 0.14f
            drawOval(
                light.copy(alpha = (1f - splash) * 0.24f * pose.reveal),
                topLeft = impact - Offset(width / 2f, height / 2f),
                size = Size(width, height),
                style = Stroke(0.65.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawVolumeWater(
    time: Float,
    light: Color,
    energy: Float,
    pose: VolumePose,
) {
    val water = waterProjection(size, pose)
    val contactWidth = size.width * 0.36f * pose.scaleX * (1f - pose.altitude * 0.18f)
    drawOval(
        Color.Black.copy(alpha = 0.24f * (1f - pose.altitude * 0.35f) * pose.reveal),
        topLeft = water - Offset(contactWidth / 2f, size.height * 0.015f),
        size = Size(contactWidth, size.height * 0.030f),
    )
    repeat(4) { index ->
        val phase = (time * 0.22f + index * 0.25f) % 1f
        val width = size.width * (0.16f + phase * 0.76f) * pose.scaleX
        val height = width * 0.14f
        drawOval(
            light.copy(
                alpha = (1f - phase) * (0.21f + energy * 0.06f) * (0.45f + pose.reveal * 0.55f),
            ),
            topLeft = water - Offset(width / 2f, height / 2f),
            size = Size(width, height),
            style = Stroke((0.55f + (1f - phase) * 0.45f).dp.toPx()),
        )
    }
}
