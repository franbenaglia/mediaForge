package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.ProcessingStep

/**
 * Convierte la salida de una descarga de yt-dlp en estados. Tiene estado: una instancia por descarga.
 *
 * Una descarga "video + audio" son dos archivos que yt-dlp baja uno después del otro. Se informan como
 * un solo avance: `(parte + fracción) / partes`. El total en bytes se conoce recién en la última parte;
 * antes queda nulo en lugar de mostrar un número que después crece.
 *
 * Líneas que se usan:
 * - `[info] <id>: Downloading 1 format(s): 299+258` → cantidad de partes.
 * - `[download] Destination: ...` → empieza una parte.
 * - `MFPROG <status> <bajados> <total> <estimado> <velocidad>` (plantilla de [YtDlpCommand.download]).
 * - Líneas `Merger` y `Fixup` → unión o ajuste sin recodificar; `Metadata`, `EmbedThumbnail` y
 *   `ThumbnailsConvertor` → metadatos.
 * - `ExtractAudio` → [audioStep] (recodificar), o extraer sin recodificar si yt-dlp dice "Not converting".
 */
class YtDlpProgressParser(private val audioStep: ProcessingStep = ProcessingStep.EXTRACT_AUDIO) {
    private var parts = 1
    private var partIndex = -1
    private var finishedBytes = 0L
    private var currentBytes = 0L
    private var last: JobState? = null

    /** Devuelve un estado nuevo, o `null` si la línea no cambia nada. */
    fun feed(line: String): JobState? {
        val state = parse(line.trim()) ?: return null
        if (state == last) return null
        last = state
        return state
    }

    private fun parse(line: String): JobState? = when {
        line.startsWith("[info]") -> {
            FORMATS.find(line)?.let { match -> parts = match.groupValues[1].split('+').size.coerceAtLeast(1) }
            null
        }
        line.startsWith("[download] Destination:") -> {
            finishedBytes += currentBytes
            currentBytes = 0
            partIndex = (partIndex + 1).coerceAtMost(parts - 1)
            null
        }
        line.startsWith(YtDlpCommand.PROGRESS_PREFIX + " ") -> progress(line)
        line.startsWith("[Merger]") || line.startsWith("[Fixup") -> JobState.Processing(ProcessingStep.MERGE, null)
        line.startsWith("[ExtractAudio] Not converting") -> JobState.Processing(ProcessingStep.EXTRACT_AUDIO, null)
        line.startsWith("[ExtractAudio]") -> JobState.Processing(audioStep, null)
        line.startsWith(
            "[Metadata]",
        ) ||
            line.startsWith("[EmbedThumbnail]") ||
            line.startsWith("[ThumbnailsConvertor]") ->
            JobState.Processing(ProcessingStep.METADATA, null)
        else -> null
    }

    private fun progress(line: String): JobState? {
        val fields = line.split(' ')
        if (fields.size < 6) return null
        val downloaded = number(fields[2]) ?: return null
        val partTotal = number(fields[3]) ?: number(fields[4])
        val speed = number(fields[5])
        if (partIndex < 0) partIndex = 0
        currentBytes = downloaded

        val fraction = partTotal?.takeIf { it > 0 }?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) }
        val progress = fraction?.let { ((partIndex + it) / parts).coerceIn(0f, 1f) }
        val isLastPart = partIndex == parts - 1
        return JobState.Downloading(
            progress = progress,
            downloadedBytes = finishedBytes + downloaded,
            totalBytes = if (isLastPart && partTotal != null) finishedBytes + partTotal else null,
            bytesPerSecond = speed,
        )
    }

    /** yt-dlp escribe `NA` cuando no sabe un valor y a veces decimales (`1234.5`). */
    private fun number(text: String): Long? = text.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }?.toLong()

    private companion object {
        val FORMATS = Regex("""Downloading \d+ format\(s\): (\S+)""")
    }
}
