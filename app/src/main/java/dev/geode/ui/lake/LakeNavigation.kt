package dev.geode.ui.lake

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.geode.R
import dev.geode.ui.GeodeAppState
import dev.geode.ui.GeodeDestination
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt
import dev.geode.ui.theme.StoneState
import dev.geode.ui.world.WorldLensAnchor

/** Native routes and input above the world. The GL surface is never transformed by this shell. */
@Composable
fun LivingLakeShell(
    appState: GeodeAppState,
    destinations: List<GeodeDestination>,
    hasMedia: Boolean,
    title: String?,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onLensesChanged: (List<WorldLensAnchor>) -> Unit,
    worldAvailable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (twoPane: Boolean) -> Unit,
) {
    val routes = remember(destinations) { (listOf(GeodeDestination.PLAYER) + destinations).distinct() }
    val layout = remember { LensLayoutRelay() }
    SideEffect {
        layout.onChange = onLensesChanged
        layout.configure(appState.orbitOpen, routes)
    }
    DisposableEffect(layout) {
        onDispose { layout.clear() }
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { layout.setViewport(it.boundsInWindow()) },
    ) {
        val twoPane = maxWidth >= 900.dp
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!appState.orbitOpen) content(twoPane)
            }
            if (!appState.orbitOpen) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (appState.onPlayer) {
                        GeodeNavigationHandle(appState = appState)
                    } else {
                        CompactPlayingAnchor(
                            appState = appState,
                            hasMedia = hasMedia,
                            title = title,
                            isPlaying = isPlaying,
                            progress = progress,
                            onPlayPause = onPlayPause,
                        )
                    }
                }
            }
        }
        if (appState.orbitOpen) {
            LakeOrbitOverlay(
                appState = appState,
                routes = routes,
                worldAvailable = worldAvailable,
                layout = layout,
            )
        }
    }
}

@Composable
private fun GeodeNavigationHandle(
    appState: GeodeAppState,
    modifier: Modifier = Modifier,
) {
    val current = stringResource(appState.dest.labelRes)
    val description = stringResource(R.string.ui2_open_navigation_from, current)
    Row(
        modifier
            .heightIn(min = LakeMaterials.MinimumTouchTarget)
            .lakeFrostedPanel(corner = 18.dp)
            .clickable(onClickLabel = description, role = Role.Button, onClick = appState::openOrbit)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LakeGeodeMark(Modifier.size(28.dp))
        Text(
            stringResource(R.string.ui2_navigation),
            style = MaterialTheme.typography.labelLarge,
            color = LakeMaterials.Ink,
        )
    }
}

@Composable
private fun CompactPlayingAnchor(
    appState: GeodeAppState,
    hasMedia: Boolean,
    title: String?,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
) {
    val trackTitle = title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.state_nothing_playing)
    val openPlayer = stringResource(R.string.ui2_open_player)
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .lakeFrostedPanel(corner = 22.dp)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = LakeMaterials.MinimumTouchTarget)
                    .clickable(onClickLabel = openPlayer, role = Role.Button) { appState.navigateTo(GeodeDestination.PLAYER) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.ui2_now_playing),
                    style = MaterialTheme.typography.labelSmall,
                    color = LakeMaterials.MutedInk,
                )
                Text(
                    trackTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LakeMaterials.Ink,
                )
            }
            IconButton(onClick = onPlayPause, enabled = hasMedia, modifier = Modifier.size(LakeMaterials.MinimumTouchTarget)) {
                StoneIconArt(
                    if (isPlaying) StoneIcon.PAUSE else StoneIcon.PLAY,
                    stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                    tint = if (hasMedia) LakeMaterials.Ink else LakeMaterials.MutedInk.copy(alpha = 0.6f),
                )
            }
            val current = stringResource(appState.dest.labelRes)
            val navigationDescription = stringResource(R.string.ui2_open_navigation_from, current)
            Box(
                Modifier
                    .size(LakeMaterials.MinimumTouchTarget)
                    .lakeFrostedPanel(corner = 24.dp, opacity = 0.84f)
                    .clickable(onClickLabel = navigationDescription, role = Role.Button, onClick = appState::openOrbit)
                    .semantics { contentDescription = navigationDescription },
                contentAlignment = Alignment.Center,
            ) {
                LakeGeodeMark(Modifier.size(30.dp))
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) },
        ) {
            val y = size.height / 2f
            drawLine(LakeMaterials.Teal.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
            if (safeProgress > 0f) {
                drawLine(LakeMaterials.Teal, Offset(0f, y), Offset(size.width * safeProgress, y), size.height, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LakeOrbitOverlay(
    appState: GeodeAppState,
    routes: List<GeodeDestination>,
    worldAvailable: Boolean,
    layout: LensLayoutRelay,
) {
    val navigation = stringResource(R.string.ui2_navigation)
    val close = stringResource(R.string.ui2_close_navigation)
    val closeFocus = remember { FocusRequester() }
    val reducedMotion = LocalReducedMotion.current
    val revealMillis = LocalThemePack.current.motion.selectedDurationMs.coerceAtLeast(0)
    val reveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    LaunchedEffect(reducedMotion, revealMillis) {
        if (reducedMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(1f, tween(durationMillis = revealMillis, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.fillMaxSize().semantics {
            paneTitle = navigation
            isTraversalGroup = true
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LakeMaterials.Frost.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = appState::closeOrbit,
                ).clearAndSetSemantics {},
        )
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                // Only native overlay chrome fades. The GL host and measured hit regions
                // keep their geometry, and closing removes this overlay immediately.
                .graphicsLayer { alpha = reveal.value }
                .focusGroup(),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    navigation,
                    modifier =
                        Modifier
                            .weight(1f)
                            .lakeFrostedPanel(corner = 16.dp)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .semantics { heading() },
                    color = LakeMaterials.Ink,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = appState::closeOrbit,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(LakeMaterials.MinimumTouchTarget)
                            .focusRequester(closeFocus)
                            .lakeFrostedPanel(corner = 24.dp),
                ) {
                    StoneIconArt(StoneIcon.CLOSE, close, tint = LakeMaterials.Ink)
                }
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val fontScale = LocalDensity.current.fontScale
                if (maxWidth < 300.dp || maxHeight < 390.dp || fontScale > 1.3f) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (destination in routes) {
                            ListedOrbitRoute(destination, appState, layout)
                        }
                    }
                } else {
                    val sideOffset = minOf(maxWidth * 0.27f, 138.dp)
                    val verticalOffset = 120.dp
                    Box(Modifier.fillMaxWidth().height(410.dp), contentAlignment = Alignment.Center) {
                        val outer = routes.filter { it != GeodeDestination.PLAYER }
                        for (destination in outer) {
                            val (x, y) =
                                when (destination) {
                                    GeodeDestination.LIBRARY -> -sideOffset to -verticalOffset
                                    GeodeDestination.VISUALS -> sideOffset to -verticalOffset
                                    GeodeDestination.STUDIO -> -sideOffset to verticalOffset
                                    GeodeDestination.SETTINGS -> sideOffset to verticalOffset
                                    GeodeDestination.PLAYER -> 0.dp to 0.dp
                                }
                            OrbitRoute(
                                destination,
                                appState,
                                worldAvailable,
                                layout,
                                modifier = Modifier.offset(x = x, y = y),
                            )
                        }
                        OrbitRoute(GeodeDestination.PLAYER, appState, worldAvailable, layout, central = true)
                    }
                }
            }
            Text(
                stringResource(R.string.ui2_navigation_hint),
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .lakeFrostedPanel(corner = 14.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                color = LakeMaterials.MutedInk,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OrbitRoute(
    destination: GeodeDestination,
    appState: GeodeAppState,
    worldAvailable: Boolean,
    layout: LensLayoutRelay,
    modifier: Modifier = Modifier,
    central: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val selected = appState.dest == destination
    Column(
        modifier
            .width(if (central) 112.dp else 102.dp)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = { appState.navigateTo(destination) },
            ).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbitLens(destination, selected, worldAvailable, interactions, layout, if (central) 80.dp else 64.dp)
        Text(
            stringResource(destination.labelRes),
            modifier =
                Modifier
                    .lakeFrostedPanel(corner = 14.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            color = LakeMaterials.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ListedOrbitRoute(
    destination: GeodeDestination,
    appState: GeodeAppState,
    layout: LensLayoutRelay,
) {
    val interactions = remember { MutableInteractionSource() }
    val selected = appState.dest == destination
    Row(
        Modifier
            .fillMaxWidth()
            .lakeFrostedPanel(corner = 20.dp)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = { appState.navigateTo(destination) },
            ).semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OrbitLens(
            destination,
            selected,
            worldAvailable = false,
            interactions,
            layout,
            LakeMaterials.MinimumTouchTarget,
            opticalAnchor = false,
        )
        Text(
            stringResource(destination.labelRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = LakeMaterials.Ink,
        )
        if (selected) StoneIconArt(StoneIcon.CHECK, null, tint = LakeMaterials.Teal)
    }
}

@Composable
private fun OrbitLens(
    destination: GeodeDestination,
    selected: Boolean,
    worldAvailable: Boolean,
    interactions: MutableInteractionSource,
    layout: LensLayoutRelay,
    size: androidx.compose.ui.unit.Dp,
    opticalAnchor: Boolean = true,
) {
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val state =
        when {
            pressed -> StoneState.PRESSED
            focused -> StoneState.FOCUSED
            selected -> StoneState.SELECTED
            else -> StoneState.DEFAULT
        }
    DisposableEffect(destination, layout) {
        onDispose { layout.remove(destination) }
    }
    Box(
        Modifier
            .size(size)
            .onGloballyPositioned {
                if (opticalAnchor) layout.setLens(destination, it.boundsInWindow(), selected)
            }.then(
                if (focused || selected) {
                    Modifier.border(if (focused) 2.dp else 1.dp, LakeMaterials.Teal.copy(alpha = 0.75f), CircleShape)
                } else {
                    Modifier
                },
            ).background(if (pressed) LakeMaterials.Water.copy(alpha = 0.4f) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!worldAvailable) LakeSurfaceArt(StoneComponent.ICON_BUTTON, state, Modifier.matchParentSize())
        if (destination == GeodeDestination.PLAYER) {
            if (!worldAvailable) LakeGeodeMark(Modifier.size(if (size > 64.dp) 40.dp else 28.dp))
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .then(if (worldAvailable) Modifier.lakeFrostedPanel(corner = 18.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                StoneIconArt(destination.icon, null, Modifier.size(28.dp), tint = LakeMaterials.Ink)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(LakeMaterials.Frost, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                StoneIconArt(StoneIcon.CHECK, null, Modifier.size(14.dp), tint = LakeMaterials.Teal)
            }
        }
    }
}

/** A deterministic native identity mark for handles and the renderer-unavailable fallback. */
@Composable
private fun LakeGeodeMark(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        fun point(
            x: Float,
            y: Float,
        ) = Offset(size.width * x, size.height * y)
        val rim =
            listOf(
                point(0.25f, 0.12f),
                point(0.7f, 0.08f),
                point(0.94f, 0.43f),
                point(0.78f, 0.88f),
                point(0.28f, 0.95f),
                point(0.06f, 0.5f),
            )
        val center = point(0.49f, 0.5f)
        val tones =
            listOf(
                LakeMaterials.Teal,
                LakeMaterials.MutedInk,
                LakeMaterials.Ink,
                LakeMaterials.Teal,
                LakeMaterials.MutedInk,
                LakeMaterials.Water,
            )
        for (index in rim.indices) {
            val facet =
                Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(rim[index].x, rim[index].y)
                    val next = rim[(index + 1) % rim.size]
                    lineTo(next.x, next.y)
                    close()
                }
            drawPath(facet, tones[index])
        }
        drawCircle(LakeMaterials.Frost, radius = size.minDimension * 0.12f, center = center)
        drawLine(LakeMaterials.Water, point(0.3f, 0.25f), point(0.42f, 0.43f), size.minDimension * 0.055f, StrokeCap.Round)
    }
}

/** Emits only changed layout snapshots. Native hit testing remains the source of all positions. */
private class LensLayoutRelay {
    var onChange: (List<WorldLensAnchor>) -> Unit = {}
    private var viewport: Rect? = null
    private var enabled = false
    private var order = emptyList<GeodeDestination>()
    private val lenses = mutableMapOf<GeodeDestination, Pair<Rect, Boolean>>()
    private var emitted = emptyList<WorldLensAnchor>()

    fun configure(
        active: Boolean,
        destinations: List<GeodeDestination>,
    ) {
        enabled = active
        order = destinations
        publish()
    }

    fun setViewport(bounds: Rect) {
        viewport = bounds
        publish()
    }

    fun setLens(
        destination: GeodeDestination,
        bounds: Rect,
        selected: Boolean,
    ) {
        lenses[destination] = bounds to selected
        publish()
    }

    fun remove(destination: GeodeDestination) {
        lenses.remove(destination)
        publish()
    }

    fun clear() {
        enabled = false
        lenses.clear()
        publish()
    }

    private fun publish() {
        val bounds = viewport
        val snapshot =
            if (!enabled || bounds == null || bounds.isEmpty) {
                emptyList()
            } else {
                order.take(5).mapNotNull { destination ->
                    lenses[destination]
                        ?.takeIf { (lens, _) -> lens.overlaps(bounds) }
                        ?.let { (lens, selected) ->
                            WorldLensAnchor(
                                x = ((lens.center.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
                                y = ((lens.center.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
                                radius = (minOf(lens.width, lens.height) * 0.5f / minOf(bounds.width, bounds.height)).coerceIn(0f, 0.5f),
                                selected = selected,
                            )
                        }
                }
            }
        if (snapshot != emitted) {
            emitted = snapshot
            onChange(snapshot)
        }
    }
}
