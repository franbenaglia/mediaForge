package com.fabapps.mediaforge.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.download.VideoOption
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.ui.common.ErrorMessage
import com.fabapps.mediaforge.ui.common.JobRow
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
) {
    val editable = !state.download.isActive && state.download !is DownloadState.Saved
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
            DownloadState.None -> DownloadButton(state.canDownload, onDownload)
            is DownloadState.Running -> {
                JobRow(fileName = state.fileName, siteName = siteName, state = download.job, actions = {
                    TextButton(onClick = onCancel, enabled = state.canCancelDownload) {
                        Text(stringResource(R.string.download_cancel))
                    }
                })
                Hint(stringResource(R.string.download_keep_open))
            }
            DownloadState.Saving -> {
                Text(stringResource(R.string.download_saving), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is DownloadState.Saved -> Saved(download.file)
            is DownloadState.Failed -> {
                Text(
                    stringResource(R.string.download_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                ErrorMessage(error = download.error, modifier = Modifier.fillMaxWidth())
                DownloadButton(state.canDownload, onDownload, again = true)
            }
            DownloadState.Cancelled -> {
                Hint(stringResource(R.string.download_cancelled))
                DownloadButton(state.canDownload, onDownload, again = true)
            }
            DownloadState.PermissionDenied -> {
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
private fun Saved(file: OutputFile) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.download_no_app)
    Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
    Hint(
        stringResource(
            if (file.mimeType.startsWith("audio/")) R.string.download_saved_music else R.string.download_saved,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(file.location.toUri(), file.mimeType)
            context.launch(intent, noApp)
        }) { Text(stringResource(R.string.download_open)) }
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND)
                .setType(file.mimeType)
                .putExtra(Intent.EXTRA_STREAM, file.location.toUri())
            context.launch(Intent.createChooser(send, null), noApp)
        }) { Text(stringResource(R.string.download_share)) }
    }
}

private fun Context.launch(intent: Intent, noAppMessage: String) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noAppMessage, Toast.LENGTH_SHORT).show()
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
                    download = DownloadState.Running(JobState.Downloading(0.42f, 120_000_000, null, 2_100_000)),
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
