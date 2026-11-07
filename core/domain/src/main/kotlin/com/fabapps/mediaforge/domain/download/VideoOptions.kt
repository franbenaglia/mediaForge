package com.fabapps.mediaforge.domain.download

import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.VideoContainer

/** Una calidad que se le ofrece al usuario, con los formatos concretos que se van a bajar. */
data class VideoOption(
    /** Nulo solo en la opción genérica, cuando el sitio no informó formatos. */
    val height: Int?,
    val fps: Double?,
    val selection: DownloadSelection.Video,
    val estimatedBytes: Long?,
    /** El tamaño sale de una estimación del sitio o de bitrate × duración. */
    val sizeIsApproximate: Boolean,
    val hasAudio: Boolean,
)

/**
 * Arma las opciones de calidad a partir de los formatos reales: una por altura, de mayor a menor.
 * La primera es la "mejor calidad". Cada opción elige formatos concretos para que el tamaño
 * mostrado sea el que se descarga.
 *
 * Preferencias, pensadas para que el MP4 resultante se reproduzca en cualquier teléfono:
 * video H.264 > VP9 > AV1 > otros; audio AAC > Opus > otros; nunca variantes `-drc`
 * (rango dinámico comprimido) si hay otra. Por debajo de 720p el audio se limita a
 * [LOW_QUALITY_AUDIO_KBPS], para que una calidad baja no pese por el audio.
 */
object VideoOptions {
    const val LOW_QUALITY_AUDIO_KBPS = 160.0
    private const val HD_HEIGHT = 720

    fun from(info: MediaInfo, container: VideoContainer = VideoContainer.MP4): List<VideoOption> {
        val videos = info.formats.filter { it.hasVideo && it.height != null }
        if (videos.isEmpty()) {
            return listOf(
                VideoOption(
                    height = null,
                    fps = null,
                    selection = DownloadSelection.Video(container = container),
                    estimatedBytes = null,
                    sizeIsApproximate = true,
                    hasAudio = true,
                ),
            )
        }
        val audios = info.formats.filter { it.hasAudio && !it.hasVideo }
        val best = audios.maxWithOrNull(audioOrder)
        val light =
            audios.filter { (it.bitrateKbps ?: 0.0) <= LOW_QUALITY_AUDIO_KBPS }.maxWithOrNull(audioOrder) ?: best
        return videos
            .groupBy { it.height!! }
            .toSortedMap(compareByDescending { it })
            .map { (height, atHeight) ->
                val audio = if (height < HD_HEIGHT) light else best
                optionFor(height, atHeight, audio, info.durationSeconds, container)
            }
    }

    private fun optionFor(
        height: Int,
        atHeight: List<MediaFormat>,
        audio: MediaFormat?,
        duration: Long?,
        container: VideoContainer,
    ): VideoOption {
        val videoOnly = atHeight.filter { !it.hasAudio }.maxWithOrNull(videoOrder)
        val combined = atHeight.filter { it.hasAudio }.maxWithOrNull(videoOrder)
        // Video + audio por separado da más calidad; el combinado se usa cuando no hay con qué unir.
        val parts = when {
            videoOnly != null && audio != null -> listOf(videoOnly, audio)
            combined != null -> listOf(combined)
            else -> listOf(videoOnly!!)
        }
        val sizes = parts.map { size(it, duration) }
        return VideoOption(
            height = height,
            fps = parts.first().fps,
            selection = DownloadSelection.Video(
                formatIds = parts.map { it.formatId },
                maxHeight = height,
                container = container,
            ),
            estimatedBytes = if (sizes.all { it != null }) sizes.sumOf { it!!.bytes } else null,
            sizeIsApproximate = sizes.any { it == null || it.approximate },
            hasAudio = parts.any { it.hasAudio },
        )
    }

    private class Size(val bytes: Long, val approximate: Boolean)

    private fun size(format: MediaFormat, duration: Long?): Size? {
        format.fileSizeBytes?.let { return Size(it, format.fileSizeIsApproximate) }
        val kbps = format.bitrateKbps ?: return null
        val seconds = duration ?: return null
        return Size((kbps * 1000 / 8 * seconds).toLong(), approximate = true)
    }

    private fun videoCodecRank(codec: String?): Int = when {
        codec == null -> 0
        codec.startsWith("avc") || codec.startsWith("h264") -> 4
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 3
        codec.startsWith("av01") -> 2
        else -> 1
    }

    private fun audioCodecRank(codec: String?): Int = when {
        codec == null -> 0
        codec.startsWith("mp4a") || codec == "aac" -> 3
        codec == "opus" -> 2
        else -> 1
    }

    private fun MediaFormat.isDrc() = formatId.endsWith("-drc")

    private val videoOrder: Comparator<MediaFormat> =
        compareBy<MediaFormat>({ !it.isDrc() }, { videoCodecRank(it.videoCodec) }, { it.fps ?: 0.0 })
            .thenBy { it.bitrateKbps ?: 0.0 }

    private val audioOrder: Comparator<MediaFormat> =
        compareBy<MediaFormat>({ !it.isDrc() }, { audioCodecRank(it.audioCodec) }, { it.bitrateKbps ?: 0.0 })
}
