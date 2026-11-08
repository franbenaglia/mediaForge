package com.fabapps.mediaforge.domain.history

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.OutputFile
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow

enum class HistoryKind { VIDEO, AUDIO, CONVERSION }

/**
 * Un archivo que MediaForge guardó (ADR-019). Tiene todo lo necesario para mostrarlo aunque el trabajo
 * ya no esté en Descargas.
 */
data class HistoryEntry(
    /** El id del trabajo que lo produjo. */
    val id: String,
    val kind: HistoryKind,
    val title: String,
    /** Sitio de origen; nulo en las conversiones. */
    val sourceName: String? = null,
    /** Enlace descargado, para "Descargar de nuevo"; nulo en las conversiones. */
    val sourceUrl: String? = null,
    /** Contenedor o formato de salida: "MP4", "MP3"… */
    val format: String,
    val maxHeight: Int? = null,
    val bitrateKbps: Int? = null,
    val file: OutputFile,
    val thumbnailUrl: String? = null,
    val completedAt: Long,
) {
    companion object {
        /** La entrada de un trabajo completado; nulo si el trabajo no terminó con un archivo guardado. */
        fun from(job: Job): HistoryEntry? {
            if (job.status != JobStatus.COMPLETED) return null
            val file = job.output ?: return null
            val base = HistoryEntry(
                id = job.id,
                kind = HistoryKind.CONVERSION,
                title = job.title,
                sourceName = job.sourceName,
                thumbnailUrl = job.thumbnailUrl,
                format = "",
                file = file,
                completedAt = job.updatedAt,
            )
            return when (val request = job.request) {
                is JobRequest.Download -> when (val selection = request.request.selection) {
                    is DownloadSelection.Video -> base.copy(
                        kind = HistoryKind.VIDEO,
                        format = selection.container.name,
                        maxHeight = selection.maxHeight,
                    )
                    is DownloadSelection.Audio -> base.copy(
                        kind = HistoryKind.AUDIO,
                        format = selection.format.name,
                        bitrateKbps = selection.bitrateKbps,
                    )
                }.copy(sourceUrl = request.request.sourceUrl)
                is JobRequest.Conversion -> when (val target = request.request.target) {
                    is ConversionTarget.Video -> base.copy(format = target.container.name, maxHeight = target.maxHeight)
                    is ConversionTarget.Audio -> base.copy(
                        format = target.format.name,
                        bitrateKbps = target.bitrateKbps,
                    )
                }.copy(sourceName = null)
            }
        }
    }
}

enum class HistoryFilter { ALL, VIDEO, AUDIO, CONVERSIONS }

/** Filtra por tipo y busca en título, sitio y nombre de archivo, sin distinguir mayúsculas ni tildes. */
object HistorySearch {
    fun apply(entries: List<HistoryEntry>, filter: HistoryFilter, query: String): List<HistoryEntry> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        return entries.filter { entry ->
            matches(entry.kind, filter) &&
                words.all { word ->
                    listOfNotNull(entry.title, entry.sourceName, entry.file.displayName).any {
                        normalize(it).contains(word)
                    }
                }
        }
    }

    private fun matches(kind: HistoryKind, filter: HistoryFilter) = when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.VIDEO -> kind == HistoryKind.VIDEO
        HistoryFilter.AUDIO -> kind == HistoryKind.AUDIO
        HistoryFilter.CONVERSIONS -> kind == HistoryKind.CONVERSION
    }

    private val marks = Regex("""\p{M}+""")
    private val spaces = Regex("""\s+""")

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(spaces, " ").trim()
}

/** Dónde se guarda el historial. La implementación (Room) vive en `:core:data`. */
interface HistoryStore {
    /** Inserta o reemplaza por id. */
    suspend fun put(entry: HistoryEntry)

    suspend fun get(id: String): HistoryEntry?

    /** Del más nuevo al más viejo. */
    fun observeAll(): Flow<List<HistoryEntry>>

    suspend fun delete(id: String)
}
