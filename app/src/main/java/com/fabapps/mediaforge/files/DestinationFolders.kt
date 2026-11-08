package com.fabapps.mediaforge.files

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Permisos persistentes sobre la carpeta de destino elegida (ADR-019). */
interface DestinationFolders {
    /** Lectura y escritura siguen concedidas. */
    fun hasAccess(folder: String): Boolean

    /** Conserva el permiso que dio el selector; `false` si el sistema no lo concede. */
    fun take(folder: String): Boolean

    fun release(folder: String)

    /** Nombre para mostrar ("Music", "Descargas"…), o nulo si no se puede leer. */
    suspend fun name(folder: String): String?
}

@Singleton
class SafDestinationFolders @Inject constructor(@ApplicationContext private val context: Context) : DestinationFolders {
    private val resolver get() = context.contentResolver
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    override fun hasAccess(folder: String): Boolean = resolver.persistedUriPermissions.any {
        it.uri.toString() == folder && it.isReadPermission && it.isWritePermission
    }

    override fun take(folder: String): Boolean = try {
        resolver.takePersistableUriPermission(folder.toUri(), flags)
        hasAccess(folder)
    } catch (_: SecurityException) {
        false
    }

    override fun release(folder: String) {
        try {
            resolver.releasePersistableUriPermission(folder.toUri(), flags)
        } catch (_: SecurityException) {
            // Ya no estaba concedido.
        }
    }

    override suspend fun name(folder: String): String? = withContext(Dispatchers.IO) {
        try {
            val tree = folder.toUri()
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
