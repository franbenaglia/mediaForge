package com.fabapps.mediaforge.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fabapps.mediaforge.R
import com.fabapps.mediaforge.domain.model.OutputFile

/** Archivo guardado en la galería, con Abrir y Compartir. */
@Composable
fun SavedFile(file: OutputFile) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.download_no_app)
    Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(
            if (file.mimeType.startsWith("audio/")) R.string.download_saved_music else R.string.download_saved,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { context.openFile(file, noApp) }) { Text(stringResource(R.string.download_open)) }
        OutlinedButton(onClick = { context.shareFile(file, noApp) }) { Text(stringResource(R.string.download_share)) }
    }
}

/** Abre el archivo guardado con la app que elija el sistema. */
fun Context.openFile(file: OutputFile, noAppMessage: String) =
    launch(Intent(Intent.ACTION_VIEW).setDataAndType(file.location.toUri(), file.mimeType), noAppMessage)

fun Context.shareFile(file: OutputFile, noAppMessage: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, file.location.toUri())
    launch(Intent.createChooser(send, null), noAppMessage)
}

private fun Context.launch(intent: Intent, noAppMessage: String) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}
