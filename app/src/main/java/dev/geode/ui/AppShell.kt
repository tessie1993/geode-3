package dev.geode.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.geode.R
import dev.geode.analysis.SearchMatcher
import dev.geode.data.BootAnimationStore
import dev.geode.data.GeodePrefsFiles
import dev.geode.render.VisualizerView
import dev.geode.ui.lake.LivingLakeShell
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt
import dev.geode.ui.theme.isLivingLake
import dev.geode.ui.world.NativeWorldBackdrop
import dev.geode.ui.world.WorldLensAnchor
import dev.geode.ui.world.WorldQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val CRASH_REPORT_MAX_BYTES = 64 * 1024

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
    val introShowing = bootAnimEnabled && !appState.bootDone
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportRunning by remember {
        dev.geode.export.ExportRun.state
            .map { it.running }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
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
    PredictiveBackHandler(enabled = !appState.expanded && !appState.searching && appState.canNavigateBack) { events ->
        try {
            events.collect { appState.updateBackProgress(it.progress) }
            appState.navigateBack()
        } catch (cancelled: CancellationException) {
            appState.updateBackProgress(0f)
            throw cancelled
        }
    }
    CrystalMaterialTheme(
        pack = effectiveTheme,
        gui = gui,
        motionObscured = appState.expanded || appState.searching,
    ) {
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
        val destinations =
            GeodeDestination.entries
                .filter { it != GeodeDestination.STUDIO || showsStudio }
        val destinationState = rememberSaveableStateHolder()
        val destinationContent: @Composable (twoPane: Boolean) -> Unit = { twoPane ->
            destinationState.SaveableStateProvider(appState.dest.name) {
                Column(Modifier.fillMaxSize()) {
                    PlaybackNoticeBanner(viewModel)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (appState.dest) {
                            GeodeDestination.PLAYER ->
                                PlayerScreen(
                                    viewModel,
                                    onOpenSearch = appState::openSearch,
                                    onExpand = appState::expand,
                                    onOpenLibrary = { appState.navigateTo(GeodeDestination.LIBRARY) },
                                    onOpenNavigation = appState::openOrbit,
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
                                                onOpenNavigation = appState::openOrbit,
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
                                    ownsVisualizerSurface = !appState.expanded && !appState.searching && !onSecondScreen,
                                )
                            GeodeDestination.STUDIO -> StudioRoute()
                            GeodeDestination.SETTINGS ->
                                SettingsScreen(viewModel, visualizerView, onStartTutorial = {
                                    tutorialRunning =
                                        true
                                })
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            var lenses by remember { mutableStateOf(emptyList<WorldLensAnchor>()) }
            var worldError by remember { mutableStateOf<String?>(null) }
            val worldActive =
                LakeWorldVisibility.isActive(
                    destination = appState.dest,
                    expanded = appState.expanded,
                    searching = appState.searching,
                    onboarding = (!introShowing && (!gui.safetyAcknowledged || !gui.setupDone)) || tutorialRunning,
                    errorDialog = crashText != null,
                    secondScreen = onSecondScreen,
                    orbitOpen = appState.orbitOpen,
                    exporting = exportRunning,
                )
            if (effectiveTheme.isLivingLake) {
                LakeWorldLayer(
                    viewModel = viewModel,
                    appState = appState,
                    active = worldActive,
                    lenses = lenses,
                    onRenderError = { worldError = it },
                )
            } else {
                CrystalBackground(Modifier.fillMaxSize())
            }
            if (!introShowing) {
                LivingLakeShell(
                    appState = appState,
                    destinations = destinations,
                    hasMedia = state.hasMedia,
                    title = listOfNotNull(state.title, state.artist?.takeIf { it.isNotBlank() }).joinToString(" \u2014 ").ifBlank { null },
                    isPlaying = state.isPlaying,
                    progress = if (state.durationMs > 0) state.positionMs / state.durationMs.toFloat() else 0f,
                    onPlayPause = viewModel::togglePlayPause,
                    onLensesChanged = { lenses = it },
                    worldAvailable = effectiveTheme.isLivingLake && worldActive && worldError == null,
                    content = { twoPane ->
                        if (!appState.expanded && !appState.searching) destinationContent(twoPane)
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

/** Live scalars go directly to the world host, without recomposing the native content tree. */
@Composable
private fun LakeWorldLayer(
    viewModel: PlayerViewModel,
    appState: GeodeAppState,
    active: Boolean,
    lenses: List<WorldLensAnchor>,
    onRenderError: (String?) -> Unit,
) {
    NativeWorldBackdrop(
        modifier = Modifier.fillMaxSize(),
        destination = appState.dest,
        featureSource = viewModel.features,
        reducedMotion = LocalReducedMotion.current,
        active = active,
        orbitExpanded = appState.orbitOpen,
        lenses = lenses,
        quality = if (appState.onPlayer || appState.orbitOpen) WorldQuality.BALANCED else WorldQuality.LOW,
        backProgress = appState.predictiveBackProgress,
        backDestination = appState.previousDestination.takeUnless { appState.orbitOpen },
        onRenderError = onRenderError,
    )
}

@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    visualizerView: VisualizerView,
    onStartTutorial: () -> Unit,
) {
    var showExport by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (!LocalThemePack.current.isLivingLake) {
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
        CrystalBackground(Modifier.fillMaxSize())
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
