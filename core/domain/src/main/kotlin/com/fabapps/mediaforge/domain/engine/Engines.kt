package com.fabapps.mediaforge.domain.engine

import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import kotlinx.coroutines.flow.Flow

/**
 * Contratos de los motores. Las implementaciones (yt-dlp, FFmpeg) viven fuera del dominio.
 *
 * Cancelación: cancelar la corrutina que colecta el `Flow` detiene el trabajo y su proceso
 * nativo. No hay un `cancel(id)` aparte (ver DECISIONS.md, ADR-005).
 */
interface MediaExtractor {
    suspend fun extract(url: String): Outcome<MediaInfo>
}

interface MediaDownloader {
    /** Emite estados hasta `Completed`, `Failed` o `Cancelled`. */
    fun download(request: DownloadRequest): Flow<JobState>
}

interface MediaConverter {
    /** Emite estados hasta `Completed`, `Failed` o `Cancelled`. No usa yt-dlp. */
    fun convert(request: ConversionRequest): Flow<JobState>
}

/** Información y actualización del motor de extracción. */
interface ExtractorEngine {
    suspend fun installedVersion(): String?

    /** Versión más nueva publicada, o `null` si ya está al día. */
    suspend fun checkForUpdate(): Outcome<String?>

    /** Descarga, verifica firma y hash, prueba y activa la versión nueva. Conserva la anterior. */
    suspend fun update(): Outcome<String>
}

/** Resultado sin excepciones para errores esperables. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: MediaError) : Outcome<Nothing>
}
