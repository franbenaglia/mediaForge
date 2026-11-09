package com.fabapps.mediaforge.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.SubtitleTrack
import java.util.Locale

/** Elegir un idioma de subtítulos para incrustar en el video (ADR-021). */
@Composable
internal fun SubtitleSection(state: HomeUiState, onSelectSubtitle: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.download_subtitles), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.subtitle?.let { subtitleLabel(it) } ?: stringResource(R.string.download_subtitles_none))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_subtitles_none)) },
                    onClick = {
                        open = false
                        onSelectSubtitle(null)
                    },
                )
                state.subtitles.forEachIndexed { index, track ->
                    DropdownMenuItem(
                        text = { Text(subtitleLabel(track)) },
                        onClick = {
                            open = false
                            onSelectSubtitle(index)
                        },
                    )
                }
            }
        }
        if (state.subtitle != null) {
            Text(
                stringResource(R.string.download_subtitles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** El nombre que da el sitio o, si falta, el del idioma en el idioma del teléfono. */
@Composable
private fun subtitleLabel(track: SubtitleTrack): String {
    val locale = LocalConfiguration.current.locales[0]
    val code = track.language.removeSuffix("-orig")
    val name = track.name ?: Locale.forLanguageTag(code).getDisplayName(locale)
        .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { it.titlecase(locale) }
        ?: track.language
    return if (track.automatic) stringResource(R.string.download_subtitles_automatic, name) else name
}
