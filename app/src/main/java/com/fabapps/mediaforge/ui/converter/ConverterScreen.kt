package com.fabapps.mediaforge.ui.converter

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.convert.AudioStream
import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.convert.VideoStream
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.ui.common.ErrorMessage
import com.fabapps.mediaforge.ui.common.JobRow
import com.fabapps.mediaforge.ui.common.SavedFile
import com.fabapps.mediaforge.ui.common.WorkState
import com.fabapps.mediaforge.ui.format.Formatters
import com.fabapps.mediaforge.ui.home.nameRes
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import kotlin.math.roundToLong

@Composable
fun ConverterScreen(viewModel: ConverterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onFilePicked(uri?.toString())
    }
    // Solo Android 8–9: guardar en la galería pide permiso de escritura.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onConvert() else viewModel.onStoragePermissionDenied()
    }
    ConverterContent(
        state = state,
        actions = ConverterActions(
            onPick = { picker.launch(arrayOf("video/*", "audio/*")) },
            onKind = viewModel::onKindChange,
            onContainer = viewModel::onContainerChange,
            onMaxHeight = viewModel::onMaxHeightChange,
            onAudioFormat = viewModel::onAudioFormatChange,
            onBitrate = viewModel::onBitrateChange,
            onFileName = viewModel::onFileNameChange,
            onConvert = {
                focus.clearFocus()
                if (viewModel.needsStoragePermission) {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.onConvert()
                }
            },
            onCancel = viewModel::onCancel,
        ),
    )
}

internal data class ConverterActions(
    val onPick: () -> Unit = {},
    val onKind: (TargetKind) -> Unit = {},
    val onContainer: (VideoContainer) -> Unit = {},
    val onMaxHeight: (Int?) -> Unit = {},
    val onAudioFormat: (AudioFormat) -> Unit = {},
    val onBitrate: (Int?) -> Unit = {},
    val onFileName: (String) -> Unit = {},
    val onConvert: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

@Composable
private fun ConverterContent(state: ConverterUiState, actions: ConverterActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.converter_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val input = state.input) {
            InputState.None -> Button(onClick = actions.onPick) { Text(stringResource(R.string.converter_pick)) }
            InputState.Inspecting -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.converter_inspecting), style = MaterialTheme.typography.bodyMedium)
            }
            is InputState.Failed -> {
                ErrorMessage(input.error, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = actions.onPick) { Text(stringResource(R.string.converter_change_file)) }
            }
            is InputState.Ready -> {
                FileCard(input.media)
                if (!state.locked) {
                    TextButton(onClick = actions.onPick) { Text(stringResource(R.string.converter_change_file)) }
                }
                Options(state, input.media, actions)
                Work(state, actions)
            }
        }
    }
}

@Composable
private fun FileCard(media: LocalMedia) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(media.displayName, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(
                media.durationSeconds?.let { Formatters.duration(it.roundToLong()) },
                media.sizeBytes?.let { Formatters.bytes(it, locale) },
                media.video?.let { v ->
                    listOfNotNull(Formatters.codec(v.codec), v.height?.let { h -> Formatters.quality(h, v.fps) })
                        .joinToString(" ")
                },
                media.audio?.let { a ->
                    listOfNotNull(Formatters.codec(a.codec), a.bitrateKbps?.let { "${it.roundToLong()} kbps" })
                        .joinToString(" ")
                },
            )
            Text(
                details.joinToString(stringResource(R.string.detail_separator)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Options(state: ConverterUiState, media: LocalMedia, actions: ConverterActions) {
    if (state.locked || state.work is WorkState.Saved) return
    val kinds = listOf(
        TargetKind.VIDEO to R.string.converter_target_video,
        TargetKind.AUDIO to R.string.converter_target_audio,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, (kind, label) ->
            SegmentedButton(
                selected = state.kind == kind,
                onClick = { actions.onKind(kind) },
                enabled = kind == TargetKind.AUDIO || media.video != null,
                shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
            ) { Text(stringResource(label)) }
        }
    }
    if (media.video == null) Note(stringResource(R.string.converter_no_video_track))

    if (state.kind == TargetKind.VIDEO) {
        Section(stringResource(R.string.converter_container)) {
            VideoContainer.entries.forEach { container ->
                FilterChip(
                    selected = state.container == container,
                    onClick = { actions.onContainer(container) },
                    label = { Text(container.extension.uppercase()) },
                )
            }
        }
        if (state.heightOptions.isNotEmpty()) {
            Section(stringResource(R.string.converter_resolution)) {
                FilterChip(
                    selected = state.maxHeight == null,
                    onClick = { actions.onMaxHeight(null) },
                    label = { Text(stringResource(R.string.converter_resolution_original)) },
                )
                state.heightOptions.forEach { height ->
                    FilterChip(
                        selected = state.maxHeight == height,
                        onClick = { actions.onMaxHeight(height) },
                        label = { Text(Formatters.quality(height, null)) },
                    )
                }
            }
        }
    } else {
        Section(stringResource(R.string.audio_format)) {
            AudioFormat.entries.forEach { format ->
                FilterChip(
                    selected = state.audioFormat == format,
                    onClick = { actions.onAudioFormat(format) },
                    label = { Text(stringResource(format.nameRes())) },
                )
            }
        }
        if (state.bitrates.isNotEmpty()) {
            Section(stringResource(R.string.audio_bitrate)) {
                state.autoBitrate?.let { auto ->
                    FilterChip(
                        selected = state.audioBitrate == null,
                        onClick = { actions.onBitrate(null) },
                        label = { Text(stringResource(R.string.audio_bitrate_auto, auto)) },
                    )
                }
                state.bitrates.forEach { kbps ->
                    FilterChip(
                        selected = state.audioBitrate == kbps,
                        onClick = { actions.onBitrate(kbps) },
                        label = { Text(stringResource(R.string.audio_kbps, kbps)) },
                    )
                }
            }
        }
    }

    when (val plan = state.plan) {
        is Outcome.Success -> PlanNotes(state, plan.value, media)
        is Outcome.Failure -> ErrorMessage(plan.error, modifier = Modifier.fillMaxWidth())
        null -> Unit
    }
    OutlinedTextField(
        value = state.fileName,
        onValueChange = actions.onFileName,
        label = { Text(stringResource(R.string.download_file_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanNotes(state: ConverterUiState, plan: ConversionPlan, media: LocalMedia) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val video = plan.video) {
            StreamPlan.Copy -> Note(
                stringResource(R.string.converter_video_copy, Formatters.codec(media.video!!.codec)),
            )
            is StreamPlan.Encode -> Note(stringResource(R.string.converter_video_encode, Formatters.codec(video.codec)))
            StreamPlan.Drop -> Unit
        }
        when (val audio = plan.audio) {
            StreamPlan.Copy -> Note(
                stringResource(R.string.converter_audio_copy, Formatters.codec(media.audio!!.codec)),
            )
            is StreamPlan.Encode -> Note(
                if (audio.bitrateKbps != null) {
                    stringResource(
                        R.string.converter_audio_encode_kbps,
                        Formatters.codec(audio.codec),
                        audio.bitrateKbps!!,
                    )
                } else {
                    stringResource(R.string.converter_audio_encode, Formatters.codec(audio.codec))
                },
            )
            StreamPlan.Drop -> if (plan.video != StreamPlan.Drop) Note(stringResource(R.string.converter_audio_none))
        }
        Note(stringResource(if (plan.reencodes) R.string.converter_reencode_note else R.string.converter_fast_note))
        if (state.bitrateExceedsSource) {
            Text(
                stringResource(R.string.audio_exceeds_note, media.audio!!.bitrateKbps!!.roundToLong().toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (state.kind == TargetKind.AUDIO &&
            state.audioFormat.lossless
        ) {
            Note(stringResource(R.string.audio_lossless_note))
        }
        state.estimatedBytes?.let { Note(stringResource(R.string.converter_estimated, Formatters.bytes(it, locale))) }
    }
}

@Composable
private fun Work(state: ConverterUiState, actions: ConverterActions) {
    when (val work = state.work) {
        WorkState.None -> ConvertButton(state.canConvert, actions.onConvert)
        is WorkState.Running -> {
            JobRow(fileName = state.fileName, siteName = null, state = work.job, actions = {
                TextButton(onClick = actions.onCancel, enabled = state.canCancel) {
                    Text(stringResource(R.string.download_cancel))
                }
            })
            Note(stringResource(R.string.converter_keep_open))
        }
        WorkState.Saving -> {
            Text(stringResource(R.string.download_saving), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is WorkState.Saved -> SavedFile(work.file)
        is WorkState.Failed -> {
            Text(
                stringResource(R.string.converter_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            ErrorMessage(work.error, modifier = Modifier.fillMaxWidth())
            ConvertButton(state.canConvert, actions.onConvert, again = true)
        }
        WorkState.Cancelled -> {
            Note(stringResource(R.string.converter_cancelled))
            ConvertButton(state.canConvert, actions.onConvert, again = true)
        }
        WorkState.PermissionDenied -> {
            Note(stringResource(R.string.download_permission_denied))
            ConvertButton(state.canConvert, actions.onConvert, again = true)
        }
    }
}

@Composable
private fun ConvertButton(enabled: Boolean, onClick: () -> Unit, again: Boolean = false) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (again) R.string.converter_again else R.string.converter_start))
    }
}

@Composable
private fun Section(title: String, chips: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@PreviewLightDark
@Composable
private fun ConverterEmptyPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface { ConverterContent(ConverterUiState(), ConverterActions()) }
    }
}

@PreviewLightDark
@Composable
private fun ConverterReadyPreview() {
    val media = LocalMedia(
        displayName = "Vacaciones.mkv",
        sizeBytes = 182_000_000,
        durationSeconds = 312.0,
        video = VideoStream("h264", 1920, 1080, 30.0),
        audio = AudioStream("opus", 128.0, 2, 48_000),
    )
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            ConverterContent(
                ConverterUiState(
                    input = InputState.Ready("content://x", media),
                    kind = TargetKind.VIDEO,
                    fileName = "Vacaciones",
                ),
                ConverterActions(),
            )
        }
    }
}
