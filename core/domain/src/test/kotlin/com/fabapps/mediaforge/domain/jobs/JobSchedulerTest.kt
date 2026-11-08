package com.fabapps.mediaforge.domain.jobs

import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobSchedulerTest {
    private class FakeStore : JobStore {
        val jobs = MutableStateFlow<Map<String, Job>>(linkedMapOf())

        override suspend fun put(job: Job) {
            jobs.value = LinkedHashMap(jobs.value).apply { put(job.id, job) }
        }

        override suspend fun get(id: String): Job? = jobs.value[id]

        override suspend fun withStatus(statuses: Set<JobStatus>): List<Job> =
            jobs.value.values.filter { it.status in statuses }.sortedBy { it.createdAt }

        override fun observeAll(): Flow<List<Job>> = jobs.map { all -> all.values.sortedByDescending { it.createdAt } }

        override fun observe(id: String): Flow<Job?> = jobs.map { it[id] }

        override suspend fun delete(id: String) {
            jobs.value = LinkedHashMap(jobs.value).apply { remove(id) }
        }

        fun status(id: String) = jobs.value.getValue(id).status
    }

    /** Cada corrida emite un avance y espera a que la prueba diga cómo termina, salvo que haya una respuesta fija. */
    private class FakeEngine :
        MediaDownloader,
        MediaConverter {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val fixedResult = mutableMapOf<String, JobState>()
        val failWithException = mutableSetOf<String>()
        private val gates = mutableMapOf<String, CompletableDeferred<JobState>>()

        fun finish(id: String, state: JobState) = gates.getValue(id).complete(state)

        fun complete(id: String) =
            finish(id, JobState.Completed(listOf(OutputFile("/private/$id", "$id.mp4", "video/mp4", 10))))

        private fun run(id: String): Flow<JobState> = flow {
            started += id
            if (id in failWithException) error("boom")
            emit(JobState.Downloading(0.5f, 50, 100, 10))
            fixedResult[id]?.let { return@flow emit(it) }
            val gate = CompletableDeferred<JobState>().also { gates[id] = it }
            try {
                emit(gate.await())
            } catch (e: CancellationException) {
                cancelled += id
                throw e
            }
        }

        override fun download(request: DownloadRequest) = run(request.jobId)

        override fun convert(request: ConversionRequest) = run(request.jobId)
    }

    private class FakeLibrary : MediaLibrary {
        var failure: MediaError? = null

        override suspend fun save(file: OutputFile): Outcome<OutputFile> = failure?.let { Outcome.Failure(it) }
            ?: Outcome.Success(file.copy(location = "content://saved/${file.displayName}"))

        override fun needsWritePermission() = false
    }

    private val store = FakeStore()
    private val engine = FakeEngine()
    private val library = FakeLibrary()

    private fun TestScope.scheduler(limits: JobScheduler.Limits = JobScheduler.Limits()) =
        JobScheduler(store, engine, engine, library, backgroundScope, clock = {
            testScheduler.currentTime
        }, limits = limits)

    private fun download(id: String) =
        JobRequest.Download(DownloadRequest(id, "https://example.com/$id", DownloadSelection.Video(), id))

    private fun conversion(id: String) =
        JobRequest.Conversion(ConversionRequest(id, "content://in/$id", ConversionTarget.Audio(AudioFormat.MP3), id))

    @Test
    fun `download runs saves and completes`() = runTest {
        val scheduler = scheduler()
        val finished = mutableListOf<Job>()
        backgroundScope.launchCollect(scheduler) { finished += it }
        scheduler.enqueue(download("a"), title = "A", sourceName = "YouTube")
        runCurrent()

        assertEquals(JobStatus.RUNNING, store.status("a"))
        assertEquals(JobState.Downloading(0.5f, 50, 100, 10), scheduler.live.value["a"])
        assertTrue(scheduler.activity.value.isBusy)

        engine.complete("a")
        runCurrent()

        val job = store.jobs.value.getValue("a")
        assertEquals(JobStatus.COMPLETED, job.status)
        assertEquals("content://saved/a.mp4", job.output?.location)
        assertEquals(1, job.attempts)
        assertEquals("YouTube", job.sourceName)
        assertNull(scheduler.live.value["a"])
        assertFalse(scheduler.activity.value.isBusy)
        assertEquals(listOf("a"), finished.map { it.id })
    }

    @Test
    fun `at most two downloads and one conversion run at once, in order`() = runTest {
        val scheduler = scheduler()
        listOf("d1", "d2", "d3").forEach { scheduler.enqueue(download(it), it) }
        scheduler.enqueue(conversion("c1"), "c1")
        scheduler.enqueue(conversion("c2"), "c2")
        runCurrent()

        assertEquals(listOf("d1", "d2", "c1"), engine.started)
        assertEquals(
            SchedulerActivity(runningDownloads = 2, runningConversions = 1, waiting = 2),
            scheduler.activity.value,
        )

        engine.complete("d1")
        engine.complete("c1")
        runCurrent()
        assertEquals(listOf("d1", "d2", "c1", "d3", "c2"), engine.started)
    }

    @Test
    fun `cancelling a running job stops the engine and frees the slot`() = runTest {
        val scheduler = scheduler(JobScheduler.Limits(downloads = 1))
        scheduler.enqueue(download("a"), "a")
        scheduler.enqueue(download("b"), "b")
        runCurrent()

        scheduler.cancel("a")
        runCurrent()

        assertEquals(listOf("a"), engine.cancelled)
        assertEquals(JobStatus.CANCELLED, store.status("a"))
        assertEquals(JobStatus.RUNNING, store.status("b"))
    }

    @Test
    fun `cancelling a queued job means it never starts`() = runTest {
        val scheduler = scheduler(JobScheduler.Limits(downloads = 1))
        scheduler.enqueue(download("a"), "a")
        scheduler.enqueue(download("b"), "b")
        runCurrent()

        scheduler.cancel("b")
        engine.complete("a")
        runCurrent()

        assertEquals(listOf("a"), engine.started)
        assertEquals(JobStatus.CANCELLED, store.status("b"))
    }

    @Test
    fun `network errors are retried after 5 and 15 seconds, then fail`() = runTest {
        val scheduler = scheduler()
        engine.fixedResult["a"] = JobState.Failed(MediaError.Network("timeout"))
        scheduler.enqueue(download("a"), "a")
        runCurrent()
        assertEquals(1, engine.started.size)
        assertEquals(JobStatus.QUEUED, store.status("a"))
        assertTrue("a retry keeps the service alive", scheduler.activity.value.isBusy)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, engine.started.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, engine.started.size)

        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(3, engine.started.size)
        val job = store.jobs.value.getValue("a")
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals(3, job.attempts)
        assertTrue(job.error is MediaError.Network)
    }

    @Test
    fun `other errors fail without retrying`() = runTest {
        val scheduler = scheduler()
        engine.fixedResult["a"] = JobState.Failed(MediaError.LoginRequired())
        scheduler.enqueue(download("a"), "a")
        runCurrent()
        advanceTimeBy(60_000)

        assertEquals(1, engine.started.size)
        assertEquals(JobStatus.FAILED, store.status("a"))
    }

    @Test
    fun `an exception from the engine becomes an unknown error`() = runTest {
        val scheduler = scheduler()
        engine.failWithException += "a"
        scheduler.enqueue(download("a"), "a")
        runCurrent()

        val job = store.jobs.value.getValue("a")
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals(MediaError.Unknown("boom"), job.error)
    }

    @Test
    fun `a failed save fails the job`() = runTest {
        val scheduler = scheduler()
        library.failure = MediaError.InsufficientStorage(null, null)
        scheduler.enqueue(download("a"), "a")
        runCurrent()
        engine.complete("a")
        runCurrent()

        assertEquals(JobStatus.FAILED, store.status("a"))
        assertEquals(library.failure, store.jobs.value.getValue("a").error)
    }

    @Test
    fun `manual retry starts again with fresh attempts`() = runTest {
        val scheduler = scheduler()
        engine.fixedResult["a"] = JobState.Failed(MediaError.AccessDenied())
        scheduler.enqueue(download("a"), "a")
        runCurrent()
        engine.fixedResult.remove("a")

        scheduler.retry("a")
        runCurrent()
        assertEquals(JobStatus.RUNNING, store.status("a"))
        assertEquals(1, store.jobs.value.getValue("a").attempts)
        assertNull(store.jobs.value.getValue("a").error)

        engine.complete("a")
        runCurrent()
        assertEquals(JobStatus.COMPLETED, store.status("a"))
    }

    @Test
    fun `only finished jobs can be deleted`() = runTest {
        val scheduler = scheduler()
        scheduler.enqueue(download("a"), "a")
        runCurrent()
        scheduler.delete("a")
        assertEquals(JobStatus.RUNNING, store.status("a"))

        engine.complete("a")
        runCurrent()
        scheduler.delete("a")
        assertNull(store.jobs.value["a"])
    }

    @Test
    fun `start puts stale jobs back in the queue while attempts remain`() = runTest {
        store.put(Job(download("again"), "again", status = JobStatus.RUNNING, attempts = 1, createdAt = 1))
        store.put(Job(download("saving"), "saving", status = JobStatus.SAVING, attempts = 2, createdAt = 2))
        store.put(Job(download("spent"), "spent", status = JobStatus.RUNNING, attempts = 3, createdAt = 3))
        val scheduler = scheduler()
        val finished = mutableListOf<Job>()
        backgroundScope.launchCollect(scheduler) { finished += it }
        runCurrent()

        scheduler.start()
        runCurrent()

        assertEquals(listOf("again", "saving"), engine.started)
        assertEquals(2, store.jobs.value.getValue("again").attempts)
        assertEquals(JobStatus.INTERRUPTED, store.status("spent"))
        assertEquals(listOf("spent"), finished.map { it.id })

        // Una segunda llamada (la app vuelve al frente) no toca los trabajos en curso.
        scheduler.start()
        runCurrent()
        assertEquals(listOf("again", "saving"), engine.started)
    }

    @Test
    fun `interrupting stops running jobs and holds the queue until the next start`() = runTest {
        val scheduler = scheduler(JobScheduler.Limits(downloads = 1))
        scheduler.enqueue(download("a"), "a")
        scheduler.enqueue(download("b"), "b")
        runCurrent()

        scheduler.interruptAll()
        runCurrent()

        assertEquals(JobStatus.INTERRUPTED, store.status("a"))
        assertEquals(JobStatus.QUEUED, store.status("b"))
        assertEquals(listOf("a"), engine.started)
        assertFalse(scheduler.activity.value.isBusy)

        scheduler.start()
        runCurrent()
        assertEquals(listOf("a", "b"), engine.started)
    }

    /** Se suscribe antes de que el scheduler emita: las dos cosas corren en el mismo despachador, en orden. */
    private fun CoroutineScope.launchCollect(scheduler: JobScheduler, block: (Job) -> Unit) = launch {
        scheduler.finished.collect { block(it) }
    }
}
