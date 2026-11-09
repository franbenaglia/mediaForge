package com.fabapps.mediaforge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.download.VideoOption
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.ui.common.ErrorMessage
import com.fabapps.mediaforge.ui.common.JobRow
import com.fabapps.mediaforge.ui.common.SavedFile
import com.fabapps.mediaforge.ui.common.WorkState
import com.fabapps.mediaforge.ui.format.Formatters
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme

/** Elegir calidad y nombre, descargar, seguir el progreso y abrir o compartir el resultado. */
@Composable
internal fun DownloadPanel(
    state: HomeUiState,
    siteName: String,
    onSelectOption: (Int) -> Unit,
    onFileNameChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onModeChange: (DownloadMode) -> Unit = {},
    onSelectAudioFormat: (Int) -> Unit = {},
    onSelectBitrate: (Int?) -> Unit = {},
    onSelectSubtitle: (Int?) -> Unit = {},
) {
    val editable = !state.download.isActive && state.download !is WorkState.Saved
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (editable) {
            ModeSelector(state.mode, onModeChange)
            if (state.mode == DownloadMode.VIDEO) {
                Text(stringResource(R.string.download_quality), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    state.options.forEachIndexed { index, option ->
                        OptionRow(option, isBest = index == 0, selected = index == state.selectedOption) {
                            onSelectOption(index)
                        }
                    }
                }
                if (state.subtitles.isNotEmpty()) SubtitleSection(state, onSelectSubtitle)
            } else {
                AudioSection(state, onSelectAudioFormat, onSelectBitrate)
            }
            OutlinedTextField(
                value = state.fileName,
                onValueChange = onFileNameChange,
                label = { Text(stringResource(R.string.download_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (val download = state.download) {
            WorkState.None -> DownloadButton(state.canDownload, onDownload)
            is WorkState.Running -> {
                JobRow(fileName = state.fileName, siteName = siteName, state = download.job, actions = {
                    TextButton(onClick = onCancel, enabled = state.canCancelDownload) {
                        Text(stringResource(R.string.download_cancel))
                    }
                })
                if (state.waitingForWifi) Hint(stringResource(R.string.download_waiting_wifi))
                Hint(stringResource(R.string.download_background))
            }
            WorkState.Saving -> {
                Text(stringResource(R.string.download_saving), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is WorkState.Saved -> SavedFile(download.file)
            is WorkState.Failed -> {
                Text(
                    stringResource(R.string.download_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                ErrorMessage(error = download.error, modifier = Modifier.fillMaxWidth())
                DownloadButton(state.canDownload, onDownload, again = true)
            }
            WorkState.Cancelled -> {
                Hint(stringResource(R.string.download_cancelled))
                DownloadButton(state.canDownload, onDownload, again = true)
            }
            WorkState.Interrupted -> {
                Hint(stringResource(R.string.download_interrupted))
                DownloadButton(state.canDownload, onDownload, again = true)
            }
            WorkState.PermissionDenied -> {
                Hint(stringResource(R.string.download_permission_denied))
                DownloadButton(state.canDownload, onDownload, again = true)
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: DownloadMode, onModeChange: (DownloadMode) -> Unit) {
    val modes = listOf(
        DownloadMode.VIDEO to R.string.download_mode_video,
        DownloadMode.AUDIO to R.string.download_mode_audio,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun OptionRow(option: VideoOption, isBest: Boolean, selected: Boolean, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val quality = option.height?.let { Formatters.quality(it, option.fps) }
    val size = when {
        option.estimatedBytes == null -> stringResource(R.string.download_size_unknown)
        option.sizeIsApproximate -> stringResource(
            R.string.download_size_approx,
            Formatters.bytes(option.estimatedBytes!!, locale),
        )
        else -> Formatters.bytes(option.estimatedBytes!!, locale)
    }
    val label = listOfNotNull(
        if (isBest) stringResource(R.string.download_best) else null,
        quality,
        size,
        if (option.hasAudio) null else stringResource(R.string.download_no_audio),
    ).joinToString(stringResource(R.string.detail_separator))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun DownloadButton(enabled: Boolean, onClick: () -> Unit, again: Boolean = false) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (again) R.string.download_again else R.string.download_start))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val previewOptions = listOf(
    VideoOption(1080, 60.0, DownloadSelection.Video(listOf("299", "258")), 288_387_264, false, true),
    VideoOption(720, 60.0, DownloadSelection.Video(listOf("298", "258")), 181_292_478, false, true),
    VideoOption(240, null, DownloadSelection.Video(listOf("hls-380")), 34_311_720, true, true),
)

@PreviewLightDark
@Composable
private fun DownloadPanelChoosePreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            DownloadPanel(
                state = HomeUiState(options = previewOptions, fileName = "Big Buck Bunny"),
                siteName = "YouTube",
                onSelectOption = {},
                onFileNameChange = {},
                onDownload = {},
                onCancel = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DownloadPanelRunningPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            DownloadPanel(
                state = HomeUiState(
                    options = previewOptions,
                    fileName = "Big Buck Bunny",
                    download = WorkState.Running(JobState.Downloading(0.42f, 120_000_000, null, 2_100_000)),
                ),
                siteName = "YouTube",
                onSelectOption = {},
                onFileNameChange = {},
                onDownload = {},
                onCancel = {},
            )
        }
    }
}
