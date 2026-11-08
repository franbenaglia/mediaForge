package com.fabapps.mediaforge.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.ui.format.Formatters
import kotlin.math.roundToInt

/** Formato y bitrate de audio, con los avisos de main.md §4.2. */
@Composable
internal fun AudioSection(state: HomeUiState, onSelectFormat: (Int) -> Unit, onSelectBitrate: (Int?) -> Unit) {
    val option = state.audioOption ?: return
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audio_format), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.audioOptions.forEachIndexed { index, it ->
                FilterChip(
                    selected = index == state.selectedAudio,
                    onClick = { onSelectFormat(index) },
                    label = { Text(stringResource(it.format.nameRes())) },
                )
            }
        }
        if (option.bitrates.isNotEmpty()) {
            Text(stringResource(R.string.audio_bitrate), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                option.autoKbps?.let { auto ->
                    FilterChip(
                        selected = state.audioBitrate == null,
                        onClick = { onSelectBitrate(null) },
                        label = { Text(stringResource(R.string.audio_bitrate_auto, auto)) },
                    )
                }
                option.bitrates.forEach { kbps ->
                    FilterChip(
                        selected = state.audioBitrate == kbps,
                        onClick = { onSelectBitrate(kbps) },
                        label = { Text(stringResource(R.string.audio_kbps, kbps)) },
                    )
                }
            }
        }
        when {
            option.copiesWithoutReencoding -> Note(stringResource(R.string.audio_copy_note))
            option.format.lossless -> Note(stringResource(R.string.audio_lossless_note))
        }
        if (option.format == AudioFormat.WAV) Note(stringResource(R.string.audio_wav_no_cover))
        if (state.bitrateExceedsSource) {
            val source = option.sourceKbps!!.roundToInt()
            Text(
                stringResource(R.string.audio_exceeds_note, source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        state.audioEstimatedBytes?.let { bytes ->
            Note(
                stringResource(
                    R.string.audio_estimated_size,
                    stringResource(R.string.download_size_approx, Formatters.bytes(bytes, locale)),
                ),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@StringRes
internal fun AudioFormat.nameRes(): Int = when (this) {
    AudioFormat.MP3 -> R.string.audio_name_mp3
    AudioFormat.M4A -> R.string.audio_name_m4a
    AudioFormat.OPUS -> R.string.audio_name_opus
    AudioFormat.OGG -> R.string.audio_name_ogg
    AudioFormat.WAV -> R.string.audio_name_wav
    AudioFormat.FLAC -> R.string.audio_name_flac
}
