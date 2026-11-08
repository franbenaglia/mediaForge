package com.fabapps.mediaforge.data.jobs

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.jobs.JobStore
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomJobStore(private val dao: JobDao) : JobStore {
    override suspend fun put(job: Job) = dao.upsert(JobMapper.toEntity(job))

    override suspend fun get(id: String): Job? = dao.get(id)?.let(JobMapper::toDomain)

    override suspend fun withStatus(statuses: Set<JobStatus>): List<Job> =
        dao.withStatus(statuses.map { it.name }).mapNotNull(JobMapper::toDomain)

    override fun observeAll(): Flow<List<Job>> = dao.observeAll().map { rows -> rows.mapNotNull(JobMapper::toDomain) }

    override fun observe(id: String): Flow<Job?> = dao.observe(id).map { it?.let(JobMapper::toDomain) }

    override suspend fun delete(id: String) = dao.delete(id)
}

/** Ida y vuelta entre [Job] y [JobEntity]. Una fila ilegible se descarta en lugar de romper la lista. */
object JobMapper {
    fun toEntity(job: Job): JobEntity {
        val error = job.error
        val output = job.output
        return JobEntity(
            id = job.id,
            requestJson = RequestJson.encode(job.request),
            requestVersion = RequestJson.VERSION,
            title = job.title,
            sourceName = job.sourceName,
            thumbnailUrl = job.thumbnailUrl,
            status = job.status.name,
            errorType = error?.let { ErrorType.of(it).name },
            errorDetail = error?.detail,
            errorRequiredBytes = (error as? MediaError.InsufficientStorage)?.requiredBytes,
            errorAvailableBytes = (error as? MediaError.InsufficientStorage)?.availableBytes,
            outputLocation = output?.location,
            outputName = output?.displayName,
            outputMimeType = output?.mimeType,
            outputSizeBytes = output?.sizeBytes,
            attempts = job.attempts,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )
    }

    fun toDomain(row: JobEntity): Job? {
        val request = RequestJson.decode(row.requestJson, row.requestVersion) ?: return null
        // Un estado desconocido (de una versión más nueva de la app) se muestra como interrumpido.
        val status = JobStatus.entries.firstOrNull { it.name == row.status } ?: JobStatus.INTERRUPTED
        return Job(
            request = request,
            title = row.title,
            sourceName = row.sourceName,
            thumbnailUrl = row.thumbnailUrl,
            status = status,
            error = row.errorType?.let { error(it, row) },
            output = output(row),
            attempts = row.attempts,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )
    }

    private fun output(row: JobEntity): OutputFile? {
        val location = row.outputLocation ?: return null
        return OutputFile(location, row.outputName.orEmpty(), row.outputMimeType.orEmpty(), row.outputSizeBytes ?: 0)
    }

    private fun error(type: String, row: JobEntity): MediaError {
        val detail = row.errorDetail
        return when (ErrorType.entries.firstOrNull { it.name == type }) {
            ErrorType.INVALID_URL -> MediaError.InvalidUrl(detail)
            ErrorType.UNSUPPORTED_SITE -> MediaError.UnsupportedSite(detail)
            ErrorType.LOGIN_REQUIRED -> MediaError.LoginRequired(detail)
            ErrorType.ACCESS_DENIED -> MediaError.AccessDenied(detail)
            ErrorType.NETWORK -> MediaError.Network(detail)
            ErrorType.INSUFFICIENT_STORAGE ->
                MediaError.InsufficientStorage(row.errorRequiredBytes, row.errorAvailableBytes, detail)
            ErrorType.UNSUPPORTED_CONVERSION -> MediaError.UnsupportedConversion(detail)
            ErrorType.ENGINE_OUTDATED -> MediaError.EngineOutdated(detail)
            ErrorType.UNKNOWN, null -> MediaError.Unknown(detail)
        }
    }

    /** Nombres guardados en la base: no cambiarlos sin migración. */
    private enum class ErrorType {
        INVALID_URL,
        UNSUPPORTED_SITE,
        LOGIN_REQUIRED,
        ACCESS_DENIED,
        NETWORK,
        INSUFFICIENT_STORAGE,
        UNSUPPORTED_CONVERSION,
        ENGINE_OUTDATED,
        UNKNOWN,
        ;

        companion object {
            fun of(error: MediaError): ErrorType = when (error) {
                is MediaError.InvalidUrl -> INVALID_URL
                is MediaError.UnsupportedSite -> UNSUPPORTED_SITE
                is MediaError.LoginRequired -> LOGIN_REQUIRED
                is MediaError.AccessDenied -> ACCESS_DENIED
                is MediaError.Network -> NETWORK
                is MediaError.InsufficientStorage -> INSUFFICIENT_STORAGE
                is MediaError.UnsupportedConversion -> UNSUPPORTED_CONVERSION
                is MediaError.EngineOutdated -> ENGINE_OUTDATED
                is MediaError.Unknown -> UNKNOWN
            }
        }
    }
}
