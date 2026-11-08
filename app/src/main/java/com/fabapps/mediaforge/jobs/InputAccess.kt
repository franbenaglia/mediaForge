package com.fabapps.mediaforge.jobs

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Permiso de lectura sobre el archivo que eligió el usuario para convertir. El permiso del selector dura
 * lo que dura la actividad; para correr o reintentar la conversión más tarde hay que conservarlo (ADR-017).
 */
interface InputAccess {
    fun keep(uri: String)

    fun release(uri: String)
}

class PersistedInputAccess @Inject constructor(@ApplicationContext private val context: Context) : InputAccess {
    // Solo URIs del selector de documentos (OpenDocument) se pueden conservar; con otras, Android lanza
    // SecurityException y la conversión sigue con el permiso temporal.
    override fun keep(uri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun release(uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
