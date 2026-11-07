package com.fabapps.mediaforge.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.SourceSite
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.ui.common.ErrorMessage
import com.fabapps.mediaforge.ui.common.MediaCard
import com.fabapps.mediaforge.ui.common.RemoteThumbnail
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Solo Android 8–9: guardar en la galería pide permiso de escritura.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onDownload() else viewModel.onStoragePermissionDenied()
    }

    HomeContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onPaste = {
            scope.launch {
                val clip = clipboard.getClipEntry()?.clipData
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                viewModel.onPaste(text)
            }
        },
        onClear = viewModel::onClear,
        onAnalyze = viewModel::onAnalyze,
        onUpdateEngine = viewModel::onUpdateEngine,
        download = DownloadActions(
            onSelectOption = viewModel::onSelectOption,
            onFileNameChange = viewModel::onFileNameChange,
            onDownload = {
                if (viewModel.needsStoragePermission) {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.onDownload()
                }
            },
            onCancel = viewModel::onCancelDownload,
        ),
    )
}

internal data class DownloadActions(
    val onSelectOption: (Int) -> Unit = {},
    val onFileNameChange: (String) -> Unit = {},
    val onDownload: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

@Composable
private fun HomeContent(
    state: HomeUiState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
    onUpdateEngine: () -> Unit = {},
    download: DownloadActions = DownloadActions(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.home_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val error = state.invalidReason?.takeIf { state.status == InputStatus.INVALID }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_url_label)) },
            placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
            singleLine = true,
            readOnly = state.inputLocked,
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(it.messageRes())) } },
            trailingIcon = if (state.input.isNotEmpty() && !state.inputLocked) {
                {
                    IconButton(onClick = onClear) {
                        Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.home_clear))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPaste, enabled = !state.inputLocked) {
                Icon(painterResource(R.drawable.ic_paste), contentDescription = null)
                Text(stringResource(R.string.home_paste), Modifier.padding(start = 8.dp))
            }
            Button(onClick = onAnalyze, enabled = state.canAnalyze) {
                Text(stringResource(R.string.home_analyze))
            }
        }
        AnalysisResult(state.analysis, onRetry = onAnalyze)
        val loaded = state.analysis as? AnalysisState.Loaded
        if (loaded != null) {
            DownloadPanel(
                state = state,
                siteName = loaded.info.site.displayName,
                onSelectOption = download.onSelectOption,
                onFileNameChange = download.onFileNameChange,
                onDownload = download.onDownload,
                onCancel = download.onCancel,
            )
        }
        if (state.canUpdateEngine) {
            Button(onClick = onUpdateEngine, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.home_update_engine))
            }
        }
    }
}

@Composable
private fun AnalysisResult(analysis: AnalysisState, onRetry: () -> Unit) {
    when (analysis) {
        AnalysisState.Idle -> Unit
        AnalysisState.UpdatingEngine -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.home_updating_engine), style = MaterialTheme.typography.bodyMedium)
        }
        is AnalysisState.UpdateFailed -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.home_update_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            ErrorMessage(error = analysis.error)
        }
        AnalysisState.Loading -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.home_analyzing), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.home_first_run_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        is AnalysisState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val info = analysis.info
            MediaCard(
                title = info.title,
                siteName = info.site.displayName,
                uploader = info.uploader,
                durationSeconds = info.durationSeconds,
                thumbnail = info.thumbnailUrl?.let { url -> { RemoteThumbnail(url) } },
            )
            Text(
                pluralStringResource(R.plurals.home_formats_count, info.formats.size, info.formats.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is AnalysisState.Failed -> ErrorMessage(
            error = analysis.error,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            onRetry = onRetry,
        )
    }
}

@StringRes
private fun UrlCheck.Reason.messageRes(): Int = when (this) {
    UrlCheck.Reason.EMPTY -> R.string.url_error_empty
    UrlCheck.Reason.TOO_LONG -> R.string.url_error_too_long
    UrlCheck.Reason.NOT_A_URL -> R.string.url_error_not_a_url
    UrlCheck.Reason.UNSUPPORTED_SCHEME -> R.string.url_error_scheme
    UrlCheck.Reason.MISSING_HOST -> R.string.url_error_missing_host
    UrlCheck.Reason.CREDENTIALS -> R.string.url_error_credentials
    UrlCheck.Reason.LOCAL_ADDRESS -> R.string.url_error_local
}

@PreviewLightDark
@Composable
private fun HomeContentReadyPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            HomeContent(
                state = HomeUiState(input = "https://www.youtube.com/watch?v=aqz-KE-bpKQ", status = InputStatus.READY),
                onInputChange = {},
                onPaste = {},
                onClear = {},
                onAnalyze = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeContentLoadedPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            HomeContent(
                state = HomeUiState(
                    input = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                    status = InputStatus.READY,
                    analysis = AnalysisState.Loaded(
                        MediaInfo(
                            id = "aqz-KE-bpKQ",
                            sourceUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                            title = "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film",
                            site = SourceSite("Youtube", "YouTube"),
                            uploader = "Blender",
                            durationSeconds = 635,
                        ),
                    ),
                ),
                onInputChange = {},
                onPaste = {},
                onClear = {},
                onAnalyze = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeContentFailedPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            HomeContent(
                state = HomeUiState(
                    input = "https://vimeo.com/1",
                    status = InputStatus.READY,
                    analysis = AnalysisState.Failed(MediaError.LoginRequired()),
                ),
                onInputChange = {},
                onPaste = {},
                onClear = {},
                onAnalyze = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeContentInvalidPreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            HomeContent(
                state = HomeUiState(
                    input = "file:///etc/passwd",
                    status = InputStatus.INVALID,
                    invalidReason = UrlCheck.Reason.UNSUPPORTED_SCHEME,
                ),
                onInputChange = {},
                onPaste = {},
                onClear = {},
                onAnalyze = {},
            )
        }
    }
}
