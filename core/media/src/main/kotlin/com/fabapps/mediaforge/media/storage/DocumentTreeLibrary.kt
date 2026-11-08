package com.fabapps.mediaforge.media.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.file.FileNames
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.FolderLibrary
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [FolderLibrary] sobre el Storage Access Framework: la carpeta es la URI de un árbol con permiso persistente. */
class DocumentTreeLibrary(private val context: Context, private val io: CoroutineDispatcher) : FolderLibrary {
    private val resolver get() = context.contentResolver

    override suspend fun save(file: OutputFile, folder: String): Outcome<OutputFile>? = withContext(io) {
        val source = File(file.location)
        if (!source.isFile) return@withContext Outcome.Failure(MediaError.Unknown(detail = "source file missing"))
        val extension = file.displayName.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isEmpty() || !extension.all { it.isLetterOrDigit() }) {
            return@withContext Outcome.Failure(MediaError.Unknown(detail = "name without extension"))
        }
        val tree = folder.toUri()
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (_: IllegalArgumentException) {
            return@withContext null
        }
        val existing = childNames(tree) ?: return@withContext null

        val name = FileNames.unique(FileNames.sanitize(file.displayName.substringBeforeLast('.'), extension)) {
            it in existing
        }
        try {
            // El proveedor puede ajustar el nombre: se lee el que quedó.
            val target = DocumentsContract.createDocument(resolver, parent, file.mimeType, name)
                ?: throw IOException("cannot create $name")
            try {
                val out = resolver.openOutputStream(target) ?: throw IOException("cannot open $target")
                out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            } catch (e: IOException) {
                DocumentsContract.deleteDocument(resolver, target)
                throw e
            }
            Outcome.Success(OutputFile(target.toString(), displayName(target) ?: name, file.mimeType, source.length()))
        } catch (e: IOException) {
            val noSpace = e.message.orEmpty().contains("ENOSPC") || e.message.orEmpty().contains("No space")
            Outcome.Failure(
                if (noSpace) {
                    MediaError.InsufficientStorage(file.sizeBytes, null, detail = e.message)
                } else {
                    MediaError.Unknown(detail = e.message)
                },
            )
        } catch (e: SecurityException) {
            Outcome.Failure(MediaError.Unknown(detail = "folder permission: ${e.message}"))
        } finally {
            source.delete()
            source.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
        }
    }

    /** Nombres de lo que ya hay en la carpeta; `null` si no se puede leer (borrada o sin permiso). */
    private fun childNames(tree: Uri): Set<String>? = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    } catch (_: SecurityException) {
        null
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun displayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
}
