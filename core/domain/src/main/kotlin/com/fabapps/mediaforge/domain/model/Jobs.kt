package com.fabapps.mediaforge.domain.model

/** Estado observable de una descarga o conversión. */
sealed interface JobState {
    data object Pending : JobState

    data object Analyzing : JobState

    /** Transferencia en curso. Los tamaños son nulos cuando el sitio no los informa. */
    data class Downloading(
        val progress: Float?,
        val downloadedBytes: Long?,
        val totalBytes: Long?,
        val bytesPerSecond: Long?,
    ) : JobState

    /** Fase local de FFmpeg (merge, extracción de audio o conversión). Separada de la descarga. */
    data class Processing(val step: ProcessingStep, val progress: Float?) : JobState

    data class Completed(val outputs: List<OutputFile>, val notes: Set<JobNote> = emptySet()) : JobState

    data class Failed(val error: MediaError) : JobState

    data object Cancelled : JobState
}

/** Algo que salió distinto de lo pedido sin que el trabajo falle. Se avisa al terminar. */
enum class JobNote {
    /** El subtítulo elegido no se pudo bajar: se guardó el video sin él (ADR-021). */
    SUBTITLES_SKIPPED,
}

enum class ProcessingStep {
    /** Une pistas de audio y video sin recodificar. */
    MERGE,

    /** Copia el audio sin recodificar. */
    EXTRACT_AUDIO,

    /** Recodifica: más lento y con posible pérdida de calidad. */
    TRANSCODE,

    /** Cambia de contenedor sin recodificar. */
    REMUX,

    /** Copia el archivo elegido a la carpeta privada antes de convertir. */
    PREPARE_INPUT,

    /** Escribe metadatos o portada. */
    METADATA,

    /** Saca un fotograma del video. */
    EXTRACT_FRAME,
}

/** Archivo producido por un trabajo. `location` es una URI de contenido o una ruta privada. */
data class OutputFile(val location: String, val displayName: String, val mimeType: String, val sizeBytes: Long)

/** Errores que la UI sabe explicar. El texto crudo del motor queda solo en `detail`, para diagnóstico. */
sealed interface MediaError {
    val detail: String?

    data class InvalidUrl(override val detail: String? = null) : MediaError

    data class UnsupportedSite(override val detail: String? = null) : MediaError

    /** El sitio exige iniciar sesión. La app no lo resuelve en el MVP. */
    data class LoginRequired(override val detail: String? = null) : MediaError

    /** El sitio rechazó la descarga (HTTP 403, contenido restringido o protegido). */
    data class AccessDenied(override val detail: String? = null) : MediaError

    data class Network(override val detail: String? = null) : MediaError

    data class InsufficientStorage(
        val requiredBytes: Long?,
        val availableBytes: Long?,
        override val detail: String? = null,
    ) : MediaError

    /** El archivo no tiene lo que el destino necesita (por ejemplo, audio → video) o no se puede leer. */
    data class UnsupportedConversion(override val detail: String? = null) : MediaError

    /** Probablemente se arregla actualizando yt-dlp. */
    data class EngineOutdated(override val detail: String? = null) : MediaError

    data class Unknown(override val detail: String? = null) : MediaError
}
