package com.fabapps.mediaforge.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme

/** Lista vacía: ícono, título y explicación de qué hacer. */
@Composable
fun EmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    CenteredMessage(modifier) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Error explicado al usuario, centrado en toda la pantalla, con reintento opcional. */
@Composable
fun ErrorState(error: MediaError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    CenteredMessage(modifier) { ErrorMessage(error, onRetry = onRetry) }
}

/** El mismo mensaje, para usar dentro de otra pantalla (no ocupa todo el alto). */
@Composable
fun ErrorMessage(error: MediaError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        error.detail?.let { TechnicalDetail(it) }
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** Texto crudo del motor (ya sin query strings), plegado y seleccionable para poder reportarlo. */
@Composable
private fun TechnicalDetail(detail: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.error_hide_details else R.string.error_show_details))
    }
    if (expanded) {
        SelectionContainer {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@PreviewLightDark
@Composable
private fun EmptyStatePreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface {
            EmptyState(
                iconRes = R.drawable.ic_nav_downloads,
                title = stringResource(R.string.nav_downloads),
                message = stringResource(R.string.screen_not_implemented),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ErrorStatePreview() {
    MediaForgeTheme(dynamicColor = false) {
        Surface { ErrorState(MediaError.EngineOutdated(), onRetry = {}) }
    }
}
