package com.fabapps.mediaforge.files

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Confirmación del sistema que hace falta para borrar un archivo que la app no puede borrar sola. */
interface DeleteConsent

class IntentSenderConsent(val sender: IntentSender) : DeleteConsent

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** Ya no estaba: para el usuario es lo mismo que borrado. */
    data object NotFound : DeleteResult

    data class NeedsConsent(val consent: DeleteConsent) : DeleteResult

    data object Failed : DeleteResult
}

/** Archivos que MediaForge guardó (galería o carpeta elegida), para el historial (ADR-019). */
interface SavedFiles {
    suspend fun exists(location: String): Boolean

    suspend fun delete(location: String): DeleteResult
}

@Singleton
class ContentSavedFiles @Inject constructor(@ApplicationContext private val context: Context) : SavedFiles {
    private val resolver get() = context.contentResolver

    override suspend fun exists(location: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.query(location.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override suspend fun delete(location: String): DeleteResult = withContext(Dispatchers.IO) {
        val uri = location.toUri()
        try {
            val deleted = if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                resolver.delete(uri, null, null) > 0
            }
            if (deleted) DeleteResult.Deleted else DeleteResult.NotFound
        } catch (_: FileNotFoundException) {
            DeleteResult.NotFound
        } catch (e: SecurityException) {
            consentFor(uri, e)?.let { DeleteResult.NeedsConsent(IntentSenderConsent(it)) } ?: DeleteResult.Failed
        } catch (_: IllegalArgumentException) {
            DeleteResult.Failed
        }
    }

    /** La app ya no es dueña del archivo (por ejemplo, otra app lo tocó): Android pregunta al usuario. */
    private fun consentFor(uri: Uri, error: SecurityException): IntentSender? = when {
        uri.authority != MediaStore.AUTHORITY -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> MediaStore.createDeleteRequest(
            resolver,
            listOf(uri),
        ).intentSender
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException ->
            error.userAction.actionIntent.intentSender
        else -> null
    }
}
