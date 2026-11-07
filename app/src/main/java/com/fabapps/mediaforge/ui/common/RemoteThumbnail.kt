package com.fabapps.mediaforge.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Miniatura remota, decorativa (el título ya describe el contenido). Solo se cargan URLs `https`:
 * la URL viene del sitio, no del usuario. Mientras carga o si falla, queda el fondo de la tarjeta.
 */
@Composable
fun RemoteThumbnail(url: String, modifier: Modifier = Modifier) {
    if (!isLoadable(url)) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

internal fun isLoadable(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
