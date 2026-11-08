package com.fabapps.mediaforge.domain.jobs

import com.fabapps.mediaforge.domain.model.JobState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lo que las pantallas pueden pedirle a la cola. La implementa [JobScheduler]. */
interface JobQueue {
    /** Último estado del motor de cada trabajo en curso. */
    val live: StateFlow<Map<String, JobState>>

    fun observeAll(): Flow<List<Job>>

    fun observe(id: String): Flow<Job?>

    suspend fun enqueue(
        request: JobRequest,
        title: String,
        sourceName: String? = null,
        thumbnailUrl: String? = null,
    ): Job

    suspend fun cancel(id: String)

    suspend fun retry(id: String)

    suspend fun delete(id: String)
}
