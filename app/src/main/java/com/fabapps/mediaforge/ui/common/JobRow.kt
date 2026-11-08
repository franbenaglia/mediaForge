package com.fabapps.mediaforge.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.ui.format.Formatters
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme

/**
 * Una fila del administrador de descargas: nombre, estado, progreso y detalle.
 * Las acciones (cancelar, reintentar, abrir) entran por [actions].
 */
@Composable
fun JobRow(
    fileName: String,
    siteName: String?,
    state: JobState,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = stringResource(state.statusRes())
            val detail = jobDetail(state)
            Text(
                text = listOfNotNull(
                    siteName,
                    status,
                    detail,
                ).joinToString(stringResource(R.string.detail_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = if (state is JobState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.isActive) {
                val fraction = state.progressFraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        actions?.invoke()
    }
}

/** Tamaño, velocidad o motivo del error, según el estado. `null` si no hay nada útil para mostrar. */
@Composable
fun jobDetail(state: JobState): String? {
    val locale = LocalConfiguration.current.locales[0]
    return when (state) {
        is JobState.Downloading -> {
            val downloaded = state.downloadedBytes?.let { Formatters.bytes(it, locale) }
            val total = state.totalBytes?.let { Formatters.bytes(it, locale) }
            val size = when {
                downloaded != null && total != null -> stringResource(R.string.job_progress_of, downloaded, total)
                else -> downloaded ?: total
            }
            val speed = state.bytesPerSecond?.let { stringResource(R.string.job_speed, Formatters.bytes(it, locale)) }
            listOfNotNull(size, speed).joinToString(stringResource(R.string.detail_separator)).ifEmpty { null }
        }
        is JobState.Completed -> state.outputs.sumOf { it.sizeBytes }.takeIf { it > 0 }
            ?.let { Formatters.bytes(it, locale) }
        is JobState.Failed -> stringResource(state.error.messageRes())
        else -> null
    }
}

@PreviewLightDark
@Composable
private fun JobRowPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            Column {
                JobRow("Big Buck Bunny.mp4", "YouTube", JobState.Downloading(0.42f, 52_000_000, 124_000_000, 2_100_000))
                JobRow("Big Buck Bunny.mp4", "YouTube", JobState.Processing(ProcessingStep.MERGE, null))
                JobRow("Big Buck Bunny.mp3", "YouTube", JobState.Failed(MediaError.AccessDenied()))
                JobRow("Big Buck Bunny.mp3", null, JobState.Pending)
            }
        }
    }
}
