package com.fabapps.mediaforge.media.ffmpeg

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Lee archivos elegidos con el selector del sistema (SAF). Solo URIs `content://`. */
class ContentInputSource(private val context: Context) : InputSource {

    override suspend fun describe(input: String): InputSource.Info? {
        val uri = input.toUri().takeIf { it.scheme == "content" } ?: return null
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val name = cursor.getString(0) ?: "media"
                    val size = if (cursor.isNull(1)) null else cursor.getLong(1)
                    InputSource.Info(name, size)
                }
        } catch (_: SecurityException) {
            null
        }
    }

    override suspend fun copyTo(input: String, target: File) {
        val uri = input.toUri()
        require(uri.scheme == "content") { "only content:// inputs" }
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            throw IOException(e.message, e)
        } catch (e: SecurityException) {
            throw IOException(e.message, e)
        } ?: throw IOException("cannot open $uri")
        stream.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    // Cancelar a mitad de una copia grande no espera a que termine.
                    coroutineContext.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private companion object {
        const val BUFFER = 256 * 1024
    }
}
