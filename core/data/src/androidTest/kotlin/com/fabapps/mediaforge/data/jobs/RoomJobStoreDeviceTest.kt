package com.fabapps.mediaforge.data.jobs

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** DAO real sobre SQLite del teléfono, en memoria. */
@RunWith(AndroidJUnit4::class)
class RoomJobStoreDeviceTest {
    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        MediaForgeDatabase::class.java,
    ).build()
    private val store = RoomJobStore(database.jobs())

    @After
    fun close() = database.close()

    private fun job(id: String, createdAt: Long, status: JobStatus = JobStatus.QUEUED) = Job(
        request = JobRequest.Download(DownloadRequest(id, "https://example.com/$id", DownloadSelection.Video(), id)),
        title = id,
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun putGetUpdateAndDelete() = runTest {
        store.put(job("a", 1))
        assertEquals(job("a", 1), store.get("a"))

        store.put(job("a", 1, JobStatus.RUNNING).copy(attempts = 1))
        assertEquals(JobStatus.RUNNING, store.get("a")?.status)
        assertEquals(1, store.observeAll().first().size)

        store.delete("a")
        assertNull(store.get("a"))
        assertNull(store.observe("a").first())
    }

    @Test
    fun ordersByCreationAndFiltersByStatus() = runTest {
        store.put(job("late", 5))
        store.put(job("same-1", 2))
        store.put(job("same-2", 2))
        store.put(job("done", 1, JobStatus.COMPLETED))
        // Actualizar no cambia el lugar en la cola.
        store.put(job("same-1", 2).copy(title = "renamed"))

        assertEquals(listOf("same-1", "same-2", "late"), store.withStatus(setOf(JobStatus.QUEUED)).map { it.id })
        assertEquals(listOf("late", "same-2", "same-1", "done"), store.observeAll().first().map { it.id })
    }
}
