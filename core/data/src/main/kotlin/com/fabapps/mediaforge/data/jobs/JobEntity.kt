package com.fabapps.mediaforge.data.jobs

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.fabapps.mediaforge.data.history.HistoryDao
import com.fabapps.mediaforge.data.history.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Fila de un trabajo. Los enums se guardan por nombre y el pedido como JSON con versión ([RequestJson]):
 * así un cambio en el dominio no rompe filas viejas en silencio.
 */
@Entity(tableName = "jobs", indices = [Index("status"), Index("createdAt")])
data class JobEntity(
    @PrimaryKey val id: String,
    val requestJson: String,
    val requestVersion: Int,
    val title: String,
    val sourceName: String?,
    val thumbnailUrl: String?,
    val status: String,
    val errorType: String?,
    val errorDetail: String?,
    val errorRequiredBytes: Long?,
    val errorAvailableBytes: Long?,
    val outputLocation: String?,
    val outputName: String?,
    val outputMimeType: String?,
    val outputSizeBytes: Long?,
    val attempts: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface JobDao {
    @Upsert
    suspend fun upsert(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    /** Del más viejo al más nuevo; `rowid` desempata trabajos creados en el mismo milisegundo. */
    @Query("SELECT * FROM jobs WHERE status IN (:statuses) ORDER BY createdAt ASC, rowid ASC")
    suspend fun withStatus(statuses: List<String>): List<JobEntity>

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC, rowid DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)
}

/** Versión 2: tabla `history` (ADR-019). Cada versión exporta su esquema a `core/data/schemas/`. */
@Database(
    entities = [JobEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MediaForgeDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao

    abstract fun history(): HistoryDao

    companion object {
        const val NAME = "mediaforge.db"
    }
}
