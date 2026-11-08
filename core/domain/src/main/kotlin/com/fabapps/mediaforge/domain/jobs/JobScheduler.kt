package com.fabapps.mediaforge.domain.jobs

import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job as Coroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Qué está haciendo la cola. Con `isBusy` la app decide si el servicio en primer plano tiene que estar vivo. */
data class SchedulerActivity(val runningDownloads: Int = 0, val runningConversions: Int = 0, val waiting: Int = 0) {
    val isBusy: Boolean get() = runningDownloads + runningConversions + waiting > 0
}

/**
 * Cola de descargas y conversiones (ADR-017). Corre los trabajos en [scope], que vive tanto como el
 * proceso; las pantallas y el servicio solo observan y piden.
 *
 * - En orden de llegada, con un límite por tipo ([Limits]).
 * - Al terminar el motor, guarda en la galería con [MediaLibrary].
 * - En el [JobStore] van los cambios de estado; el progreso en vivo está en [live].
 * - Los errores de red se reintentan solos ([Limits.networkRetryDelaysMs]); el resto, a mano.
 * - [start] recupera lo que quedó a medias si el proceso murió: vuelve a la cola mientras queden intentos.
 */
class JobScheduler(
    private val store: JobStore,
    private val downloader: MediaDownloader,
    private val converter: MediaConverter,
    private val library: MediaLibrary,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limits: Limits = Limits(),
) : JobQueue {
    data class Limits(
        val downloads: Int = 2,
        val conversions: Int = 1,
        /** Corridas permitidas antes de dar un trabajo por fallido o interrumpido. */
        val maxAttempts: Int = 3,
        /** Espera antes de cada reintento automático por error de red. */
        val networkRetryDelaysMs: List<Long> = listOf(5_000, 15_000),
    )

    private class Running(val isConversion: Boolean, val coroutine: Coroutine)

    private val mutex = Mutex()
    private val running = mutableMapOf<String, Running>()
    private val retryAt = mutableMapOf<String, Long>()
    private val saving = ConcurrentHashMap.newKeySet<String>()

    /** Por qué se cortó un trabajo desde afuera: `CANCELLED` o `INTERRUPTED`. */
    private val stopReasons = ConcurrentHashMap<String, JobStatus>()
    private var recovered = false
    private var paused = false

    private val _live = MutableStateFlow<Map<String, JobState>>(emptyMap())

    /** Último estado del motor de cada trabajo en curso (progreso, velocidad, fase). */
    override val live: StateFlow<Map<String, JobState>> = _live.asStateFlow()

    private val _activity = MutableStateFlow(SchedulerActivity())
    val activity: StateFlow<SchedulerActivity> = _activity.asStateFlow()

    private val _finished = MutableSharedFlow<Job>(extraBufferCapacity = 32)

    /** Trabajos que terminaron (completados, fallidos sin más reintentos o interrumpidos), para avisar. */
    val finished: SharedFlow<Job> = _finished.asSharedFlow()

    override fun observeAll(): Flow<List<Job>> = store.observeAll()

    override fun observe(id: String): Flow<Job?> = store.observe(id)

    /**
     * Arranca o reanuda la cola. La primera vez recupera los trabajos que figuraban en curso: el proceso
     * anterior murió sin terminarlos. Hay que llamarla con la app visible (el servicio no puede arrancar
     * desde segundo plano).
     */
    suspend fun start() {
        mutex.withLock {
            paused = false
            if (!recovered) {
                recovered = true
                recoverLocked()
            }
        }
        pump()
    }

    override suspend fun enqueue(request: JobRequest, title: String, sourceName: String?, thumbnailUrl: String?): Job {
        val job = Job(request, title, sourceName, thumbnailUrl, createdAt = clock())
        store.put(job)
        mutex.withLock { paused = false }
        pump()
        return job
    }

    /** Corta un trabajo en cola o en curso. No hace nada mientras se guarda en la galería. */
    override suspend fun cancel(id: String) {
        val coroutine = mutex.withLock {
            running[id]?.let { current ->
                if (id in saving) return
                stopReasons[id] = JobStatus.CANCELLED
                current.coroutine
            } ?: run {
                val job = store.get(id) ?: return
                if (job.status == JobStatus.QUEUED) {
                    retryAt.remove(id)
                    store.put(job.copy(status = JobStatus.CANCELLED, updatedAt = clock()))
                }
                null
            }
        }
        coroutine?.cancelAndJoin()
        pump()
    }

    /** Vuelve a poner en cola un trabajo fallido, cancelado o interrumpido, con los intentos en cero. */
    override suspend fun retry(id: String) {
        val job = store.get(id) ?: return
        if (!job.status.canRetry) return
        store.put(job.copy(status = JobStatus.QUEUED, error = null, output = null, attempts = 0, updatedAt = clock()))
        mutex.withLock { paused = false }
        pump()
    }

    /** Quita de la lista un trabajo terminado. El archivo de la galería queda. */
    override suspend fun delete(id: String) {
        val job = store.get(id) ?: return
        if (!job.status.canDelete) return
        store.delete(id)
    }

    /**
     * Android cortó el servicio (`onTimeout`): todo lo que corre queda interrumpido y la cola no sigue
     * hasta el próximo [start] o [enqueue].
     */
    suspend fun interruptAll() {
        val coroutines = mutex.withLock {
            paused = true
            running.forEach { (id, _) -> stopReasons[id] = JobStatus.INTERRUPTED }
            running.values.map { it.coroutine }
        }
        coroutines.forEach { it.cancelAndJoin() }
        mutex.withLock { updateActivityLocked() }
    }

    private suspend fun recoverLocked() {
        val stale = store.withStatus(setOf(JobStatus.RUNNING, JobStatus.SAVING))
        stale.forEach { job ->
            val status = if (job.attempts < limits.maxAttempts) JobStatus.QUEUED else JobStatus.INTERRUPTED
            val updated = job.copy(status = status, updatedAt = clock())
            store.put(updated)
            if (status == JobStatus.INTERRUPTED) _finished.tryEmit(updated)
        }
    }

    private suspend fun pump() {
        mutex.withLock {
            if (!paused) startEligibleLocked()
            updateActivityLocked()
        }
    }

    private suspend fun startEligibleLocked() {
        val now = clock()
        store.withStatus(setOf(JobStatus.QUEUED)).forEach { job ->
            if (job.id in running) return@forEach
            if ((retryAt[job.id] ?: 0) > now) return@forEach
            val isConversion = job.request is JobRequest.Conversion
            val busy = running.values.count { it.isConversion == isConversion }
            if (busy >= if (isConversion) limits.conversions else limits.downloads) return@forEach
            retryAt.remove(job.id)
            stopReasons.remove(job.id)
            val started = job.copy(
                status = JobStatus.RUNNING,
                attempts = job.attempts + 1,
                error = null,
                updatedAt = now,
            )
            store.put(started)
            setLive(job.id, JobState.Pending)
            running[job.id] = Running(isConversion, scope.launch { execute(started) })
        }
    }

    private suspend fun updateActivityLocked() {
        val waiting = if (paused) 0 else store.withStatus(setOf(JobStatus.QUEUED)).count { it.id !in running }
        _activity.value = SchedulerActivity(
            runningDownloads = running.values.count { !it.isConversion },
            runningConversions = running.values.count { it.isConversion },
            waiting = waiting,
        )
    }

    private suspend fun execute(job: Job) {
        try {
            run(job)
        } catch (e: CancellationException) {
            stopReasons.remove(job.id)?.let { reason ->
                withContext(NonCancellable) { finish(job, reason) }
            }
            throw e
        } catch (e: Exception) {
            finish(job, JobStatus.FAILED, MediaError.Unknown(e.message ?: e::class.simpleName))
        } finally {
            withContext(NonCancellable) {
                saving.remove(job.id)
                mutex.withLock { running.remove(job.id) }
                _live.update { it - job.id }
                pump()
            }
        }
    }

    private suspend fun run(job: Job) {
        var terminal: JobState? = null
        engineFor(job.request).collect { state ->
            when (state) {
                is JobState.Completed, is JobState.Failed, JobState.Cancelled -> terminal = state
                else -> setLive(job.id, state)
            }
        }
        when (val result = terminal) {
            is JobState.Completed -> save(job, result.outputs.firstOrNull())
            is JobState.Failed -> fail(job, result.error)
            JobState.Cancelled -> finish(job, JobStatus.CANCELLED)
            else -> finish(job, JobStatus.FAILED, MediaError.Unknown("engine finished without a result"))
        }
    }

    private fun engineFor(request: JobRequest): Flow<JobState> = when (request) {
        is JobRequest.Download -> downloader.download(request.request)
        is JobRequest.Conversion -> converter.convert(request.request)
    }

    private suspend fun save(job: Job, file: OutputFile?) {
        if (file == null) return finish(job, JobStatus.FAILED, MediaError.Unknown("engine produced no file"))
        saving.add(job.id)
        store.put(job.copy(status = JobStatus.SAVING, updatedAt = clock()))
        when (val saved = library.save(file)) {
            is Outcome.Success -> finish(job, JobStatus.COMPLETED, output = saved.value)
            is Outcome.Failure -> finish(job, JobStatus.FAILED, saved.error)
        }
    }

    private suspend fun fail(job: Job, error: MediaError) {
        val wait = limits.networkRetryDelaysMs.getOrNull(job.attempts - 1)
        if (error !is MediaError.Network || wait == null || job.attempts >= limits.maxAttempts) {
            return finish(job, JobStatus.FAILED, error)
        }
        mutex.withLock { retryAt[job.id] = clock() + wait }
        store.put(job.copy(status = JobStatus.QUEUED, error = error, updatedAt = clock()))
        scope.launch {
            delay(wait)
            pump()
        }
    }

    private suspend fun finish(job: Job, status: JobStatus, error: MediaError? = null, output: OutputFile? = null) {
        val done = job.copy(status = status, error = error, output = output, updatedAt = clock())
        store.put(done)
        if (status != JobStatus.CANCELLED) _finished.tryEmit(done)
    }

    private fun setLive(id: String, state: JobState) = _live.update { it + (id to state) }
}
