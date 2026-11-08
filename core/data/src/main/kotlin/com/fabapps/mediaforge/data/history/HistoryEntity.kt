package com.fabapps.mediaforge.data.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Fila del historial (ADR-019). El tipo se guarda por nombre. */
@Entity(tableName = "history", indices = [Index("completedAt")])
data class HistoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val sourceName: String?,
    val sourceUrl: String?,
    val format: String,
    val maxHeight: Int?,
    val bitrateKbps: Int?,
    val fileLocation: String,
    val fileName: String,
    val fileMimeType: String,
    val fileSizeBytes: Long,
    val thumbnailUrl: String?,
    val completedAt: Long,
)

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY completedAt DESC, rowid DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: String)
}
