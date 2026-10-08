package dev.geode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.geode.R
import dev.geode.ui.lake.LakeMaterials
import dev.geode.ui.lake.LakeScreenHeader
import dev.geode.ui.lake.lakeArtworkFrame
import dev.geode.ui.lake.lakeFrostedPanel
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt
import dev.geode.ui.theme.StoneSurfaceArt
import dev.geode.ui.theme.isJellyGlass
import dev.geode.ui.theme.isLivingLake
import dev.geode.ui.theme.rememberStoneInteraction
import dev.geode.ui.theme.rememberStoneState
import dev.geode.ui.theme.stonePress
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenSearch: () -> Unit,
    onExpand: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenNavigation: () -> Unit = onExpand,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    val livingLake = LocalThemePack.current.isLivingLake
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viz by viewModel.vizState.collectAsStateWithLifecycle()
    val mic by viewModel.micState.collectAsStateWithLifecycle()
    val external by viewModel.externalAudio.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val abLoop by viewModel.abLoop.collectAsStateWithLifecycle()
    val autoMode by viewModel.autoMode.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val tick by viewModel.historyTick.collectAsStateWithLifecycle()
    val sleepRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val canShuffle = remember(tick) { viewModel.recentlyPlayed().isNotEmpty() }
    var showQueue by rememberSaveable { mutableStateOf(true) }
    val upNext = remember(queue) { queue.tracks.drop(queue.index + 1).take(3) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (tidal) 10.dp else 16.dp),
    ) {
        item {
            if (livingLake) {
                LakeScreenHeader(
                    title = stringResource(R.string.nav_player),
                    subtitle = stringResource(R.string.ui2_screen_player_subtitle),
                ) {
                    PlayerTransportButton(StoneIcon.SEARCH, stringResource(R.string.action_search), onOpenSearch)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (tidal) {
                            Column(Modifier.jellyMatteSheet(12.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    stringResource(R.string.app_name).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 5.sp),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.nav_player),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            CrystalOverline(stringResource(R.string.app_name))
                            GlowTitle(stringResource(R.string.nav_player))
                        }
                    }
                    PlayerTransportButton(StoneIcon.SEARCH, stringResource(R.string.action_search), onOpenSearch)
                }
            }
        }

        item {
            PlayerHero(
                viewModel = viewModel,
                state = state,
                styleLabel = sceneDisplayLabel(viz.sceneId),
                micActive = mic.active,
                external = external,
                favourites = favourites,
                canResume = canShuffle,
                onExpand = onExpand,
                onOpenLibrary = onOpenLibrary,
                onOpenNavigation = onOpenNavigation,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            TransportCard(
                viewModel = viewModel,
                state = state,
                waveform = waveform,
                abLoop = abLoop,
                autoMode = autoMode,
                queueSize = queue.tracks.size,
                queueOpen = showQueue,
                onToggleQueue = { showQueue = !showQueue },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            LiveSpectrum(
                viewModel,
                live = state.isPlaying || mic.active || external.active,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(
                            if (livingLake) {
                                Modifier.lakeFrostedPanel(corner = 20.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                            } else {
                                Modifier
                            },
                        ).height(if (livingLake) 32.dp else 44.dp),
            )
        }

        if (showQueue && upNext.isNotEmpty()) {
            item {
                QueuePreview(
                    upNext = upNext,
                    onExpand = onExpand,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            QuickActions(
                viewModel = viewModel,
                micActive = mic.active,
                external = external,
                sleepRunning = sleepRemainingMs != null,
                canShuffle = canShuffle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerHero(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    styleLabel: String,
    micActive: Boolean,
    external: ExternalAudioState,
    favourites: Set<String>,
    canResume: Boolean,
    onExpand: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    val livingLake = LocalThemePack.current.isLivingLake
    val uri = remember(state.title, state.artist) { viewModel.currentTrackUri() }
    val foreign = external.active
    val foreignTrack = external.nowPlaying?.takeIf { it.title.isNotBlank() }
    val hasSource = foreign || micActive || state.hasMedia
    val isFavourite = uri != null && uri in favourites
    val localArtwork = state.hasMedia && !foreign && !micActive
    val navigationDescription = stringResource(R.string.ui2_screen_open_navigation)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (tidal) {
                    Modifier
                } else {
                    Modifier.crystalPanel(
                        0.42f,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        corner = 24.dp,
                        glowStrength = if (state.isPlaying || foreign || micActive) 1.2f else 0.7f,
                    )
                },
            ).then(if (livingLake) Modifier else Modifier.clickable(enabled = hasSource, onClick = onExpand))
            .padding(if (tidal) 0.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (livingLake) {
            // The scoped GL world supplies the mineral volume and its water reflection here.
            // A transparent layout reserve keeps a second artwork/shader hero from covering it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clickable(role = Role.Button, onClick = onOpenNavigation)
                    .semantics { contentDescription = navigationDescription },
            )
        } else if (tidal) {
            TidalPlayerArtwork(
                viewModel,
                state.isPlaying || foreign || micActive,
                artworkUri = if (localArtwork) uri else null,
            )
        } else {
            TrackArtwork(
                if (foreign || micActive) null else uri,
                Modifier.fillMaxWidth().aspectRatio(1f),
                corner = 18.dp,
            )
        }
        Row(
            Modifier
                .then(
                    if (livingLake) Modifier.clickable(enabled = hasSource, role = Role.Button, onClick = onExpand) else Modifier,
                ).then(
                    if (tidal) {
                        Modifier
                            .crystalPanel(0.62f, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, corner = 24.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if ((tidal || livingLake) && localArtwork) {
                TrackArtwork(
                    uri,
                    Modifier
                        .size(if (livingLake) 64.dp else 48.dp)
                        .then(if (livingLake) Modifier.lakeArtworkFrame() else Modifier),
                    corner = 14.dp,
                )
                Box(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                val sourceStatus =
                    when {
                        foreign -> external.nowPlaying?.appLabel ?: stringResource(R.string.source_other_apps)
                        micActive -> stringResource(R.string.source_live_input)
                        state.isPlaying -> stringResource(R.string.state_now_playing)
                        state.hasMedia -> stringResource(R.string.state_paused)
                        else -> stringResource(R.string.state_nothing_playing)
                    }
                if (livingLake) {
                    Text(
                        sourceStatus.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CrystalOverline(sourceStatus)
                }
                Text(
                    when {
                        foreign -> foreignTrack?.title ?: stringResource(R.string.title_whatever_is_playing)
                        micActive -> stringResource(R.string.title_the_room)
                        state.hasMedia -> state.title ?: stringResource(R.string.title_untitled)
                        else -> stringResource(R.string.title_pick_something)
                    },
                    style =
                        (if (tidal) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                            .copy(fontWeight = FontWeight.SemiBold),
                    maxLines = if (tidal) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        external.refusedByApp ->
                            stringResource(
                                R.string.subtitle_capture_refused,
                                external.refusingApp
                                    ?: stringResource(R.string.subtitle_capture_refused_unknown_app),
                            )
                        foreign ->
                            foreignTrack?.artist?.ifBlank { null }
                                ?: stringResource(R.string.subtitle_captured_from_another_app)
                        micActive -> stringResource(R.string.subtitle_microphone_hears)
                        state.hasMedia ->
                            state.artist?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.subtitle_unknown_artist)
                        else -> stringResource(R.string.subtitle_nothing_playing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (external.refusedByApp) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.hasMedia && !foreign) {
                PlayerTransportButton(
                    StoneIcon.FAVORITE,
                    stringResource(if (isFavourite) R.string.action_favourite_remove else R.string.action_favourite_add),
                    onClick = { viewModel.toggleFavourite() },
                    selected = isFavourite,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SceneChip(styleLabel)
            Box(Modifier.weight(1f))
            if (foreign) {
                CrystalButton(filled = false, compact = true, onClick = viewModel::stopExternalAudio) {
                    Text(stringResource(R.string.action_stop_capture))
                }
            }
        }
        if (!hasSource) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CrystalButton(enabled = canResume, onClick = viewModel::resumeLastPlayed) {
                    Text(stringResource(R.string.action_resume_last_played))
                }
                CrystalButton(filled = false, onClick = onOpenLibrary) { Text(stringResource(R.string.action_open_library)) }
            }
        }
    }
}

@Composable
private fun SceneChip(label: String) {
    Box(
        Modifier
            .crystalPanel(
                0.25f,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                corner = 12.dp,
                glowStrength = 0.4f,
            ).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = accentTextColor(),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportCard(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    waveform: FloatArray?,
    abLoop: AbLoop?,
    autoMode: Int,
    queueSize: Int,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    val livingLake = LocalThemePack.current.isLivingLake
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (livingLake) {
                    Modifier.lakeFrostedPanel()
                } else if (tidal) {
                    Modifier
                } else {
                    Modifier.crystalPanel(
                        0.35f,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        corner = 24.dp,
                        glowStrength = 0.8f,
                    )
                },
            ).padding(horizontal = if (tidal && !livingLake) 0.dp else 14.dp, vertical = 10.dp),
    ) {
        if (livingLake) {
            WaveformSeekBar(
                waveform = waveform,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                loopStartMs = abLoop?.startMs,
                loopEndMs = abLoop?.endMs,
                onSeek = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth().height(LakeMaterials.MinimumTouchTarget),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatClock(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatClock(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (tidal) {
                            Modifier
                                .crystalPanel(0.6f, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, corner = 32.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(formatClock(state.positionMs), style = MaterialTheme.typography.labelSmall)
                WaveformSeekBar(
                    waveform = waveform,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    loopStartMs = abLoop?.startMs,
                    loopEndMs = abLoop?.endMs,
                    onSeek = viewModel::seekTo,
                    modifier = Modifier.weight(1f).height(if (tidal) 48.dp else 40.dp),
                )
                Text(formatClock(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = if (tidal) 6.dp else 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 5,
        ) {
            PlayerTransportButton(
                StoneIcon.SHUFFLE,
                stringResource(R.string.action_shuffle),
                viewModel::toggleShuffle,
                selected = state.shuffle,
            )
            PlayerTransportButton(
                StoneIcon.PREVIOUS,
                stringResource(R.string.action_previous),
                viewModel::previous,
                enabled = state.hasMedia,
            )
            if (tidal) {
                PlayerTransportButton(
                    if (state.isPlaying) StoneIcon.PAUSE else StoneIcon.PLAY,
                    stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                    viewModel::togglePlayPause,
                    enabled = state.hasMedia,
                    selected = state.isPlaying,
                    large = true,
                )
            } else {
                CrystalPlayButton(
                    icon = if (state.isPlaying) StoneIcon.PAUSE else StoneIcon.PLAY,
                    contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                    onClick = viewModel::togglePlayPause,
                    enabled = state.hasMedia,
                )
            }
            PlayerTransportButton(
                StoneIcon.NEXT,
                stringResource(R.string.action_next),
                viewModel::next,
                enabled = state.hasMedia,
            )
            PlayerTransportButton(
                StoneIcon.REPEAT,
                stringResource(R.string.action_repeat),
                viewModel::cycleRepeatMode,
                selected = state.repeatMode != Player.REPEAT_MODE_OFF,
            )
        }
        if (livingLake) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)))
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 3,
        ) {
            TextButton(
                onClick = viewModel::cycleAbLoop,
                enabled = state.hasMedia,
                modifier = Modifier.heightIn(min = LakeMaterials.MinimumTouchTarget),
            ) {
                Text(
                    when {
                        abLoop == null -> stringResource(R.string.ab_loop_idle)
                        abLoop.endMs == null -> stringResource(R.string.ab_loop_set_b)
                        else -> stringResource(R.string.ab_loop_looping)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (abLoop != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            TextButton(onClick = viewModel::cycleAutoMode, modifier = Modifier.heightIn(min = LakeMaterials.MinimumTouchTarget)) {
                Text(
                    when (autoMode) {
                        1 -> stringResource(R.string.auto_random)
                        2 -> stringResource(R.string.auto_smart)
                        3 -> stringResource(R.string.auto_sections)
                        else -> stringResource(R.string.auto_off)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggleQueue, modifier = Modifier.heightIn(min = LakeMaterials.MinimumTouchTarget)) {
                Text(
                    if (queueSize > 1) {
                        stringResource(R.string.queue_with_count, queueSize)
                    } else {
                        stringResource(R.string.queue)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (queueOpen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun PlayerTransportButton(
    icon: StoneIcon,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    large: Boolean = false,
) {
    val tint =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val toggle = icon == StoneIcon.SHUFFLE || icon == StoneIcon.REPEAT || icon == StoneIcon.FAVORITE
    val selection = if (toggle) Modifier.semantics { this.selected = selected } else Modifier
    if (!LocalThemePack.current.isJellyGlass) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = selection,
        ) { StoneIconArt(icon, description, tint = tint) }
        return
    }
    val interaction = rememberStoneInteraction()
    val state = rememberStoneState(interaction, enabled, selected)
    Box(
        Modifier
            .size(
                if (LocalThemePack.current.isLivingLake) {
                    if (large) 68.dp else LakeMaterials.MinimumTouchTarget
                } else {
                    if (large) 76.dp else 52.dp
                },
            )
            .then(selection)
            .stonePress(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StoneSurfaceArt(StoneComponent.ICON_BUTTON, state, Modifier.matchParentSize())
        StoneIconArt(
            icon,
            description,
            Modifier.size(if (large) 32.dp else 22.dp),
            tint = (LocalFontColor.current ?: tint).copy(alpha = if (enabled) 1f else 0.42f),
        )
    }
}

@Composable
private fun QueuePreview(
    upNext: List<QueueTrack>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val livingLake = LocalThemePack.current.isLivingLake
    Column(
        modifier
            .fillMaxWidth()
            .crystalPanel(
                0.3f,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                corner = 20.dp,
                glowStrength = 0.5f,
            ).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val untitled = stringResource(R.string.title_untitled)
        if (livingLake) {
            Text(
                stringResource(R.string.queue_up_next).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CrystalOverline(stringResource(R.string.queue_up_next))
        }
        upNext.forEach { t ->
            Text(
                t.title.ifBlank { untitled },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LakeMaterials.MinimumTouchTarget)
                    .clickable(onClick = onExpand)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onExpand) {
                Text(stringResource(R.string.action_open_queue), style = MaterialTheme.typography.labelMedium, color = accentTextColor())
            }
        }
    }
}

@Composable
private fun LiveSpectrum(
    viewModel: PlayerViewModel,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val bars by produceState(initialValue = FloatArray(BARS), live) {
        driveSpectrum(live, { viewModel.features.value.bands }) { value = it }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    androidx.compose.foundation.Canvas(modifier) {
        val gap = size.width / (BARS * 6f)
        val barWidth = (size.width - gap * (BARS - 1)) / BARS
        val brush =
            Brush.verticalGradient(
                listOf(secondary.copy(alpha = 0.95f), primary.copy(alpha = 0.75f)),
            )
        for (i in 0 until BARS) {
            val v = bars.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val h = (size.height * (0.06f + 0.94f * v)).coerceAtLeast(2f)
            drawRoundRect(
                brush = brush,
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(i * (barWidth + gap), size.height - h),
                size =
                    androidx.compose.ui.geometry
                        .Size(barWidth, h),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(barWidth / 2f),
            )
        }
    }
}

internal const val BARS = 24

internal const val SPECTRUM_TICK_MS = 50L

internal suspend fun driveSpectrum(
    live: Boolean,
    bands: () -> FloatArray,
    emit: (FloatArray) -> Unit,
) {
    if (!live) {
        emit(FloatArray(BARS))
        return
    }
    val smoothed = FloatArray(BARS)
    while (true) {
        val current = bands()
        for (i in 0 until BARS) {
            val target =
                if (current.isEmpty()) {
                    0f
                } else {
                    val from = i * current.size / BARS
                    val to = ((i + 1) * current.size / BARS).coerceAtLeast(from + 1)
                    var acc = 0f
                    for (b in from until minOf(to, current.size)) acc += current[b]
                    acc / (minOf(to, current.size) - from)
                }
            smoothed[i] =
                if (target > smoothed[i]) target else smoothed[i] + (target - smoothed[i]) * 0.35f
        }
        emit(smoothed.copyOf())
        delay(SPECTRUM_TICK_MS)
    }
}

@Composable
private fun QuickActions(
    viewModel: PlayerViewModel,
    micActive: Boolean,
    external: ExternalAudioState,
    sleepRunning: Boolean,
    canShuffle: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermission =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { granted -> if (granted) viewModel.setMicEnabled(true) }
    val projectionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .StartActivityForResult(),
        ) { result ->
            val data = result.data
            if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
                dev.geode.audio.PlaybackCaptureService
                    .start(context, result.resultCode, data)
            } else {
                viewModel.noteExternalAudioConsentDenied()
            }
        }
    val capturePermissions =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestMultiplePermissions(),
        ) { granted ->
            if (granted[android.Manifest.permission.RECORD_AUDIO] != false) {
                viewModel.noteExternalAudioConsentPending()
                projectionLauncher.launch(
                    context
                        .getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        .createScreenCaptureIntent(),
                )
            } else {
                viewModel.noteExternalAudioConsentDenied()
            }
        }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp),
    ) {
        item {
            QuickAction(
                StoneIcon.MICROPHONE,
                stringResource(if (micActive) R.string.quick_room_on else R.string.source_live_input),
                active = micActive,
            ) {
                if (micActive) {
                    viewModel.setMicEnabled(false)
                } else if (viewModel.hasMicPermission()) {
                    viewModel.setMicEnabled(true)
                } else {
                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        if (external.supported) {
            item {
                QuickAction(
                    Icons.Filled.Cast,
                    stringResource(if (external.active) R.string.quick_capturing else R.string.source_other_apps),
                    active = external.active,
                ) {
                    if (external.active) {
                        viewModel.stopExternalAudio()
                    } else {
                        capturePermissions.launch(
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    android.Manifest.permission.RECORD_AUDIO,
                                    android.Manifest.permission.POST_NOTIFICATIONS,
                                )
                            } else {
                                arrayOf(android.Manifest.permission.RECORD_AUDIO)
                            },
                        )
                    }
                }
            }
        }
        item {
            QuickAction(
                Icons.Filled.Bedtime,
                stringResource(if (sleepRunning) R.string.quick_sleep_on else R.string.quick_sleep_30m),
                active = sleepRunning,
            ) {
                if (sleepRunning) viewModel.cancelSleepTimer() else viewModel.startSleepTimer(30)
            }
        }
        item {
            QuickAction(StoneIcon.SHUFFLE, stringResource(R.string.quick_shuffle_all), enabled = canShuffle) {
                viewModel.shuffleAllHistory()
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: StoneIcon,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) = QuickActionShell(label, enabled, active, onClick) { StoneIconArt(icon, null, Modifier.size(22.dp), tint = it) }

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) = QuickActionShell(label, enabled, active, onClick) { Icon(icon, null, Modifier.size(22.dp), tint = it) }

@Composable
private fun QuickActionShell(
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tidal = LocalThemePack.current.isJellyGlass
    val interaction = rememberStoneInteraction()
    val tint =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    Column(
        Modifier
            .width(84.dp)
            .defaultMinSize(minHeight = 64.dp)
            .stonePress(interaction)
            .crystalPanel(
                if (active) 0.5f else 0.28f,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                corner = 18.dp,
                glowStrength = if (active) 1.1f else 0.45f,
                prismatic = active,
            ).clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon(tint)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = if (tidal) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
