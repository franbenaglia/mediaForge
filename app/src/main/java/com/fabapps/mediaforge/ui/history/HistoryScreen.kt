package com.fabapps.mediaforge.ui.history

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryFilter
import com.fabapps.mediaforge.domain.history.HistoryKind
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.files.IntentSenderConsent
import com.fabapps.mediaforge.ui.common.EmptyState
import com.fabapps.mediaforge.ui.common.RemoteThumbnail
import com.fabapps.mediaforge.ui.common.openFile
import com.fabapps.mediaforge.ui.common.shareFile
import com.fabapps.mediaforge.ui.format.Formatters
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onOpenHome: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.resultCode == Activity.RESULT_OK)
    }
    val consent = state.consent
    LaunchedEffect(consent) {
        val sender = (consent?.consent as? IntentSenderConsent)?.sender
        if (consent != null && !consent.shown && sender != null) {
            viewModel.onConsentShown()
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val deleted = stringResource(R.string.history_file_deleted)
    val failed = stringResource(R.string.history_delete_failed)
    LaunchedEffect(state.message) {
        val text = when (state.message) {
            HistoryMessage.FILE_DELETED -> deleted
            HistoryMessage.DELETE_FAILED -> failed
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.onMessageShown()
    }

    HistoryContent(
        state = state,
        actions = HistoryActions(
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onRedownload = { if (viewModel.onRedownload(it)) onOpenHome() },
            onRemove = viewModel::onRemove,
            onDeleteFile = viewModel::onDeleteFile,
        ),
    )

    state.confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message, item.entry.file.displayName)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDelete) { Text(stringResource(R.string.download_cancel)) }
            },
        )
    }
}

data class HistoryActions(
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (HistoryFilter) -> Unit = {},
    val onRedownload: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onDeleteFile: (String) -> Unit = {},
)

@Composable
fun HistoryContent(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier) {
    when {
        state.loading -> Unit
        state.isEmpty -> EmptyState(
            iconRes = R.drawable.ic_nav_history,
            title = stringResource(R.string.history_empty_title),
            message = stringResource(R.string.history_empty_message),
            modifier = modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    label = { Text(stringResource(R.string.history_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "filters") { Filters(state.filter, actions.onFilterChange) }
            if (state.items.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.history_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.items, key = { it.id }) { HistoryCard(it, actions) }
        }
    }
}

@Composable
private fun Filters(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

private fun HistoryFilter.labelRes() = when (this) {
    HistoryFilter.ALL -> R.string.history_filter_all
    HistoryFilter.VIDEO -> R.string.history_kind_video
    HistoryFilter.AUDIO -> R.string.history_kind_audio
    HistoryFilter.CONVERSIONS -> R.string.history_filter_conversions
}

private fun HistoryKind.labelRes() = when (this) {
    HistoryKind.VIDEO -> R.string.history_kind_video
    HistoryKind.AUDIO -> R.string.history_kind_audio
    HistoryKind.CONVERSION -> R.string.history_kind_conversion
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(item: HistoryItem, actions: HistoryActions) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val noApp = stringResource(R.string.download_no_app)
    val entry = item.entry
    val separator = stringResource(R.string.detail_separator)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                entry.thumbnailUrl?.let { RemoteThumbnail(it, modifier = Modifier.size(width = 96.dp, height = 54.dp)) }
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(
                    stringResource(entry.kind.labelRes()),
                    entry.sourceName,
                    entry.format,
                    entry.maxHeight?.let { stringResource(R.string.downloads_height, it) },
                    entry.bitrateKbps?.let { stringResource(R.string.downloads_kbps, it) },
                    Formatters.bytes(entry.file.sizeBytes, locale),
                ).joinToString(separator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    locale,
                ).format(Date(entry.completedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.missing) {
                Text(
                    stringResource(R.string.history_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!item.missing) {
                    TextButton(onClick = { context.openFile(entry.file, noApp) }) {
                        Text(stringResource(R.string.download_open))
                    }
                    TextButton(onClick = { context.shareFile(entry.file, noApp) }) {
                        Text(stringResource(R.string.download_share))
                    }
                }
                if (item.canRedownload) {
                    TextButton(onClick = { actions.onRedownload(item.id) }) {
                        Text(stringResource(R.string.history_redownload))
                    }
                }
                TextButton(onClick = { actions.onRemove(item.id) }) { Text(stringResource(R.string.downloads_remove)) }
                if (!item.missing) {
                    TextButton(onClick = { actions.onDeleteFile(item.id) }) {
                        Text(stringResource(R.string.history_delete_file))
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryPreview() {
    val file = OutputFile("content://x", "Big Buck Bunny.mp4", "video/mp4", 52_000_000)
    val video = HistoryEntry(
        id = "1",
        kind = HistoryKind.VIDEO,
        title = "Big Buck Bunny",
        sourceName = "YouTube",
        sourceUrl = "https://youtu.be/x",
        format = "MP4",
        maxHeight = 1080,
        file = file,
        completedAt = 0,
    )
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            HistoryContent(
                state = HistoryUiState(
                    loading = false,
                    isEmpty = false,
                    items = listOf(
                        HistoryItem(video, missing = false),
                        HistoryItem(
                            video.copy(id = "2", kind = HistoryKind.CONVERSION, sourceName = null, sourceUrl = null),
                            missing = true,
                        ),
                    ),
                ),
                actions = HistoryActions(),
            )
        }
    }
}
