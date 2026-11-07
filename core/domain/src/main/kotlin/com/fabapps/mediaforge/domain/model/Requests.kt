package com.fabapps.mediaforge.domain.model

/** Qué quiere obtener el usuario de un recurso remoto. */
sealed interface DownloadSelection {
    /** Video con audio. `formatIds` vacío significa "mejor calidad disponible". */
    data class Video(
        val formatIds: List<String> = emptyList(),
        val maxHeight: Int? = null,
        val container: VideoContainer = VideoContainer.MP4,
    ) : DownloadSelection

    data class Audio(
        val format: AudioFormat,
        /** Solo cuando se recodifica a un formato con pérdida; nulo = sin bitrate fijo (copia o sin pérdida). */
        val bitrateKbps: Int? = null,
        /** Formato de origen concreto; vacío = el mejor audio disponible. */
        val formatIds: List<String> = emptyList(),
    ) : DownloadSelection
}

data class DownloadRequest(
    val jobId: String,
    val sourceUrl: String,
    val selection: DownloadSelection,
    /** Nombre elegido por el usuario, sin extensión. Se sanea antes de usarlo. */
    val fileName: String,
    val embedMetadata: Boolean = true,
    /** Tamaño estimado (de `VideoOptions`), para chequear el espacio antes de empezar. */
    val expectedBytes: Long? = null,
)

data class ConversionRequest(
    val jobId: String,
    /** URI de contenido del archivo de entrada. */
    val input: String,
    val target: ConversionTarget,
    val fileName: String,
)

sealed interface ConversionTarget {
    data class Video(val container: VideoContainer, val maxHeight: Int? = null) : ConversionTarget

    data class Audio(val format: AudioFormat, val bitrateKbps: Int? = null) : ConversionTarget
}

enum class VideoContainer(val extension: String, val mimeType: String) {
    MP4("mp4", "video/mp4"),
    MKV("mkv", "video/x-matroska"),
    WEBM("webm", "video/webm"),
    MOV("mov", "video/quicktime"),
}

enum class AudioFormat(val extension: String, val mimeType: String, val lossless: Boolean) {
    MP3("mp3", "audio/mpeg", lossless = false),
    M4A("m4a", "audio/mp4", lossless = false),
    OPUS("opus", "audio/ogg", lossless = false),
    OGG("ogg", "audio/ogg", lossless = false),
    WAV("wav", "audio/wav", lossless = true),
    FLAC("flac", "audio/flac", lossless = true),
}
