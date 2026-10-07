package dev.geode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import dev.geode.R
import dev.geode.analysis.SearchMatcher
import dev.geode.data.BootAnimationStore
import dev.geode.data.GeodePrefsFiles
import dev.geode.render.VisualizerView
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt
import dev.geode.ui.theme.StoneState
import dev.geode.ui.theme.StoneSurfaceArt
import dev.geode.ui.theme.isJellyGlass
import dev.geode.ui.theme.rememberStoneInteraction
import dev.geode.ui.theme.rememberStoneState
import dev.geode.ui.theme.stonePress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val CRASH_REPORT_MAX_BYTES = 64 * 1024

/**
 * A destination paired with the resolved label and icon the navigation surfaces draw for it.
 *
 * Built from [GeodeDestination.entries], so the bar and the rail cannot drift out of step with
 * the screens they switch between, and selection compares destinations rather than positions.
 */
private data class NavEntry(
    val destination: GeodeDestination,
    val item: CrystalNavItem,
)

@Composable
fun AppRoot() {
    val viewModel: PlayerViewModel = geodeViewModel()
    val settingsViewModel: SettingsViewModel = geodeViewModel()
    val context = LocalContext.current
    val visualizerView = remember { VisualizerView(context) }
    // GLSurfaceView renders continuously and only ever stops when its host says so. Without this
    // the render thread keeps drawing whenever the app is visible but not resumed — behind a
    // permission dialog, in split-screen, partially obscured — burning battery on frames nobody
    // sees. GLSurfaceView's contract is that the owner forwards these two.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, visualizerView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> visualizerView.onResume()
                    Lifecycle.Event.ON_PAUSE -> visualizerView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            visualizerView.onPause()
        }
    }
    val themePack by settingsViewModel.theme.collectAsStateWithLifecycle()
    val gui by settingsViewModel.guiPrefs.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val effectiveTheme =
        if (gui.followSystemDark && !systemDark) {
            dev.geode.ui.theme.ThemePackCatalog.all
                .firstOrNull { it.isLight } ?: themePack
        } else {
            themePack
        }
    val externalDisplay = rememberExternalDisplay()
    val appState = rememberGeodeAppState(externalDisplay)
    val bootAnimEnabled = remember { BootAnimationStore(GeodePrefsFiles(context).general).load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onSecondScreen = gui.secondScreen && externalDisplay != null
    if (onSecondScreen) {
        SecondScreenCanvas(externalDisplay, visualizerView)
    }

    var crashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashText =
            withContext(Dispatchers.IO) {
                val file = java.io.File(context.filesDir, "crash-latest.txt")
                if (file.exists()) {
                    file.inputStream().use { stream ->
                        val buf = ByteArray(CRASH_REPORT_MAX_BYTES)
                        var read = 0
                        while (read < buf.size) {
                            val n = stream.read(buf, read, buf.size - read)
                            if (n < 0) break
                            read += n
                        }
                        String(buf, 0, read, Charsets.UTF_8)
                    }
                } else {
                    null
                }
            }
    }
    VisualizerEngineBindings(viewModel, visualizerView)
    androidx.activity.compose.BackHandler(enabled = !appState.onPlayer) { appState.resetToPlayer() }
    CrystalMaterialTheme(
        pack = effectiveTheme,
        gui = gui,
        motionObscured = appState.expanded || appState.searching,
    ) {
        val miniPlayer: @Composable () -> Unit = {
            MiniPlayer(
                title =
                    listOfNotNull(
                        state.title,
                        state.artist?.takeIf { it.isNotBlank() },
                    ).joinToString(" \u2014 ").ifBlank { null },
                isPlaying = state.isPlaying,
                hasMedia = state.hasMedia,
                progress =
                    if (state.durationMs > 0) {
                        state.positionMs / state.durationMs.toFloat()
                    } else {
                        0f
                    },
                compact = gui.compactPlayer,
                barOpacity = gui.barOpacity,
                onExpand = appState::expand,
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
            )
        }
        // Someone who came here to listen does not get a render queue in their navigation bar.
        // First run no longer asks, so everyone starts with everything and narrows it in Settings.
        val showsStudio = gui.intent.showsStudio
        // Set by Settings > Help to replay the tour after it has already been seen; the automatic
        // first-run showing is a function of the stored prefs instead, so this stays false there.
        var tutorialRunning by rememberSaveable { mutableStateOf(false) }
        // Offered once, to someone who has finished setup and has not turned the offer off.
        val offerTutorial = gui.setupDone && !gui.tutorialSeen && gui.tutorialOnFirstRun
        // Latched the moment the tour is offered, so that from then on its visibility depends on
        // `tutorialRunning` alone. Without this, ticking "don't show this next time" DURING the
        // tour clears `tutorialOnFirstRun`, which clears `offerTutorial`, which closes the tour
        // out from under the person who was reading it — the checkbox is about the NEXT install,
        // not this minute.
        LaunchedEffect(offerTutorial) { if (offerTutorial) tutorialRunning = true }
        val navEntries =
            GeodeDestination.entries
                .filter { it != GeodeDestination.STUDIO || showsStudio }
                .map { NavEntry(it, CrystalNavItem(stringResource(it.labelRes), it.icon)) }
        val destinationContent: @Composable (twoPane: Boolean) -> Unit = { twoPane ->
            PlaybackNoticeBanner(viewModel)
            when (appState.dest) {
                GeodeDestination.PLAYER ->
                    PlayerScreen(
                        viewModel,
                        onOpenSearch = appState::openSearch,
                        onExpand = appState::expand,
                        onOpenLibrary = { appState.navigateTo(GeodeDestination.LIBRARY) },
                    )
                GeodeDestination.LIBRARY ->
                    if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                LibraryScreen(onOpenSearch = appState::openSearch)
                            }
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .luminousHairline(MaterialTheme.colorScheme.primary),
                            )
                            Box(Modifier.weight(1f)) {
                                PlayerScreen(
                                    viewModel,
                                    onOpenSearch = appState::openSearch,
                                    onExpand = appState::expand,
                                    onOpenLibrary = {},
                                )
                            }
                        }
                    } else {
                        LibraryScreen(onOpenSearch = appState::openSearch)
                    }
                GeodeDestination.VISUALS ->
                    VisualsHub(
                        viewModel,
                        visualizerView,
                        onOpenNowPlaying = appState::expand,
                        liveBackdrop = gui.clearVisualsMenu && !appState.expanded && !onSecondScreen,
                    )
                GeodeDestination.STUDIO -> StudioRoute()
                GeodeDestination.SETTINGS -> SettingsScreen(viewModel, visualizerView, onStartTutorial = { tutorialRunning = true })
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CrystalBackground(
                Modifier.fillMaxSize(),
                reducedMotion = gui.reducedMotion || appState.expanded || appState.searching,
            )
            val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
            if (widthClass == WindowWidthSizeClass.COMPACT) {
                AppShellCompact(
                    navEntries = navEntries,
                    appState = appState,
                    gui = gui,
                    hasMedia = state.hasMedia,
                    miniPlayer = miniPlayer,
                    content = {
                        CompositionLocalProvider(
                            LocalReducedMotion provides (LocalReducedMotion.current || appState.expanded || appState.searching),
                        ) {
                            Box(
                                Modifier.fillMaxSize().jellySceneEntrance(
                                    appState.dest.ordinal,
                                    enabled = LocalThemePack.current.isJellyGlass &&
                                        (appState.dest == GeodeDestination.PLAYER || appState.dest == GeodeDestination.LIBRARY),
                                ),
                            ) {
                                destinationContent(false)
                            }
                        }
                    },
                )
            } else {
                AppShellExpanded(
                    navEntries = navEntries,
                    appState = appState,
                    hasMedia = state.hasMedia,
                    miniPlayer = miniPlayer,
                    content = {
                        CompositionLocalProvider(
                            LocalReducedMotion provides (LocalReducedMotion.current || appState.expanded || appState.searching),
                        ) {
                            Box(
                                Modifier.fillMaxSize().jellySceneEntrance(
                                    appState.dest.ordinal,
                                    enabled = LocalThemePack.current.isJellyGlass &&
                                        (appState.dest == GeodeDestination.PLAYER || appState.dest == GeodeDestination.LIBRARY),
                                ),
                            ) {
                                destinationContent(true)
                            }
                        }
                    },
                )
            }
            if (appState.searching) {
                SearchScreen(viewModel, onClose = appState::closeSearch)
            }
            val clipLabel = stringResource(R.string.crash_clip_label)
            crashText?.let { text ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.crash_dialog_title)) },
                    text = {
                        Text(
                            stringResource(R.string.crash_dialog_body, text.take(600)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    confirmButton = {
                        CrystalButton(onClick = {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText(clipLabel, text))
                        }) { Text(stringResource(R.string.action_copy)) }
                    },
                    dismissButton = {
                        CrystalButton(filled = false, onClick = {
                            java.io.File(context.filesDir, "crash-latest.txt").delete()
                            crashText = null
                        }) { Text(stringResource(R.string.action_dismiss)) }
                    },
                )
            }
            // Read by MainActivity to decide whether leaving the app should be treated as "enter
            // PiP" at all — a screen other than the fullscreen visualizer has nothing worth
            // shrinking into a window. Excludes the second-screen placeholder card too: when the
            // visuals are mirrored to a connected display, this phone shows only a "Showing on
            // <display>" notice, and shrinking that into a PiP window would carry nothing useful.
            LaunchedEffect(appState.expanded, onSecondScreen) {
                VisualizerPipCoordinator.visualizerShowing = appState.expanded && !onSecondScreen
            }
            // Keeps the platform's own auto-enter flag (API 31+) and the PiP window's play/pause
            // remote action in step with what is actually true, rather than only at the moment
            // someone taps the PiP button.
            LaunchedEffect(gui.autoEnterPip, state.isPlaying, appState.expanded) {
                context.findMainActivity()?.refreshPipParams()
            }
            if (appState.expanded) {
                VisualizerScreen(
                    viewModel = viewModel,
                    visualizerView = visualizerView,
                    externalDisplayName = if (onSecondScreen) externalDisplay?.name else null,
                    onCollapse = appState::collapse,
                    onOpenVisuals = {
                        appState.collapse()
                        appState.navigateTo(GeodeDestination.VISUALS)
                    },
                )
            }
            if ((!bootAnimEnabled || appState.bootDone) && !gui.safetyAcknowledged) {
                SafetyConsent(
                    onAcknowledge = { settingsViewModel.setGuiPrefs(gui.copy(safetyAcknowledged = true)) },
                )
            } else if ((!bootAnimEnabled || appState.bootDone) && !gui.setupDone) {
                // Setup runs only after the notice is acknowledged, and only until it is done.
                FirstRunGate(
                    onDone = {
                        settingsViewModel.setGuiPrefs(gui.copy(setupDone = true))
                        appState.navigateTo(gui.intent.landingDestination)
                    },
                )
            } else if (tutorialRunning) {
                // Offered once setup is out of the way, so the tour walks a real app with a real
                // library rather than a set of empty tabs.
                TutorialOverlay(
                    steps = TutorialStep.forNav(showsStudio),
                    dontShowAgain = !gui.tutorialOnFirstRun,
                    onDontShowAgainChange = { settingsViewModel.setGuiPrefs(gui.copy(tutorialOnFirstRun = !it)) },
                    onNavigate = appState::navigateTo,
                    onDismiss = {
                        tutorialRunning = false
                        settingsViewModel.setGuiPrefs(gui.copy(tutorialSeen = true))
                    },
                )
            }
            if (bootAnimEnabled && !appState.bootDone) {
                BootIntro(onDone = { appState.bootDone = true })
            }
        }
    }
}

@Composable
private fun AppShellCompact(
    navEntries: List<NavEntry>,
    appState: GeodeAppState,
    gui: GuiPrefs,
    hasMedia: Boolean,
    miniPlayer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (gui.playerPosition == PlayerPosition.TOP && hasMedia && !appState.onPlayer) {
                Box(Modifier.statusBarsPadding()) { miniPlayer() }
            }
        },
        bottomBar = {
            Column {
                if (gui.playerPosition == PlayerPosition.BOTTOM && !appState.onPlayer) miniPlayer()
                if (!tidal) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .luminousHairline(MaterialTheme.colorScheme.primary),
                    )
                }
                CrystalNavBar(
                    items = navEntries.map { it.item },
                    // A destination can be hidden while still being the current one — reaching
                    // Studio from a track menu, say. Fall back to the first tab rather than -1.
                    selected = navEntries.indexOfFirst { it.destination == appState.dest }.coerceAtLeast(0),
                    onSelect = { appState.navigateTo(navEntries[it].destination) },
                    opacity = gui.barOpacity,
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) { content() }
    }
}

@Composable
private fun AppShellExpanded(
    navEntries: List<NavEntry>,
    appState: GeodeAppState,
    hasMedia: Boolean,
    miniPlayer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (tidal) {
            TidalNavigationRail(navEntries, appState)
        } else {
            NavigationRail(containerColor = Color.Transparent) {
                navEntries.forEach { (destination, item) ->
                    NavigationRailItem(
                        selected = appState.dest == destination,
                        onClick = { appState.navigateTo(destination) },
                        icon = { StoneIconArt(item.icon, item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .luminousHairline(MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f)) {
            if (hasMedia && !appState.onPlayer) miniPlayer()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun TidalNavigationRail(
    navEntries: List<NavEntry>,
    appState: GeodeAppState,
) {
    BoxWithConstraints(Modifier.width(112.dp).fillMaxHeight()) {
        val compact = maxHeight < 520.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .crystalPanel(
                    0.56f,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    corner = 28.dp,
                    glowStrength = 0.45f,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 16.dp)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp),
        ) {
            if (!compact) {
                CrystalGem(MaterialTheme.colorScheme.primary, size = 10.dp)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
            }
            navEntries.forEach { (destination, item) ->
                TidalNavigationPebble(
                    item = item,
                    selected = appState.dest == destination,
                    onSelect = { appState.navigateTo(destination) },
                    modifier = Modifier.fillMaxWidth(),
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String?,
    isPlaying: Boolean,
    hasMedia: Boolean,
    progress: Float,
    barOpacity: Float,
    compact: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (!hasMedia) return
    if (LocalThemePack.current.isJellyGlass) {
        TidalMiniPlayer(title, isPlaying, progress, barOpacity, compact, onExpand, onPlayPause, onPrevious, onNext)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .crystalPanel(
                barOpacity,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                corner = 0.dp,
                glowStrength = 0.6f,
                facets = 0.8f,
            ).clickable(onClick = onExpand),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                null,
                Modifier
                    .size(if (compact) 20.dp else 28.dp)
                    .softGlow(MaterialTheme.colorScheme.primary, 8.dp, if (isPlaying) 1f else 0.35f),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                title ?: stringResource(R.string.mini_player_idle),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onPrevious) { StoneIconArt(StoneIcon.PREVIOUS, stringResource(R.string.action_previous)) }
            IconButton(onClick = onPlayPause) {
                StoneIconArt(
                    if (isPlaying) StoneIcon.PAUSE else StoneIcon.PLAY,
                    stringResource(R.string.action_play_pause),
                )
            }
            IconButton(onClick = onNext) { StoneIconArt(StoneIcon.NEXT, stringResource(R.string.action_next)) }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )
    }
}

@Composable
private fun TidalMiniPlayer(
    title: String?,
    isPlaying: Boolean,
    progress: Float,
    barOpacity: Float,
    compact: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .crystalPanel(
                barOpacity,
                cs.surfaceVariant,
                cs.primary,
                corner = 24.dp,
                glowStrength = if (isPlaying) 0.8f else 0.45f,
                facets = 0.35f,
                sheen = cs.secondary,
                readable = false,
            ).clickable(onClick = onExpand),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (compact) 28.dp else 36.dp), contentAlignment = Alignment.Center) {
                StoneSurfaceArt(
                    StoneComponent.ICON_BUTTON,
                    if (isPlaying) StoneState.SELECTED else StoneState.DEFAULT,
                    Modifier.matchParentSize(),
                    reducedMotion = LocalReducedMotion.current,
                )
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    Modifier.size(if (compact) 16.dp else 20.dp),
                    tint = cs.primary,
                )
            }
            Text(
                title ?: stringResource(R.string.mini_player_idle),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).jellyMatteSheet(8.dp).padding(4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalFontColor.current ?: cs.onSurface,
            )
            TidalMiniTransport(StoneIcon.PREVIOUS, stringResource(R.string.action_previous), onPrevious)
            TidalMiniTransport(
                if (isPlaying) StoneIcon.PAUSE else StoneIcon.PLAY,
                stringResource(R.string.action_play_pause),
                onPlayPause,
            )
            TidalMiniTransport(StoneIcon.NEXT, stringResource(R.string.action_next), onNext)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
            color = cs.primary,
            trackColor = cs.primary.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
    }
}

@Composable
private fun TidalMiniTransport(
    icon: StoneIcon,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = rememberStoneInteraction()
    val state = rememberStoneState(interaction)
    val reducedMotion = LocalReducedMotion.current
    Box(
        Modifier
            .size(48.dp)
            .stonePress(interaction, reducedMotion = reducedMotion)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StoneSurfaceArt(StoneComponent.ICON_BUTTON, state, Modifier.matchParentSize(), reducedMotion = reducedMotion)
        StoneIconArt(icon, description, tint = LocalFontColor.current ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    visualizerView: VisualizerView,
    onStartTutorial: () -> Unit,
) {
    var showExport by rememberSaveable { mutableStateOf(false) }
    val tidal = LocalThemePack.current.isJellyGlass
    Column(Modifier.fillMaxSize()) {
        if (tidal) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .crystalPanel(
                        0.38f,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        corner = 28.dp,
                        glowStrength = 0.45f,
                        facets = 0.3f,
                    ).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    StoneSurfaceArt(
                        StoneComponent.ICON_BUTTON,
                        StoneState.SELECTED,
                        Modifier.matchParentSize(),
                        reducedMotion = LocalReducedMotion.current,
                    )
                    StoneIconArt(StoneIcon.SETTINGS, stringResource(R.string.nav_settings))
                }
                Column {
                    CrystalOverline(stringResource(R.string.app_name))
                    GlowTitle(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
                }
            }
        } else {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                CrystalOverline(stringResource(R.string.app_name))
                GlowTitle(stringResource(R.string.nav_settings))
            }
        }
        AppSettingsTab(
            viewModel,
            exportOpen = showExport,
            onOpenExport = { showExport = true },
            onStartTutorial = onStartTutorial,
        )
    }
    if (showExport) {
        ExportHost(viewModel, visualizerView) { showExport = false }
    }
}

private data class SearchTrackRow(
    val uri: String,
    val title: String,
    val subtitle: String,
    val fields: List<String>,
    val fromDevice: Boolean,
)

@Composable
fun SearchScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = geodeViewModel()
    val libraryViewModel: LibraryViewModel = geodeViewModel()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    val gui by settingsViewModel.guiPrefs.collectAsStateWithLifecycle()
    val library by libraryViewModel.library.collectAsStateWithLifecycle()
    val viz by viewModel.vizState.collectAsStateWithLifecycle()
    val deviceTracks by libraryViewModel.deviceTracks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { libraryViewModel.refreshDeviceTracks() }
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(250)
        debounced = query
    }
    val dismiss = rememberPredictiveDismiss(onDismiss = onClose)

    val terms = remember(debounced) { SearchMatcher.terms(debounced) }
    val trackResults =
        remember(terms, deviceTracks, library.tracks) {
            val candidates =
                deviceTracks.map { t ->
                    SearchTrackRow(
                        uri = t.uri,
                        title = t.title,
                        subtitle = listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                        fields = listOf(t.title, t.artist, t.album, t.folder),
                        fromDevice = true,
                    )
                } +
                    library.tracks.map { t ->
                        SearchTrackRow(
                            uri = t.uri,
                            title = t.title,
                            subtitle = listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                            fields = listOf(t.title, t.artist, t.album, t.genre),
                            fromDevice = false,
                        )
                    }
            SearchMatcher.filterTracks(
                terms = terms,
                items = candidates,
                uriOf = { it.uri },
                fieldsOf = { it.fields },
                preferred = { it.fromDevice },
            )
        }
    val playlistResults =
        remember(terms, library.playlists) {
            library.playlists.filter { SearchMatcher.matches(terms, listOf(it.name)) }
        }
    val presetResults =
        remember(terms, viz.presets) {
            viz.presets.filter { SearchMatcher.matches(terms, listOf(it.name)) }
        }

    Box(Modifier.fillMaxSize().dismissTransform(dismiss)) {
        CrystalBackground(Modifier.fillMaxSize(), reducedMotion = gui.reducedMotion)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    shape = crystalShardShape(14.dp, 5.dp),
                )
                IconButton(onClick = onClose) { StoneIconArt(StoneIcon.CLOSE, stringResource(R.string.search_close)) }
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (terms.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_hint),
                            Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (trackResults.isNotEmpty()) {
                        item {
                            CrystalOverline(
                                stringResource(R.string.search_heading_tracks, trackResults.size),
                                Modifier.padding(top = 8.dp),
                            )
                        }
                        items(trackResults, key = { "t:${it.uri}" }) { t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.playFrom(
                                            trackResults.map { r -> QueueTrack(r.uri, r.title, r.subtitle) },
                                            t.uri,
                                        )
                                        onClose()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (t.subtitle.isNotBlank()) {
                                        Text(
                                            t.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.enqueue(t.uri) }) {
                                    StoneIconArt(StoneIcon.QUEUE, stringResource(R.string.action_add_to_queue))
                                }
                            }
                        }
                    }
                    if (playlistResults.isNotEmpty()) {
                        item {
                            CrystalOverline(
                                stringResource(R.string.search_heading_playlists, playlistResults.size),
                                Modifier.padding(top = 8.dp),
                            )
                        }
                        items(playlistResults) { pl ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        libraryViewModel.playPlaylist(pl.name)
                                        onClose()
                                    }.padding(vertical = 8.dp),
                            ) {
                                Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(
                                        R.plurals.track_count,
                                        pl.trackUris.size,
                                        pl.trackUris.size,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (presetResults.isNotEmpty()) {
                        item {
                            CrystalOverline(
                                stringResource(R.string.search_heading_presets, presetResults.size),
                                Modifier.padding(top = 8.dp),
                            )
                        }
                        items(presetResults) { p ->
                            Text(
                                p.name,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.applyPreset(p)
                                        onClose()
                                    }.padding(vertical = 8.dp),
                            )
                        }
                    }
                    if (trackResults.isEmpty() && playlistResults.isEmpty() && presetResults.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_no_results, debounced),
                                Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackNoticeBanner(viewModel: PlayerViewModel) {
    val notice by viewModel.playbackNotice.collectAsStateWithLifecycle()
    val message = notice ?: return
    val dismissDescription = stringResource(R.string.notice_dismiss_description)

    LaunchedEffect(message) {
        kotlinx.coroutines.delay(NOTICE_VISIBLE_MS)
        viewModel.clearPlaybackNotice()
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { viewModel.clearPlaybackNotice() }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.notice_dismiss),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .clickable { viewModel.clearPlaybackNotice() }
                        .semantics { contentDescription = dismissDescription }
                        .padding(8.dp),
            )
        }
    }
}

private const val NOTICE_VISIBLE_MS = 8_000L
