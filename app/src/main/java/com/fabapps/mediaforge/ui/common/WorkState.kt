package com.fabapps.mediaforge.ui.common

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobQueue
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

/** Estado de una tarea con resultado en la galería (descarga o conversión). */
sealed interface WorkState {
    val isActive: Boolean get() = this is Running || this is Saving

    /** Ya no va a cambiar solo. */
    val isFinal: Boolean get() = this is Saved || this is Failed || this is Cancelled || this is Interrupted

    data object None : WorkState

    /** En cola o en el motor: pendiente, bajando, uniendo pistas o convirtiendo. */
    data class Running(val job: JobState) : WorkState

    /** Copiando a la galería. */
    data object Saving : WorkState

    data class Saved(val file: OutputFile) : WorkState

    data class Failed(val error: MediaError) : WorkState

    data object Cancelled : WorkState

    /** El proceso murió o Android cortó el servicio, sin intentos automáticos restantes. */
    data object Interrupted : WorkState

    /** Android 8–9: el usuario no dio permiso de escritura. */
    data object PermissionDenied : WorkState

    companion object {
        /** Estado para la pantalla a partir del trabajo guardado y su progreso en vivo. */
        fun of(job: Job?, live: JobState?): WorkState = when (job?.status) {
            null, JobStatus.QUEUED -> Running(JobState.Pending)
            JobStatus.RUNNING -> Running(live ?: JobState.Pending)
            JobStatus.SAVING -> Saving
            JobStatus.COMPLETED -> job.output?.let(::Saved) ?: Failed(MediaError.Unknown("saved without a file"))
            JobStatus.FAILED -> Failed(job.error ?: MediaError.Unknown())
            JobStatus.CANCELLED -> Cancelled
            JobStatus.INTERRUPTED -> Interrupted
        }
    }
}

/** Sigue un trabajo de la cola hasta que termina; el último valor emitido es el final. */
fun JobQueue.workState(id: String): Flow<WorkState> =
    combine(observe(id), live.map { it[id] }.distinctUntilChanged(), WorkState::of)
        .distinctUntilChanged()
        .transformWhile { state ->
            emit(state)
            !state.isFinal
        }
