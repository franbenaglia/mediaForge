package com.fabapps.mediaforge.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.ui.common.EmptyState
import com.fabapps.mediaforge.ui.common.ErrorMessage
import com.fabapps.mediaforge.ui.common.WorkState
import com.fabapps.mediaforge.ui.common.isActive
import com.fabapps.mediaforge.ui.common.jobDetail
import com.fabapps.mediaforge.ui.common.messageRes
import com.fabapps.mediaforge.ui.common.openFile
import com.fabapps.mediaforge.ui.common.progressFraction
import com.fabapps.mediaforge.ui.common.shareFile
import com.fabapps.mediaforge.ui.common.statusRes
import com.fabapps.mediaforge.ui.format.Formatters
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DownloadsContent(
        state = state,
        actions = JobActions(
            onCancel = viewModel::onCancel,
            onRetry = viewModel::onRetry,
            onRemove = viewModel::onRemove,
            onToggleDetails = viewModel::onToggleDetails,
            onRemoveCompleted = viewModel::onRemoveCompleted,
        ),
    )
}

data class JobActions(
    val onCancel: (String) -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onToggleDetails: (String) -> Unit = {},
    val onRemoveCompleted: () -> Unit = {},
)

@Composable
fun DownloadsContent(state: DownloadsUiState, actions: JobActions, modifier: Modifier = Modifier) {
    when {
        state.loading -> Unit
        state.items.isEmpty() -> EmptyState(
            iconRes = R.drawable.ic_nav_downloads,
            title = stringResource(R.string.downloads_empty_title),
            message = stringResource(R.string.downloads_empty_message),
            modifier = modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.hasCompleted) {
                item(key = "remove-completed") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = actions.onRemoveCompleted) {
                            Text(stringResource(R.string.downloads_remove_completed))
                        }
                    }
                }
            }
            items(state.items, key = { it.id }) { item ->
                JobCard(item, expanded = item.id == state.expandedId, actions = actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(item: JobItem, expanded: Boolean, actions: JobActions) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.download_no_app)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.job.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    item.job.sourceName ?: stringResource(R.string.downloads_conversion),
                    requestSummary(item.job.request),
                ).joinToString(stringResource(R.string.detail_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Status(item)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val output = item.job.output
                if (item.job.status == JobStatus.COMPLETED && output != null) {
                    TextButton(onClick = {
                        context.openFile(output, noApp)
                    }) { Text(stringResource(R.string.download_open)) }
                    TextButton(onClick = {
                        context.shareFile(output, noApp)
                    }) { Text(stringResource(R.string.download_share)) }
                }
                if (item.canCancel) {
                    TextButton(onClick = {
                        actions.onCancel(item.id)
                    }) { Text(stringResource(R.string.download_cancel)) }
                }
                if (item.canRetry) {
                    TextButton(onClick = { actions.onRetry(item.id) }) { Text(stringResource(R.string.action_retry)) }
                }
                if (item.canRemove) {
                    TextButton(onClick = {
                        actions.onRemove(item.id)
                    }) { Text(stringResource(R.string.downloads_remove)) }
                }
                TextButton(onClick = { actions.onToggleDetails(item.id) }) {
                    Text(stringResource(if (expanded) R.string.downloads_hide_details else R.string.downloads_details))
                }
            }
            if (expanded) Details(item)
        }
    }
}

/** Fase (en cola, descarga, procesamiento, guardado) con progreso, o el resultado. */
@Composable
private fun Status(item: JobItem) {
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.detail_separator)
    val (text, isError) = when (val work = item.work) {
        is WorkState.Running -> when {
            item.waitingRetry -> stringResource(R.string.notification_waiting_retry) to false
            item.isQueued -> stringResource(R.string.downloads_queued) to false
            else -> listOfNotNull(stringResource(work.job.statusRes()), jobDetail(work.job)).joinToString(separator) to
                false
        }
        WorkState.Saving -> stringResource(R.string.download_saving) to false
        is WorkState.Saved -> listOf(
            stringResource(R.string.job_status_completed),
            Formatters.bytes(work.file.sizeBytes, locale),
        ).joinToString(separator) to false
        is WorkState.Failed -> listOf(
            stringResource(R.string.job_status_failed),
            stringResource(work.error.messageRes()),
        ).joinToString(separator) to true
        WorkState.Cancelled -> stringResource(R.string.job_status_cancelled) to false
        WorkState.Interrupted -> stringResource(R.string.downloads_interrupted) to true
        WorkState.None, WorkState.PermissionDenied -> "" to false
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    val running = (item.work as? WorkState.Running)?.job
    if (running != null && running.isActive && !item.isQueued) {
        val fraction = running.progressFraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    if (item.work == WorkState.Saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Details(item: JobItem) {
    val locale = LocalConfiguration.current.locales[0]
    val created = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        locale,
    ).format(Date(item.job.createdAt))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLine(stringResource(R.string.downloads_detail_added, created))
        DetailLine(stringResource(R.string.downloads_detail_attempts, item.job.attempts))
        item.job.output?.let { file ->
            DetailLine(
                stringResource(
                    R.string.downloads_detail_file,
                    file.displayName,
                    Formatters.bytes(file.sizeBytes, locale),
                ),
            )
        }
        val error = item.job.error
        if (error != null &&
            item.job.status == JobStatus.FAILED
        ) {
            ErrorMessage(error, modifier = Modifier.fillMaxWidth())
        }
        if (item.canRemove) DetailLine(stringResource(R.string.downloads_remove_note))
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "MP4 · 1080p", "MP3 · 320 kbps"… */
@Composable
private fun requestSummary(request: JobRequest): String {
    // Formato, altura y bitrate del pedido; null donde no se eligió.
    val (format, height, kbps) = when (request) {
        is JobRequest.Download -> when (val selection = request.request.selection) {
            is DownloadSelection.Video -> Triple(selection.container.name, selection.maxHeight, null)
            is DownloadSelection.Audio -> Triple(selection.format.name, null, selection.bitrateKbps)
        }
        is JobRequest.Conversion -> when (val target = request.request.target) {
            is ConversionTarget.Video -> Triple(target.container.name, target.maxHeight, null)
            is ConversionTarget.Audio -> Triple(target.format.name, null, target.bitrateKbps)
        }
    }
    return listOfNotNull(
        format,
        height?.let { stringResource(R.string.downloads_height, it) },
        kbps?.let { stringResource(R.string.downloads_kbps, it) },
    ).joinToString(stringResource(R.string.detail_separator))
}

@PreviewLightDark
@Composable
private fun DownloadsPreview() {
    fun job(id: String, status: JobStatus, error: MediaError? = null, output: OutputFile? = null) = Job(
        JobRequest.Download(
            DownloadRequest(
                id,
                "https://example.com",
                DownloadSelection.Video(maxHeight = 1080, container = VideoContainer.MP4),
                id,
            ),
        ),
        title = "Big Buck Bunny $id",
        sourceName = "YouTube",
        status = status,
        error = error,
        output = output,
        attempts = 1,
        createdAt = 0,
    )
    val file = OutputFile("content://x", "Big Buck Bunny.mp4", "video/mp4", 52_000_000)
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            DownloadsContent(
                state = DownloadsUiState(
                    loading = false,
                    items = listOf(
                        JobItem(
                            job(
                                "1",
                                JobStatus.RUNNING,
                            ),
                            WorkState.Running(JobState.Downloading(0.4f, 20_000_000, 50_000_000, 2_000_000)),
                        ),
                        JobItem(job("2", JobStatus.QUEUED), WorkState.Running(JobState.Pending)),
                        JobItem(job("3", JobStatus.COMPLETED, output = file), WorkState.Saved(file)),
                        JobItem(
                            job("4", JobStatus.FAILED, MediaError.AccessDenied()),
                            WorkState.Failed(MediaError.AccessDenied()),
                        ),
                    ),
                    expandedId = "4",
                ),
                actions = JobActions(),
            )
        }
    }
}
