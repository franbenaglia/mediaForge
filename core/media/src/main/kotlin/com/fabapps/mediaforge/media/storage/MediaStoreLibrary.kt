package com.fabapps.mediaforge.media.storage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.file.FileNames
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [MediaLibrary] con MediaStore.
 * - Android 10+: `RELATIVE_PATH` + `IS_PENDING` (el archivo no aparece en la galería hasta terminar
 *   de copiarse) y sin permisos.
 * - Android 8–9: escribe en la carpeta pública con `WRITE_EXTERNAL_STORAGE` y la indexa con el scanner.
 */
class MediaStoreLibrary(private val context: Context, private val io: CoroutineDispatcher) : MediaLibrary {

    override fun needsWritePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED

    override suspend fun save(file: OutputFile): Outcome<OutputFile> = withContext(io) {
        val source = File(file.location)
        if (!source.isFile) return@withContext failure("source file missing")
        val kind = Kind.of(file.mimeType) ?: return@withContext failure("unsupported type ${file.mimeType}")
        val extension = file.displayName.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isEmpty() || !extension.all { it.isLetterOrDigit() }) {
            return@withContext failure("name without extension")
        }
        val name = FileNames.sanitize(file.displayName.substringBeforeLast('.'), extension)
        try {
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(source, name, file.mimeType, kind)
            } else {
                saveLegacy(source, name, file.mimeType, kind)
            }
            Outcome.Success(saved)
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
            Outcome.Failure(MediaError.Unknown(detail = "storage permission: ${e.message}"))
        } finally {
            // También si falla: el archivo privado no se reintenta y ocuparía espacio para siempre.
            source.delete()
            source.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(source: File, name: String, mime: String, kind: Kind): OutputFile {
        val resolver = context.contentResolver
        val collection = kind.collection()
        val relativePath = "${kind.directory}/$FOLDER/"
        // Solo se ven los archivos propios; si choca con uno de otra app, MediaStore agrega su sufijo.
        val unique = FileNames.unique(name) { candidate -> exists(collection, relativePath, candidate) }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("cannot open $uri")
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return OutputFile(uri.toString(), displayName(uri) ?: unique, mime, source.length())
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exists(collection: Uri, relativePath: String, name: String): Boolean = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
        arrayOf(relativePath, name),
        null,
    )?.use { it.count > 0 } ?: false

    private fun displayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    @Suppress("DEPRECATION") // Única forma en Android 8–9.
    private suspend fun saveLegacy(source: File, name: String, mime: String, kind: Kind): OutputFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(kind.directory), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = File(dir, FileNames.unique(name) { File(dir, it).exists() })
        try {
            source.copyTo(target)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        val uri = scan(target, mime) ?: Uri.fromFile(target)
        return OutputFile(uri.toString(), target.name, mime, target.length())
    }

    private suspend fun scan(file: File, mime: String): Uri? = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
            if (cont.isActive) cont.resume(uri)
        }
    }

    private fun failure(detail: String) = Outcome.Failure(MediaError.Unknown(detail = detail))

    private enum class Kind(val directory: String) {
        VIDEO(Environment.DIRECTORY_MOVIES),
        AUDIO(Environment.DIRECTORY_MUSIC),
        ;

        @RequiresApi(Build.VERSION_CODES.Q)
        fun collection(): Uri = when (this) {
            VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        companion object {
            fun of(mime: String): Kind? = when {
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                else -> null
            }
        }
    }

    companion object {
        const val FOLDER = "MediaForge"
    }
}

/** La galería (MediaStore). La app elige entre ella y una carpeta elegida (ADR-019). */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GalleryLibrary
