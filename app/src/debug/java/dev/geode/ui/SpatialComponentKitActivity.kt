package dev.geode.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.geode.ui.theme.LocalReducedMotion
import dev.geode.ui.theme.StoneComponent
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt
import dev.geode.ui.theme.StoneState
import dev.geode.ui.theme.StoneSurfaceArt
import dev.geode.ui.theme.ThemePack
import dev.geode.ui.theme.ThemePackCatalog
import dev.geode.ui.theme.rememberStoneInteraction
import dev.geode.ui.theme.rememberStoneState
import dev.geode.ui.theme.stonePress

/** Debug-only material evidence. The kit never acquires app state, audio, GL or a service. */
class SpatialComponentKitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requested = ThemePackCatalog.bySlug(intent.getStringExtra(THEME_EXTRA)).slug
        setContent {
            var themeSlug by rememberSaveable { mutableStateOf(requested) }
            val pack = ThemePackCatalog.bySlug(themeSlug)
            CrystalMaterialTheme(pack, GuiPrefs()) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SpatialComponentKit(pack, onTheme = { themeSlug = it.slug })
                }
            }
        }
    }

    companion object {
        const val THEME_EXTRA = "theme_slug"
    }
}

@Composable
private fun SpatialComponentKit(
    pack: ThemePack,
    onTheme: (ThemePack) -> Unit,
) {
    var interactions by rememberSaveable { mutableIntStateOf(0) }
    var capsuleSelected by rememberSaveable { mutableStateOf(true) }
    var roundSelected by rememberSaveable { mutableStateOf(true) }
    val reducedMotion = LocalReducedMotion.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CrystalBackground(Modifier.fillMaxSize(), reducedMotion = reducedMotion)
        val orbHeight = (maxHeight.value * 0.22f).coerceIn(96f, 160f).dp
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Component kit",
                Modifier.jellyMatteSheet(10.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            KitThemePicker(pack, onTheme)
            TidalWaterOrb(Modifier.fillMaxWidth().height(orbHeight), reducedMotion = reducedMotion)
            Column(
                Modifier.fillMaxWidth().jellyMatteSheet(16.dp).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Matte reading panel", style = MaterialTheme.typography.titleSmall)
                Text("Native text beside floating waterglass.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrystalButton(onClick = { interactions++ }, modifier = Modifier.weight(1f), compact = true) {
                    Text("Press capsule")
                }
                KitSelectedCapsule(
                    selected = capsuleSelected,
                    onClick = {
                        capsuleSelected = !capsuleSelected
                        interactions++
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KitPressedCapsule(Modifier.weight(1f))
                CrystalButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false, compact = true) {
                    Text("Disabled capsule")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                KitRoundLabel("Idle") {
                    CrystalPlayButton(StoneIcon.PLAY, "Press round button", onClick = { interactions++ })
                }
                KitRoundLabel("Pressed") { KitPressedRound() }
                KitRoundLabel("Selected") {
                    KitSelectedRound(
                        selected = roundSelected,
                        onClick = {
                            roundSelected = !roundSelected
                            interactions++
                        },
                    )
                }
                KitRoundLabel("Disabled") {
                    CrystalPlayButton(StoneIcon.PLAY, "Disabled round button", onClick = {}, enabled = false)
                }
            }
            Text(
                "Interactions: $interactions",
                Modifier.jellyMatteSheet(8.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun KitThemePicker(
    current: ThemePack,
    onTheme: (ThemePack) -> Unit,
) {
    val themes = ThemePackCatalog.all
    val index = themes.indexOfFirst { it.slug == current.slug }.coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
    LaunchedEffect(index) { scroll.scrollToItem(index) }
    LazyRow(
        Modifier.fillMaxWidth().selectableGroup(),
        state = scroll,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(themes, key = { it.slug }) { theme ->
            val selected = theme.slug == current.slug
            val interaction = rememberStoneInteraction()
            val state = rememberStoneState(interaction, selected = selected)
            Box(
                Modifier
                    .height(48.dp)
                    .stonePress(interaction, reducedMotion = LocalReducedMotion.current)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onTheme(theme) },
                    ).semantics { contentDescription = theme.name },
                contentAlignment = Alignment.Center,
            ) {
                StoneSurfaceArt(StoneComponent.CHIP, state, Modifier.matchParentSize())
                Text(
                    theme.name,
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KitSelectedCapsule(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = rememberStoneInteraction()
    val state = rememberStoneState(interaction, selected = selected)
    Box(
        modifier
            .height(48.dp)
            .stonePress(interaction, reducedMotion = LocalReducedMotion.current)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StoneSurfaceArt(StoneComponent.PRIMARY_BUTTON, state, Modifier.matchParentSize())
        Text("Selected capsule", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun KitPressedCapsule(modifier: Modifier) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        TidalSurfaceArt(StoneComponent.PRIMARY_BUTTON, StoneState.PRESSED, Modifier.matchParentSize())
        Text("Pressed preview", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun KitRoundLabel(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        content()
        Text(label, Modifier.jellyMatteSheet(6.dp).padding(3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun KitSelectedRound(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction: MutableInteractionSource = rememberStoneInteraction()
    val state = rememberStoneState(interaction, selected = selected)
    Box(
        Modifier
            .size(60.dp)
            .stonePress(interaction, reducedMotion = LocalReducedMotion.current)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StoneSurfaceArt(StoneComponent.ICON_BUTTON, state, Modifier.matchParentSize())
        StoneIconArt(StoneIcon.CHECK, "Selected round button")
    }
}

@Composable
private fun KitPressedRound() {
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        TidalSurfaceArt(StoneComponent.ICON_BUTTON, StoneState.PRESSED, Modifier.matchParentSize())
        StoneIconArt(StoneIcon.FAVORITE, "Pressed round preview")
    }
}
