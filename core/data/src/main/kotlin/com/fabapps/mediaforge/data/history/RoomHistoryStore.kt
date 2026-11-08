package com.fabapps.mediaforge.data.history

import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryKind
import com.fabapps.mediaforge.domain.history.HistoryStore
import com.fabapps.mediaforge.domain.model.OutputFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryStore(private val dao: HistoryDao) : HistoryStore {
    override suspend fun put(entry: HistoryEntry) = dao.upsert(HistoryMapper.toEntity(entry))

    override suspend fun get(id: String): HistoryEntry? = dao.get(id)?.let(HistoryMapper::toDomain)

    override fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeAll().map { rows -> rows.mapNotNull(HistoryMapper::toDomain) }

    override suspend fun delete(id: String) = dao.delete(id)
}

/** Ida y vuelta entre [HistoryEntry] y [HistoryEntity]. Un tipo desconocido descarta la fila. */
object HistoryMapper {
    fun toEntity(entry: HistoryEntry) = HistoryEntity(
        id = entry.id,
        kind = entry.kind.name,
        title = entry.title,
        sourceName = entry.sourceName,
        sourceUrl = entry.sourceUrl,
        format = entry.format,
        maxHeight = entry.maxHeight,
        bitrateKbps = entry.bitrateKbps,
        fileLocation = entry.file.location,
        fileName = entry.file.displayName,
        fileMimeType = entry.file.mimeType,
        fileSizeBytes = entry.file.sizeBytes,
        thumbnailUrl = entry.thumbnailUrl,
        completedAt = entry.completedAt,
    )

    fun toDomain(row: HistoryEntity): HistoryEntry? {
        val kind = HistoryKind.entries.firstOrNull { it.name == row.kind } ?: return null
        return HistoryEntry(
            id = row.id,
            kind = kind,
            title = row.title,
            sourceName = row.sourceName,
            sourceUrl = row.sourceUrl,
            format = row.format,
            maxHeight = row.maxHeight,
            bitrateKbps = row.bitrateKbps,
            file = OutputFile(row.fileLocation, row.fileName, row.fileMimeType, row.fileSizeBytes),
            thumbnailUrl = row.thumbnailUrl,
            completedAt = row.completedAt,
        )
    }
}
