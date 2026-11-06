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
    val hasSubtitles: Boolean = false,
)

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
