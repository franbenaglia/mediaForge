package com.fabapps.mediaforge.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

/** Error explicado al usuario, con reintento opcional. */
@Composable
fun ErrorState(error: MediaError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    CenteredMessage(modifier) {
        Text(
            stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
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
