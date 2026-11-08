package com.fabapps.mediaforge.domain.jobs

import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import kotlinx.coroutines.flow.Flow

/** Lo que hay que hacer: una descarga o una conversión. Lleva el pedido completo para poder repetirlo. */
sealed interface JobRequest {
    val jobId: String

    data class Download(val request: DownloadRequest) : JobRequest {
        override val jobId: String get() = request.jobId
    }

    data class Conversion(val request: ConversionRequest) : JobRequest {
        override val jobId: String get() = request.jobId
    }
}

/** Estado que se guarda. El progreso fino de un trabajo en curso vive en memoria (ver `JobScheduler`). */
enum class JobStatus {
    QUEUED,
    RUNNING,

    /** Copiando a la galería. No se puede cancelar: dejaría un archivo a medias. */
    SAVING,
    COMPLETED,
    FAILED,
    CANCELLED,

    /** El proceso murió o Android cortó el servicio, sin intentos automáticos restantes. */
    INTERRUPTED,
    ;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == SAVING
    val canCancel: Boolean get() = this == QUEUED || this == RUNNING
    val canRetry: Boolean get() = this == FAILED || this == CANCELLED || this == INTERRUPTED

    /** Borrar quita el trabajo de la lista. El archivo guardado en la galería no se toca. */
    val canDelete: Boolean get() = !isActive
}

data class Job(
    val request: JobRequest,
    /** Título del recurso o nombre del archivo de entrada. */
    val title: String,
    /** Nombre del sitio de origen (por ejemplo, YouTube). Nulo en las conversiones. */
    val sourceName: String? = null,
    val thumbnailUrl: String? = null,
    val status: JobStatus = JobStatus.QUEUED,
    /** Solo en `FAILED`. */
    val error: MediaError? = null,
    /** Archivo guardado en la galería, solo en `COMPLETED`. */
    val output: OutputFile? = null,
    /** Cuántas veces empezó a correr. */
    val attempts: Int = 0,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
) {
    val id: String get() = request.jobId
}

/** Dónde se guardan los trabajos. La implementación (Room) vive en `:core:data`. */
interface JobStore {
    /** Inserta o reemplaza por id. */
    suspend fun put(job: Job)

    suspend fun get(id: String): Job?

    /** Trabajos con alguno de esos estados, del más viejo al más nuevo. */
    suspend fun withStatus(statuses: Set<JobStatus>): List<Job>

    /** Todos, del más nuevo al más viejo. */
    fun observeAll(): Flow<List<Job>>

    fun observe(id: String): Flow<Job?>

    suspend fun delete(id: String)
}
