package com.fabapps.mediaforge.domain.model

import java.time.LocalDate

/**
 * Información de un recurso remoto, obtenida del motor de extracción.
 * Todo lo opcional puede faltar: la UI no debe bloquearse si no está.
 */
data class MediaInfo(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val site: SourceSite,
    val uploader: String? = null,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val uploadDate: LocalDate? = null,
    val description: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    /** Subtítulos que se pueden incrustar: manuales y, de los automáticos, solo el del idioma original. */
    val subtitles: List<SubtitleTrack> = emptyList(),
) {
    val hasSubtitles: Boolean get() = subtitles.isNotEmpty()
}

/**
 * Un idioma de subtítulos del sitio (ADR-021). [language] es la clave de yt-dlp (`es`, `en-US`,
 * `en-orig`); [automatic], que lo generó el sitio (reconocimiento de voz) y no una persona.
 */
data class SubtitleTrack(val language: String, val name: String? = null, val automatic: Boolean = false) {
    init {
        require(LANGUAGE.matches(language)) { "invalid subtitle language: $language" }
    }

    companion object {
        /** `--sub-langs` es una expresión regular: solo letras, números, `-` y `_`, y sin `-` al inicio. */
        val LANGUAGE = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,31}")
    }
}

/**
 * Sitio de origen tal como lo identifica el motor. No es una lista fija de plataformas:
 * cualquier sitio que soporte el motor llega con su propia clave.
 */
data class SourceSite(
    /** Identificador estable del extractor, por ejemplo `Youtube` o `Dailymotion`. */
    val extractorKey: String,
    /** Nombre para mostrar. */
    val displayName: String,
)

/**
 * Un formato ofrecido por el sitio. Contenedor y códecs van por separado:
 * `container` es el formato de archivo (mp4, webm) y `videoCodec`/`audioCodec` el contenido.
 */
data class MediaFormat(
    val formatId: String,
    val container: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val bitrateKbps: Double? = null,
    val fileSizeBytes: Long? = null,
    /** `true` si el tamaño es una estimación del sitio y no un valor exacto. */
    val fileSizeIsApproximate: Boolean = false,
) {
    val hasVideo: Boolean get() = videoCodec != null
    val hasAudio: Boolean get() = audioCodec != null
}
