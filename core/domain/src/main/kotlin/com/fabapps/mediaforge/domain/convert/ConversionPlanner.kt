package com.fabapps.mediaforge.domain.convert

import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer

/** Qué se hace con una pista. */
sealed interface StreamPlan {
    data object Copy : StreamPlan

    /** [codec] con nombre de FFmpeg; el comando elige el codificador. */
    data class Encode(val codec: String, val bitrateKbps: Int? = null) : StreamPlan

    data object Drop : StreamPlan
}

data class ConversionPlan(
    val video: StreamPlan,
    val audio: StreamPlan,
    /** Altura de salida si hay que reducir la resolución. */
    val scaleToHeight: Int?,
    val extension: String,
    val mimeType: String,
) {
    val reencodes: Boolean get() = video is StreamPlan.Encode || audio is StreamPlan.Encode
}

/**
 * Decide, pista por pista, si se copia o se recodifica (main.md §5.3, ADR-016). Copiar es instantáneo y
 * sin pérdida: se hace siempre que el contenedor de destino admite el códec.
 */
object ConversionPlanner {

    /** Códecs que cada contenedor admite sin recodificar, pensando en que se reproduzcan en un teléfono. */
    private val videoCodecs = mapOf(
        VideoContainer.MP4 to setOf("h264", "hevc", "av1", "vp9", "mpeg4"),
        VideoContainer.MOV to setOf("h264", "hevc", "mpeg4", "prores", "mjpeg"),
        VideoContainer.WEBM to setOf("vp8", "vp9", "av1"),
    )
    private val audioCodecs = mapOf(
        VideoContainer.MP4 to setOf("aac", "mp3", "ac3", "eac3"),
        VideoContainer.MOV to setOf("aac", "mp3", "alac", "pcm_s16le", "ac3"),
        VideoContainer.WEBM to setOf("opus", "vorbis"),
    )

    /** MKV admite prácticamente cualquier códec. */
    private fun accepts(table: Map<VideoContainer, Set<String>>, container: VideoContainer, codec: String) =
        container == VideoContainer.MKV || codec in table.getValue(container)

    const val VIDEO_AUDIO_KBPS = 160
    const val WEBM_AUDIO_KBPS = 128

    fun plan(media: LocalMedia, target: ConversionTarget): Outcome<ConversionPlan> = when (target) {
        is ConversionTarget.Video -> planVideo(media, target)
        is ConversionTarget.Audio -> planAudio(media, target)
    }

    private fun planVideo(media: LocalMedia, target: ConversionTarget.Video): Outcome<ConversionPlan> {
        val video = media.video ?: return Outcome.Failure(MediaError.UnsupportedConversion("no video track"))
        val container = target.container
        val scale = target.maxHeight?.takeIf { max -> video.height != null && video.height > max }
        val webm = container == VideoContainer.WEBM
        val videoPlan = if (scale == null && accepts(videoCodecs, container, video.codec)) {
            StreamPlan.Copy
        } else {
            StreamPlan.Encode(if (webm) "vp9" else "h264")
        }
        val audioPlan = when {
            media.audio == null -> StreamPlan.Drop
            accepts(audioCodecs, container, media.audio.codec) -> StreamPlan.Copy
            webm -> StreamPlan.Encode("opus", WEBM_AUDIO_KBPS)
            else -> StreamPlan.Encode("aac", VIDEO_AUDIO_KBPS)
        }
        return Outcome.Success(ConversionPlan(videoPlan, audioPlan, scale, container.extension, container.mimeType))
    }

    private fun planAudio(media: LocalMedia, target: ConversionTarget.Audio): Outcome<ConversionPlan> {
        val audio = media.audio ?: return Outcome.Failure(MediaError.UnsupportedConversion("no audio track"))
        val format = target.format
        val bitrates = AudioOptions.bitratesFor(format)
        val chosen = target.bitrateKbps?.takeIf { it in bitrates }
        val audioPlan = if (chosen == null && codecOf(format) == audio.codec) {
            StreamPlan.Copy
        } else {
            StreamPlan.Encode(codecOf(format), chosen ?: AudioOptions.autoBitrate(format, audio.bitrateKbps))
        }
        return Outcome.Success(ConversionPlan(StreamPlan.Drop, audioPlan, null, format.extension, format.mimeType))
    }

    /** Códec de FFmpeg que produce cada formato de audio. */
    fun codecOf(format: AudioFormat): String = when (format) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.M4A -> "aac"
        AudioFormat.OPUS -> "opus"
        AudioFormat.OGG -> "vorbis"
        AudioFormat.WAV -> "pcm_s16le"
        AudioFormat.FLAC -> "flac"
    }

    /**
     * Tamaño de salida, para chequear el espacio. Copia: el de la entrada. Audio recodificado:
     * bitrate × duración (WAV: PCM). Video recodificado: el de la entrada, como cota razonable.
     */
    fun estimateBytes(media: LocalMedia, plan: ConversionPlan): Long? {
        val duration = media.durationSeconds
        if (plan.video == StreamPlan.Drop) {
            val audio = plan.audio
            return when {
                // FLAC no se puede estimar bien: se usa el tamaño de un WAV como cota.
                audio is StreamPlan.Encode && audio.codec in LOSSLESS -> duration?.let {
                    (it * WAV_BYTES_PER_SECOND).toLong()
                }
                audio is StreamPlan.Encode && audio.bitrateKbps != null ->
                    duration?.let { (audio.bitrateKbps * 1000 / 8 * it).toLong() }
                else -> media.sizeBytes
            }
        }
        return media.sizeBytes
    }

    private val LOSSLESS = setOf("pcm_s16le", "flac")

    /** PCM 16 bits, 44,1 kHz, estéreo. */
    private const val WAV_BYTES_PER_SECOND = 176_400.0
}
