package dev.geode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.geode.R
import dev.geode.ui.lake.LakeScreenHeader
import dev.geode.ui.theme.LocalThemePack
import dev.geode.ui.theme.isLivingLake

private data class SettingsCategory(
    val label: Int,
    val summary: Int,
    val icon: ImageVector,
)

private val settingsCategories =
    listOf(
        SettingsCategory(R.string.settings_tab_look, R.string.ui2_settings_look_summary, Icons.Outlined.Palette),
        SettingsCategory(R.string.settings_tab_audio, R.string.ui2_settings_audio_summary, Icons.Outlined.VolumeUp),
        SettingsCategory(R.string.settings_tab_export, R.string.ui2_settings_export_summary, Icons.Outlined.FileUpload),
        SettingsCategory(R.string.settings_tab_folders, R.string.ui2_settings_folders_summary, Icons.Outlined.Folder),
        SettingsCategory(R.string.settings_tab_behavior, R.string.ui2_settings_behavior_summary, Icons.Outlined.Tune),
        SettingsCategory(R.string.settings_tab_help, R.string.ui2_settings_help_summary, Icons.Outlined.HelpOutline),
        SettingsCategory(R.string.settings_tab_about, R.string.ui2_settings_about_summary, Icons.Outlined.Info),
    )

@Composable
internal fun AppSettingsTab(
    viewModel: PlayerViewModel,
    exportOpen: Boolean = false,
    onOpenExport: () -> Unit,
    onStartTutorial: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = geodeViewModel()
    val livingLake = LocalThemePack.current.isLivingLake
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var group by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = livingLake && group != null) { group = null }
    // Order matches the `when` below.
    val tabTitles = settingsCategories.map { stringResource(it.label) }
    Column(Modifier.fillMaxSize()) {
        if (livingLake) {
            LakeScreenHeader(
                title = stringResource(R.string.nav_settings),
                subtitle = stringResource(R.string.ui2_screen_settings_subtitle),
            )
        }
        if (livingLake && group == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabTitles.size, key = { it }) { index ->
                    val category = settingsCategories[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .jellyMatteSheet()
                            .clickable(role = Role.Button) { group = index }
                            .heightIn(min = 80.dp)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tabTitles[index], style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(category.summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            val selected = if (livingLake) group ?: 0 else tab
            if (livingLake) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { group = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.library_back))
                    }
                    Text(tabTitles[selected], style = MaterialTheme.typography.titleLarge)
                }
            } else {
                CrystalTabs(titles = tabTitles, selected = tab, onSelect = { tab = it })
            }
            when (selected) {
                0 -> LookSettingsTab(settingsViewModel)
                1 -> AudioSettingsTab(viewModel)
                2 -> ExportSettingsTab(exportOpen, onOpenExport)
                3 -> FolderSettingsTab()
                4 -> BehaviorSettingsTab(settingsViewModel)
                5 -> HelpSettingsTab(settingsViewModel, onStartTutorial)
                else -> AboutSettingsTab()
            }
        }
    }
}

@Composable
internal fun SettingsTabColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .crystalPanel(
                0.30f,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                corner = 18.dp,
                glowStrength = 0.45f,
            ).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (LocalThemePack.current.isLivingLake) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            } else {
                CrystalOverline(title, Modifier.weight(1f))
            }
            header?.invoke(this)
        }
        content()
    }
}
