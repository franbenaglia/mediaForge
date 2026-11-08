package com.fabapps.mediaforge.ui.downloads

import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.jobs.InputAccess
import com.fabapps.mediaforge.testing.InMemoryJobStore
import com.fabapps.mediaforge.ui.common.WorkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Un canal por trabajo: la prueba decide qué emite cada uno. */
    private class FakeEngines :
        MediaDownloader,
        MediaConverter {
        val channels = mutableMapOf<String, Channel<JobState>>()

        fun channel(id: String) = channels.getOrPut(id) { Channel(Channel.UNLIMITED) }

        override fun download(request: DownloadRequest): Flow<JobState> = channel(request.jobId).consumeAsFlow()

        override fun convert(request: ConversionRequest): Flow<JobState> = channel(request.jobId).consumeAsFlow()
    }

    private class FakeLibrary : MediaLibrary {
        override suspend fun save(file: OutputFile): Outcome<OutputFile> =
            Outcome.Success(file.copy(location = "content://media/${file.displayName}"))

        override fun needsWritePermission() = false
    }

    private class FakeInputAccess : InputAccess {
        val released = mutableListOf<String>()
        override fun keep(uri: String) = Unit
        override fun release(uri: String) {
            released += uri
        }
    }

    private val engines = FakeEngines()
    private val store = InMemoryJobStore()
    private val inputAccess = FakeInputAccess()
    private val queueScope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var queue: JobScheduler
    private lateinit var vm: DownloadsViewModel
    private val state get() = vm.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        queue =
            JobScheduler(store, engines, engines, FakeLibrary(), queueScope, clock = {
                dispatcher.scheduler.currentTime
            })
        vm = DownloadsViewModel(queue, inputAccess)
    }

    @After
    fun tearDown() {
        queueScope.cancel()
        Dispatchers.resetMain()
    }

    /** `stateIn(WhileSubscribed)` solo se actualiza con alguien mirando, como la pantalla. */
    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        block()
    }

    private fun download(id: String) = JobRequest.Download(
        DownloadRequest(id, "https://example.com/$id", DownloadSelection.Video(maxHeight = 720), id),
    )

    private fun conversion(id: String) =
        JobRequest.Conversion(ConversionRequest(id, "content://docs/$id", ConversionTarget.Audio(AudioFormat.MP3), id))

    private fun item(id: String) = state.items.single { it.id == id }

    @Test
    fun startsEmptyAfterLoading() = test {
        assertTrue(state.loading)
        advanceUntilIdle()
        assertFalse(state.loading)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun showsLiveProgressAndQueuedJobsNewestFirst() = test {
        queue.enqueue(download("a"), "A", sourceName = "YouTube")
        advanceUntilIdle()
        queue.enqueue(download("b"), "B")
        queue.enqueue(download("c"), "C")
        val progress = JobState.Downloading(0.5f, 5, 10, 1)
        engines.channel("a").send(progress)
        advanceUntilIdle()

        assertEquals(listOf("c", "b", "a"), state.items.map { it.id })
        assertEquals(WorkState.Running(progress), item("a").work)
        assertTrue(item("a").canCancel)
        // Hay dos lugares para descargas: "b" corre, "c" espera.
        assertFalse(item("b").isQueued)
        assertTrue(item("c").isQueued)
    }

    @Test
    fun cancelRetryAndRemove() = test {
        queue.enqueue(download("a"), "A")
        advanceUntilIdle()

        vm.onCancel("a")
        advanceUntilIdle()
        assertEquals(WorkState.Cancelled, item("a").work)
        assertTrue(item("a").canRetry)
        assertTrue(item("a").canRemove)

        // Cancelar cierra el canal de la corrida anterior; el reintento usa uno nuevo.
        engines.channels.remove("a")
        vm.onRetry("a")
        advanceUntilIdle()
        assertEquals(JobStatus.RUNNING, item("a").job.status)
        assertFalse(item("a").canRemove)
        vm.onRemove("a")
        advanceUntilIdle()
        assertEquals(1, state.items.size)

        engines.channel("a").send(JobState.Failed(MediaError.AccessDenied()))
        engines.channel("a").close()
        advanceUntilIdle()
        assertEquals(WorkState.Failed(MediaError.AccessDenied()), item("a").work)
        vm.onRemove("a")
        advanceUntilIdle()
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun completedJobKeepsTheSavedFile() = test {
        queue.enqueue(download("a"), "A")
        advanceUntilIdle()
        engines.channel("a").send(JobState.Completed(listOf(OutputFile("/private/a", "A.mp4", "video/mp4", 42))))
        engines.channel("a").close()
        advanceUntilIdle()

        val saved = OutputFile("content://media/A.mp4", "A.mp4", "video/mp4", 42)
        assertEquals(WorkState.Saved(saved), item("a").work)
        assertEquals(saved, item("a").job.output)
    }

    @Test
    fun removingAConversionReleasesTheInputFile() = test {
        queue.enqueue(conversion("c"), "C")
        advanceUntilIdle()
        vm.onCancel("c")
        advanceUntilIdle()
        vm.onRemove("c")
        advanceUntilIdle()
        assertEquals(listOf("content://docs/c"), inputAccess.released)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun removeCompletedKeepsTheRest() = test {
        queue.enqueue(download("done"), "Done")
        queue.enqueue(download("run"), "Run")
        advanceUntilIdle()
        engines.channel("done").send(JobState.Completed(listOf(OutputFile("/p/d", "d.mp4", "video/mp4", 1))))
        engines.channel("done").close()
        advanceUntilIdle()
        assertTrue(state.hasCompleted)

        vm.onRemoveCompleted()
        advanceUntilIdle()
        assertEquals(listOf("run"), state.items.map { it.id })
        assertFalse(state.hasCompleted)
    }

    @Test
    fun detailsOpenOneAtATime() = test {
        queue.enqueue(download("a"), "A")
        queue.enqueue(download("b"), "B")
        advanceUntilIdle()
        vm.onToggleDetails("a")
        advanceUntilIdle()
        assertEquals("a", state.expandedId)
        vm.onToggleDetails("b")
        advanceUntilIdle()
        assertEquals("b", state.expandedId)
        vm.onToggleDetails("b")
        advanceUntilIdle()
        assertNull(state.expandedId)
    }
}
