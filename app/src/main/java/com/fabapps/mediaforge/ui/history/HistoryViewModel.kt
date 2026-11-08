package com.fabapps.mediaforge.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryFilter
import com.fabapps.mediaforge.domain.history.HistorySearch
import com.fabapps.mediaforge.domain.history.HistoryStore
import com.fabapps.mediaforge.files.DeleteConsent
import com.fabapps.mediaforge.files.DeleteResult
import com.fabapps.mediaforge.files.SavedFiles
import com.fabapps.mediaforge.share.ShareInbox
import com.fabapps.mediaforge.share.SharedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryItem(val entry: HistoryEntry, val missing: Boolean) {
    val id: String get() = entry.id
    val canRedownload: Boolean get() = entry.sourceUrl != null
}

enum class HistoryMessage { FILE_DELETED, DELETE_FAILED }

/** El sistema tiene que confirmar el borrado de [id]. [shown] evita volver a pedirlo al girar la pantalla. */
data class PendingConsent(val id: String, val consent: DeleteConsent, val shown: Boolean = false)

data class HistoryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** Lo que coincide con la búsqueda y el filtro, del más nuevo al más viejo. */
    val items: List<HistoryItem> = emptyList(),
    /** No hay nada en el historial (distinto de "sin resultados"). */
    val isEmpty: Boolean = true,
    val confirmDelete: HistoryItem? = null,
    val consent: PendingConsent? = null,
    val message: HistoryMessage? = null,
)

/** Historial de descargas y conversiones (main.md §9, ADR-019). */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val store: HistoryStore,
    private val files: SavedFiles,
    private val shareInbox: ShareInbox,
) : ViewModel() {
    private data class Ui(
        val query: String = "",
        val filter: HistoryFilter = HistoryFilter.ALL,
        val confirmDeleteId: String? = null,
        val consent: PendingConsent? = null,
        val message: HistoryMessage? = null,
    )

    private val ui = MutableStateFlow(Ui())

    /** Archivos que ya no están, por id. Se revisa al cambiar la lista y al volver a la pantalla. */
    private val missing = MutableStateFlow<Set<String>>(emptySet())
    private val entries = MutableStateFlow<List<HistoryEntry>?>(null)

    val state: StateFlow<HistoryUiState> = combine(entries, missing, ui) { all, gone, current ->
        val list = all.orEmpty()
        val items = HistorySearch.apply(list, current.filter, current.query).map { HistoryItem(it, it.id in gone) }
        HistoryUiState(
            loading = all == null,
            query = current.query,
            filter = current.filter,
            items = items,
            isEmpty = list.isEmpty(),
            confirmDelete = current.confirmDeleteId?.let { id ->
                list.firstOrNull { it.id == id }?.let { HistoryItem(it, it.id in gone) }
            },
            consent = current.consent,
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    init {
        viewModelScope.launch {
            store.observeAll().collectLatest { list ->
                entries.value = list
                checkFiles(list)
            }
        }
    }

    /** Al volver a la pantalla: el usuario pudo borrar archivos desde otra app. */
    fun onResume() {
        viewModelScope.launch { entries.value?.let { checkFiles(it) } }
    }

    fun onQueryChange(query: String) = ui.update { it.copy(query = query) }

    fun onFilterChange(filter: HistoryFilter) = ui.update { it.copy(filter = filter) }

    /** Carga el enlace en Inicio, como si se hubiera compartido (ADR-018). La pantalla pasa a Inicio. */
    fun onRedownload(id: String): Boolean {
        val url = find(id)?.sourceUrl ?: return false
        shareInbox.offer(SharedContent(url, null))
        return true
    }

    /** Quita la entrada; el archivo queda. */
    fun onRemove(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    fun onDeleteFile(id: String) {
        if (find(id) != null) ui.update { it.copy(confirmDeleteId = id) }
    }

    fun onDismissDelete() = ui.update { it.copy(confirmDeleteId = null) }

    /** Borra el archivo y su entrada. Si Android pide confirmación, la pantalla la muestra. */
    fun onConfirmDelete() {
        val id = ui.value.confirmDeleteId ?: return
        ui.update { it.copy(confirmDeleteId = null) }
        val entry = find(id) ?: return
        viewModelScope.launch {
            when (val result = files.delete(entry.file.location)) {
                DeleteResult.Deleted, DeleteResult.NotFound -> deleted(id)
                is DeleteResult.NeedsConsent -> ui.update { it.copy(consent = PendingConsent(id, result.consent)) }
                DeleteResult.Failed -> ui.update { it.copy(message = HistoryMessage.DELETE_FAILED) }
            }
        }
    }

    fun onConsentShown() = ui.update { current -> current.copy(consent = current.consent?.copy(shown = true)) }

    /**
     * Respuesta del sistema. En Android 11+ el sistema ya borró el archivo; en Android 10 hay que
     * volver a intentarlo. Una segunda negativa cuenta como fallo, para no preguntar en bucle.
     */
    fun onConsentResult(granted: Boolean) {
        val pending = ui.value.consent ?: return
        ui.update { it.copy(consent = null) }
        if (!granted) return
        val entry = find(pending.id) ?: return
        viewModelScope.launch {
            when (files.delete(entry.file.location)) {
                DeleteResult.Deleted, DeleteResult.NotFound -> deleted(pending.id)
                else -> ui.update { it.copy(message = HistoryMessage.DELETE_FAILED) }
            }
        }
    }

    fun onMessageShown() = ui.update { it.copy(message = null) }

    private suspend fun deleted(id: String) {
        store.delete(id)
        ui.update { it.copy(message = HistoryMessage.FILE_DELETED) }
    }

    private fun find(id: String): HistoryEntry? = entries.value?.firstOrNull { it.id == id }

    private suspend fun checkFiles(list: List<HistoryEntry>) {
        missing.value = list.filterNot { files.exists(it.file.location) }.map { it.id }.toSet()
    }
}
