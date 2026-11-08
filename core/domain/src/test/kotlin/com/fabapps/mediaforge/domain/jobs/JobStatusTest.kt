package com.fabapps.mediaforge.domain.jobs

import com.fabapps.mediaforge.domain.jobs.JobStatus.CANCELLED
import com.fabapps.mediaforge.domain.jobs.JobStatus.COMPLETED
import com.fabapps.mediaforge.domain.jobs.JobStatus.FAILED
import com.fabapps.mediaforge.domain.jobs.JobStatus.INTERRUPTED
import com.fabapps.mediaforge.domain.jobs.JobStatus.QUEUED
import com.fabapps.mediaforge.domain.jobs.JobStatus.RUNNING
import com.fabapps.mediaforge.domain.jobs.JobStatus.SAVING
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class JobStatusTest {
    private fun all(predicate: (JobStatus) -> Boolean) = JobStatus.entries.filter(predicate).toSet()

    @Test
    fun `active states are queued running and saving`() {
        assertEquals(setOf(QUEUED, RUNNING, SAVING), all { it.isActive })
    }

    @Test
    fun `saving cannot be cancelled`() {
        assertEquals(setOf(QUEUED, RUNNING), all { it.canCancel })
    }

    @Test
    fun `only unfinished outcomes can be retried`() {
        assertEquals(setOf(FAILED, CANCELLED, INTERRUPTED), all { it.canRetry })
    }

    @Test
    fun `only finished jobs can be deleted`() {
        assertEquals(setOf(COMPLETED, FAILED, CANCELLED, INTERRUPTED), all { it.canDelete })
    }

    @Test
    fun `job id comes from its request`() {
        val request = DownloadRequest("abc", "https://example.com/v", DownloadSelection.Video(), "video")
        val job = Job(JobRequest.Download(request), title = "Video", createdAt = 5)
        assertEquals("abc", job.id)
        assertEquals(5, job.updatedAt)
    }
}
