package com.fabapps.mediaforge.testing

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.jobs.JobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** `JobStore` en memoria para correr el `JobScheduler` real en las pruebas de ViewModels. */
class InMemoryJobStore : JobStore {
    val jobs = MutableStateFlow<Map<String, Job>>(linkedMapOf())

    override suspend fun put(job: Job) {
        jobs.value = LinkedHashMap(jobs.value).apply { put(job.id, job) }
    }

    override suspend fun get(id: String): Job? = jobs.value[id]

    override suspend fun withStatus(statuses: Set<JobStatus>): List<Job> =
        jobs.value.values.filter { it.status in statuses }.sortedBy { it.createdAt }

    /** Como Room (`createdAt DESC, rowid DESC`): a igual fecha, el último insertado primero. */
    override fun observeAll(): Flow<List<Job>> =
        jobs.map { all -> all.values.reversed().sortedByDescending { it.createdAt } }

    override fun observe(id: String): Flow<Job?> = jobs.map { it[id] }

    override suspend fun delete(id: String) {
        jobs.value = LinkedHashMap(jobs.value).apply { remove(id) }
    }
}
