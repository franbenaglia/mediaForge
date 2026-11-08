package com.fabapps.mediaforge.domain.download

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo

/** Un formato de audio de salida, con el audio de origen que se va a usar. */
data class AudioOption(
    val format: AudioFormat,
    /** Formato del sitio que se baja; nulo si el sitio no informó formatos. */
    val sourceFormatId: String?,
    val sourceKbps: Double?,
    /** El códec de origen ya es el de salida: se copia, sin pérdida extra y sin elegir bitrate. */
    val copiesWithoutReencoding: Boolean,
    /** Bitrates que se pueden elegir. Vacío si se copia o si el formato es sin pérdida. */
    val bitrates: List<Int>,
    /** Bitrate de "Automático": el menor de [bitrates] que iguala o supera al original. */
    val autoKbps: Int?,
    val durationSeconds: Long?,
) {
    /** `true` si convertir a [kbps] no puede mejorar el audio: el original tiene menos. */
    fun exceedsSource(kbps: Int): Boolean = sourceKbps != null && kbps > sourceKbps + TOLERANCE_KBPS

    /** Selección para descargar con [kbps] elegido, o el automático si es nulo. */
    fun selection(kbps: Int? = null): DownloadSelection.Audio = DownloadSelection.Audio(
        format = format,
        bitrateKbps = if (bitrates.isEmpty()) null else (kbps?.takeIf { it in bitrates } ?: autoKbps),
        formatIds = listOfNotNull(sourceFormatId),
    )

    /** Tamaño estimado del archivo final. Siempre aproximado (salvo copia con tamaño exacto del sitio). */
    fun estimatedBytes(kbps: Int? = null, sourceBytes: Long? = null): Long? {
        val seconds = durationSeconds ?: return if (copiesWithoutReencoding) sourceBytes else null
        val rate = when {
            copiesWithoutReencoding -> return sourceBytes ?: sourceKbps?.let { bytes(it, seconds) }
            format == AudioFormat.WAV -> WAV_KBPS
            format.lossless -> return null // FLAC depende del contenido.
            else -> (selection(kbps).bitrateKbps ?: return null).toDouble()
        }
        return bytes(rate, seconds)
    }

    private fun bytes(kbps: Double, seconds: Long) = (kbps * 1000 / 8 * seconds).toLong()

    private companion object {
        /** Los sitios informan bitrates como 129,48: no avisar por diferencias de redondeo. */
        const val TOLERANCE_KBPS = 8.0

        /** PCM 16 bits, 44,1 kHz, estéreo. */
        const val WAV_KBPS = 1411.2
    }
}

/**
 * Opciones de audio, en el orden de main.md §4.1: MP3, M4A y Opus primero.
 *
 * AAC se ofrece como M4A (AAC en contenedor MP4): el `.aac` suelto no admite portada ni
 * metadatos (ADR-015).
 */
object AudioOptions {
    val ORDER =
        listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.OGG, AudioFormat.WAV, AudioFormat.FLAC)

    private val BITRATES = mapOf(
        AudioFormat.MP3 to listOf(128, 192, 256, 320),
        AudioFormat.M4A to listOf(128, 192, 256),
        AudioFormat.OPUS to listOf(96, 128, 160),
        AudioFormat.OGG to listOf(128, 192, 256),
    )

    /** Bitrates que se ofrecen para recodificar a [format]; vacío para los formatos sin pérdida (ADR-015). */
    fun bitratesFor(format: AudioFormat): List<Int> = BITRATES[format].orEmpty()

    /**
     * "Automático": el menor bitrate de la lista que iguala o supera al original (con 8 kbps de
     * tolerancia por redondeo). Sin dato del original, el segundo de la lista.
     */
    fun autoBitrate(format: AudioFormat, sourceKbps: Double?): Int? {
        val list = bitratesFor(format)
        if (list.isEmpty()) return null
        if (sourceKbps == null) return list.getOrElse(1) { list.last() }
        return list.firstOrNull { it >= sourceKbps - 8 } ?: list.last()
    }

    /** Formato a elegir de entrada con el preferido de los ajustes; el primero si no está. */
    fun preferredIndex(options: List<AudioOption>, format: AudioFormat): Int =
        options.indexOfFirst { it.format == format }.coerceAtLeast(0)

    fun from(info: MediaInfo): List<AudioOption> {
        val audioOnly = info.formats.filter { it.hasAudio && !it.hasVideo }
        // Sin pistas de audio sueltas (Dailymotion), se usa el combinado más liviano: el video se descarta.
        val candidates = audioOnly.ifEmpty {
            info.formats.filter { it.hasAudio }.sortedBy { it.height ?: Int.MAX_VALUE }.take(1)
        }
        return ORDER.map { format -> optionFor(format, candidates, info.durationSeconds) }
    }

    /** Tamaño del formato de origen, para estimar una copia. */
    fun sourceBytes(info: MediaInfo, option: AudioOption): Long? =
        info.formats.firstOrNull { it.formatId == option.sourceFormatId }?.fileSizeBytes

    private fun optionFor(format: AudioFormat, candidates: List<MediaFormat>, duration: Long?): AudioOption {
        val sameCodec = candidates.filter { codecMatches(format, it.audioCodec) }.maxWithOrNull(quality)
        val source = sameCodec ?: candidates.maxWithOrNull(preferredForTranscoding)
        val copies = sameCodec != null
        val bitrates = if (copies) emptyList() else bitratesFor(format)
        // En un formato combinado, el bitrate informado incluye el video: el del audio no se conoce.
        val sourceKbps = source?.takeIf { !it.hasVideo }?.bitrateKbps
        return AudioOption(
            format = format,
            sourceFormatId = source?.formatId,
            sourceKbps = sourceKbps,
            copiesWithoutReencoding = copies,
            bitrates = bitrates,
            autoKbps = if (bitrates.isEmpty()) null else autoBitrate(format, sourceKbps),
            durationSeconds = duration,
        )
    }

    private fun codecMatches(format: AudioFormat, codec: String?): Boolean = codec != null &&
        when (format) {
            AudioFormat.MP3 -> codec == "mp3"
            AudioFormat.M4A -> codec.startsWith("mp4a") || codec == "aac"
            AudioFormat.OPUS -> codec == "opus"
            AudioFormat.OGG -> codec == "vorbis"
            AudioFormat.FLAC -> codec == "flac"
            AudioFormat.WAV -> false
        }

    private fun MediaFormat.isDrc() = formatId.endsWith("-drc")

    private val quality: Comparator<MediaFormat> =
        compareBy<MediaFormat>({ !it.isDrc() }, { it.bitrateKbps ?: 0.0 })

    /** Para recodificar se parte del mejor original; Opus rinde más que AAC al mismo bitrate. */
    private val preferredForTranscoding: Comparator<MediaFormat> =
        compareBy<MediaFormat>({ !it.isDrc() }, { if (it.audioCodec == "opus") 1 else 0 }, { it.bitrateKbps ?: 0.0 })
}
