package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.MediaError

/**
 * Traduce el stderr de yt-dlp a un [MediaError] que la UI sabe explicar.
 *
 * Se basa en texto: los mensajes de yt-dlp cambian entre versiones, así que ante la duda devuelve
 * [MediaError.Unknown] con el detalle. El detalle nunca incluye los parámetros de las URLs
 * (pueden traer tokens firmados).
 */
object YtDlpErrorClassifier {
    private const val MAX_DETAIL_CHARS = 1_000

    private val storage = listOf("no space left on device", "[errno 28]", "disk quota exceeded")
    private val drm = listOf("drm protected", "drm-protected", "is drm")
    private val login = listOf(
        "only works when logged-in",
        "login required",
        "requires authentication",
        "this video is private",
        "private video",
        "sign in to confirm your age",
        "use --cookies",
        "account credentials",
        "members-only",
        "join this channel",
    )

    /** Antes que [login]: el mensaje de YouTube también sugiere `--cookies`, pero no se resuelve con una cuenta. */
    private val botCheck = listOf("confirm you're not a bot", "confirm you’re not a bot")
    private val denied = listOf(
        "http error 403",
        "not available in your country",
        "geo restricted",
        "geo-restricted",
        "video unavailable",
        "has been removed",
        "http error 404",
        "content is not available",
    )
    private val unsupported = listOf("unsupported url")
    private val invalidUrl = listOf("is not a valid url")
    private val network = listOf(
        "[errno 7]",
        "[errno -2]",
        "[errno -3]",
        "no address associated with hostname",
        "temporary failure in name resolution",
        "name or service not known",
        "network is unreachable",
        "connection reset",
        "connection refused",
        "timed out",
        "urlopen error",
        "http error 429",
        "too many requests",
        "remote end closed connection",
    )
    private val extractionBroken = listOf(
        "unable to extract",
        "nsig extraction failed",
        "signature extraction failed",
        "please report this issue",
        "unable to download api page",
    )
    private const val OUTDATED_WARNING = "is older than 90 days"

    private val urlWithQuery = Regex("""(https?://[^\s?#]+)[?#]\S*""")

    fun classify(stderr: String): MediaError {
        val lines = stderr.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val relevant = lines.filter { it.startsWith("ERROR:") }.ifEmpty { lines.takeLast(3) }
        val text = relevant.joinToString("\n").lowercase()
        val outdated = stderr.contains(OUTDATED_WARNING, ignoreCase = true)
        val detail = redact(relevant.joinToString("\n")).take(MAX_DETAIL_CHARS).ifBlank { null }

        return when {
            text.containsAny(storage) -> MediaError.InsufficientStorage(null, null, detail)
            text.containsAny(drm) -> MediaError.AccessDenied(detail)
            text.containsAny(botCheck) -> MediaError.AccessDenied(detail)
            text.containsAny(login) -> MediaError.LoginRequired(detail)
            text.containsAny(unsupported) -> MediaError.UnsupportedSite(detail)
            text.containsAny(invalidUrl) -> MediaError.InvalidUrl(detail)
            text.containsAny(network) -> MediaError.Network(detail)
            text.containsAny(extractionBroken) -> MediaError.EngineOutdated(detail)
            // Con un yt-dlp viejo, un 403 o "no disponible" suele arreglarse actualizando (Etapa 0).
            outdated && text.containsAny(denied) -> MediaError.EngineOutdated(detail)
            text.containsAny(denied) -> MediaError.AccessDenied(detail)
            else -> MediaError.Unknown(detail)
        }
    }

    /** Deja esquema, host y ruta; quita query y fragmento. */
    fun redact(text: String): String = urlWithQuery.replace(text) { it.groupValues[1] }

    private fun String.containsAny(needles: List<String>) = needles.any { contains(it) }
}
