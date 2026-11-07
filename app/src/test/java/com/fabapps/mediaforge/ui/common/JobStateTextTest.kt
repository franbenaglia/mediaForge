package com.fabapps.mediaforge.ui.common

import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.ProcessingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobStateTextTest {

    private val allStates: List<JobState> = listOf(
        JobState.Pending,
        JobState.Analyzing,
        JobState.Downloading(0.5f, 10, 20, 5),
        JobState.Completed(emptyList()),
        JobState.Failed(MediaError.Unknown()),
        JobState.Cancelled,
    ) + ProcessingStep.entries.map { JobState.Processing(it, null) }

    private val allErrors: List<MediaError> = listOf(
        MediaError.InvalidUrl(),
        MediaError.UnsupportedSite(),
        MediaError.LoginRequired(),
        MediaError.AccessDenied(),
        MediaError.Network(),
        MediaError.InsufficientStorage(null, null),
        MediaError.EngineOutdated(),
        MediaError.Unknown(),
    )

    @Test
    fun everyStateHasItsOwnText() {
        val ids = allStates.map { it.statusRes() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyErrorHasItsOwnMessage() {
        val ids = allErrors.map { it.messageRes() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun finishedStatesAreNotActive() {
        assertFalse(JobState.Completed(emptyList()).isActive)
        assertFalse(JobState.Failed(MediaError.Unknown()).isActive)
        assertFalse(JobState.Cancelled.isActive)
        assertTrue(JobState.Pending.isActive)
        assertTrue(JobState.Processing(ProcessingStep.MERGE, 0.1f).isActive)
    }

    @Test
    fun progressIsClampedAndNullWhenUnknown() {
        assertEquals(1f, JobState.Downloading(1.7f, null, null, null).progressFraction)
        assertEquals(0f, JobState.Processing(ProcessingStep.TRANSCODE, -0.2f).progressFraction)
        assertNull(JobState.Downloading(null, null, null, null).progressFraction)
        assertNull(JobState.Pending.progressFraction)
    }
}
