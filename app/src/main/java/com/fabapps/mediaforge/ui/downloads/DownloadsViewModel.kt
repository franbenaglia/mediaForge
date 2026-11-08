package com.fabapps.mediaforge.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobQueue
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.jobs.InputAccess
import com.fabapps.mediaforge.ui.common.WorkState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un trabajo de la lista, con su estado para la pantalla. */
data class JobItem(val job: Job, val work: WorkState) {
    val id: String get() = job.id
    val isConversion: Boolean get() = job.request is JobRequest.Conversion

    /** En cola esperando un reintento automático después de un error de red. */
    val waitingRetry: Boolean get() = job.status == JobStatus.QUEUED && job.error != null
    val isQueued: Boolean get() = job.status == JobStatus.QUEUED
    val canCancel: Boolean get() = job.status.canCancel
    val canRetry: Boolean get() = job.status.canRetry
    val canRemove: Boolean get() = job.status.canDelete
}

data class DownloadsUiState(
    val loading: Boolean = true,
    /** Del más nuevo al más viejo. */
    val items: List<JobItem> = emptyList(),
    /** Trabajo con los detalles abiertos. */
    val expandedId: String? = null,
) {
    /** Hay completados para quitar de una vez; siguen en el Historial. */
    val hasCompleted: Boolean get() = items.any { it.job.status == JobStatus.COMPLETED }
}

/** Administrador de descargas y conversiones (main.md §7.3, §7.4). */
@HiltViewModel
class DownloadsViewModel @Inject constructor(private val queue: JobQueue, private val inputAccess: InputAccess) :
    ViewModel() {
    private val expanded = MutableStateFlow<String?>(null)

    val state: StateFlow<DownloadsUiState> =
        combine(queue.observeAll(), queue.live, expanded) { jobs, live, open ->
            DownloadsUiState(
                loading = false,
                items = jobs.map { JobItem(it, WorkState.of(it, live[it.id])) },
                expandedId = open?.takeIf { id -> jobs.any { it.id == id } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadsUiState())

    fun onCancel(id: String) {
        viewModelScope.launch { queue.cancel(id) }
    }

    fun onRetry(id: String) {
        viewModelScope.launch { queue.retry(id) }
    }

    /** Quita el trabajo de la lista; el archivo guardado queda. Una conversión suelta el permiso del original. */
    fun onRemove(id: String) {
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        if (!item.canRemove) return
        viewModelScope.launch {
            queue.delete(id)
            (item.job.request as? JobRequest.Conversion)?.let { inputAccess.release(it.request.input) }
        }
    }

    /** Quita de la lista los completados. Siguen en el Historial y el archivo queda. */
    fun onRemoveCompleted() {
        val ids = state.value.items.filter { it.job.status == JobStatus.COMPLETED }.map { it.id }
        viewModelScope.launch { ids.forEach { queue.delete(it) } }
    }

    fun onToggleDetails(id: String) = expanded.update { if (it == id) null else id }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
