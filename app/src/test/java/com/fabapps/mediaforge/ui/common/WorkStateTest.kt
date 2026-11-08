package com.fabapps.mediaforge.ui.common

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkStateTest {
    private fun job(status: JobStatus, error: MediaError? = null, output: OutputFile? = null) = Job(
        JobRequest.Download(DownloadRequest("a", "https://example.com/a", DownloadSelection.Video(), "a")),
        title = "a",
        status = status,
        error = error,
        output = output,
        createdAt = 0,
    )

    private val progress = JobState.Downloading(0.3f, 3, 10, 1)

    @Test
    fun `queued and running show the live engine state`() {
        assertEquals(WorkState.Running(JobState.Pending), WorkState.of(null, null))
        assertEquals(WorkState.Running(JobState.Pending), WorkState.of(job(JobStatus.QUEUED), null))
        assertEquals(WorkState.Running(progress), WorkState.of(job(JobStatus.RUNNING), progress))
        assertEquals(WorkState.Saving, WorkState.of(job(JobStatus.SAVING), progress))
    }

    @Test
    fun `finished jobs map to final states`() {
        val file = OutputFile("content://media/1", "a.mp4", "video/mp4", 1)
        assertEquals(WorkState.Saved(file), WorkState.of(job(JobStatus.COMPLETED, output = file), null))
        assertEquals(
            WorkState.Failed(MediaError.Network()),
            WorkState.of(job(JobStatus.FAILED, MediaError.Network()), null),
        )
        assertEquals(WorkState.Cancelled, WorkState.of(job(JobStatus.CANCELLED), null))
        assertEquals(WorkState.Interrupted, WorkState.of(job(JobStatus.INTERRUPTED), null))
        assertTrue(WorkState.of(job(JobStatus.INTERRUPTED), null).isFinal)
        assertFalse(WorkState.of(job(JobStatus.RUNNING), progress).isFinal)
    }
}
