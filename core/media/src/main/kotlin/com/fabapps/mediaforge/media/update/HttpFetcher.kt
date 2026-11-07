package com.fabapps.mediaforge.media.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Descargas del actualizador. Separado para poder probar [YtDlpUpdater] sin red. */
interface HttpFetcher {
    /** Cuerpo completo; falla si supera [maxBytes]. */
    suspend fun get(url: String, maxBytes: Long): ByteArray

    /** Escribe en [target]; falla si supera [maxBytes]. */
    suspend fun download(url: String, target: File, maxBytes: Long)
}

class HttpStatusException(val status: Int, url: String) : IOException("HTTP $status for ${url.substringBefore('?')}")

/** Solo HTTPS, con tiempos límite. Sigue redirecciones (GitHub manda los assets a otro host). */
class UrlConnectionFetcher : HttpFetcher {

    override suspend fun get(url: String, maxBytes: Long): ByteArray =
        open(url).useResponse(url) { input -> input.readLimited(maxBytes) }

    override suspend fun download(url: String, target: File, maxBytes: Long) {
        open(url).useResponse(url) { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("download larger than $maxBytes bytes")
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "only https is allowed" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MediaForge")
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
        return connection
    }

    private fun <T> HttpURLConnection.useResponse(url: String, block: (java.io.InputStream) -> T): T = try {
        val status = responseCode
        if (status !in 200..299) throw HttpStatusException(status, url)
        // Una redirección a http:// no se acepta.
        if (!this.url.toString().startsWith("https://")) throw IOException("redirected to non-https")
        inputStream.use(block)
    } finally {
        disconnect()
    }

    private fun java.io.InputStream.readLimited(maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("response larger than $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
