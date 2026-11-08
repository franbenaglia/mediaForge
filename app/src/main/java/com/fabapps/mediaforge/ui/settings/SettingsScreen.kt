package com.fabapps.mediaforge.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.settings.PreferredMode
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.ThemeMode
import com.fabapps.mediaforge.ui.common.messageRes
import com.fabapps.mediaforge.ui.home.nameRes
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.settings_source_url)
    SettingsContent(
        state = state,
        actions = SettingsActions(
            onModeChange = viewModel::onModeChange,
            onMaxHeightChange = viewModel::onMaxHeightChange,
            onAudioFormatChange = viewModel::onAudioFormatChange,
            onAudioBitrateChange = viewModel::onAudioBitrateChange,
            onMaxDownloadsChange = viewModel::onMaxDownloadsChange,
            onWifiOnlyChange = viewModel::onWifiOnlyChange,
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
            onCheckEngine = viewModel::onCheckEngine,
            onInstallEngine = viewModel::onInstallEngine,
            onOpenSource = { context.openLink(sourceUrl) },
        ),
        appVersion = context.appVersion(),
    )
}

data class SettingsActions(
    val onModeChange: (PreferredMode) -> Unit = {},
    val onMaxHeightChange: (Int?) -> Unit = {},
    val onAudioFormatChange: (AudioFormat) -> Unit = {},
    val onAudioBitrateChange: (Int?) -> Unit = {},
    val onMaxDownloadsChange: (Int) -> Unit = {},
    val onWifiOnlyChange: (Boolean) -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onCheckEngine: () -> Unit = {},
    val onInstallEngine: () -> Unit = {},
    val onOpenSource: () -> Unit = {},
)

@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    if (state.loading) return
    val settings = state.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.settings_section_downloads))
        Choices(
            title = stringResource(R.string.settings_default_mode),
            options = PreferredMode.entries,
            selected = settings.mode,
            label = {
                stringResource(
                    if (it ==
                        PreferredMode.VIDEO
                    ) {
                        R.string.download_mode_video
                    } else {
                        R.string.download_mode_audio
                    },
                )
            },
            onSelect = actions.onModeChange,
        )
        Choices(
            title = stringResource(R.string.settings_default_quality),
            options = listOf<Int?>(null) + Settings.HEIGHTS,
            selected = settings.maxHeight,
            label = {
                it?.let { h -> stringResource(R.string.downloads_height, h) }
                    ?: stringResource(R.string.download_best)
            },
            onSelect = actions.onMaxHeightChange,
            note = stringResource(R.string.settings_quality_note),
        )
        Choices(
            title = stringResource(R.string.settings_audio_format),
            options = AudioOptions.ORDER,
            selected = settings.audioFormat,
            label = { stringResource(it.nameRes()) },
            onSelect = actions.onAudioFormatChange,
        )
        if (state.bitrates.isNotEmpty()) {
            Choices(
                title = stringResource(R.string.settings_audio_bitrate),
                options = listOf<Int?>(null) + state.bitrates,
                selected = settings.audioBitrateKbps,
                label = {
                    it?.let { kbps -> stringResource(R.string.audio_kbps, kbps) }
                        ?: stringResource(R.string.settings_bitrate_auto)
                },
                onSelect = actions.onAudioBitrateChange,
            )
        }
        Choices(
            title = stringResource(R.string.settings_max_downloads),
            options = Settings.DOWNLOAD_RANGE.toList(),
            selected = settings.maxDownloads,
            label = { it.toString() },
            onSelect = actions.onMaxDownloadsChange,
            note = stringResource(R.string.settings_max_downloads_note),
        )
        Toggle(
            title = stringResource(R.string.settings_wifi_only),
            checked = settings.wifiOnly,
            onChange = actions.onWifiOnlyChange,
            note = stringResource(R.string.settings_wifi_only_note),
        )

        HorizontalDivider()
        Section(stringResource(R.string.settings_section_appearance))
        Choices(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.theme,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onThemeChange,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toggle(
                title = stringResource(R.string.settings_dynamic_color),
                checked = settings.dynamicColor,
                onChange = actions.onDynamicColorChange,
            )
        }

        HorizontalDivider()
        Section(stringResource(R.string.settings_section_engine))
        Engine(state, actions)

        HorizontalDivider()
        Section(stringResource(R.string.settings_section_about))
        Note(stringResource(R.string.settings_version, appVersion))
        Note(stringResource(R.string.settings_license))
        Note(stringResource(R.string.settings_privacy))
        OutlinedButton(onClick = actions.onOpenSource) { Text(stringResource(R.string.settings_source)) }
    }
}

@Composable
private fun Engine(state: SettingsUiState, actions: SettingsActions) {
    Text(
        state.engineVersion?.let { stringResource(R.string.settings_engine_version, it) }
            ?: stringResource(R.string.settings_engine_unknown),
        style = MaterialTheme.typography.bodyLarge,
    )
    val status = when (val engine = state.engine) {
        EngineStatus.Idle -> null
        EngineStatus.Checking -> stringResource(R.string.settings_engine_checking)
        EngineStatus.UpToDate -> stringResource(R.string.settings_engine_up_to_date)
        is EngineStatus.Available -> stringResource(R.string.settings_engine_available, engine.version)
        EngineStatus.Updating -> stringResource(R.string.home_updating_engine)
        is EngineStatus.Updated -> stringResource(R.string.settings_engine_updated, engine.version)
        is EngineStatus.Failed -> stringResource(engine.error.messageRes())
    }
    status?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.engine is EngineStatus.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.canInstall) {
            Button(onClick = actions.onInstallEngine) { Text(stringResource(R.string.settings_engine_install)) }
        } else {
            OutlinedButton(onClick = actions.onCheckEngine, enabled = !state.busyEngine) {
                Text(stringResource(R.string.settings_engine_check))
            }
        }
        if (state.busyEngine) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
    }
    Note(stringResource(R.string.settings_engine_note))
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, note: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            note?.let { Note(it) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    note: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = {
                    onSelect(option)
                }, label = { Text(label(option)) })
            }
        }
        note?.let { Note(it) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun Context.appVersion(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}

private fun Context.openLink(url: String) = startSafely(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin una app que lo abra no hay nada que hacer; el botón no promete más.
    }
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            SettingsContent(
                state = SettingsUiState(
                    loading = false,
                    engineVersion = "2025.11.12",
                    engine = EngineStatus.Available("2026.10.26"),
                ),
                actions = SettingsActions(),
                appVersion = "0.1.0",
            )
        }
    }
}
