package com.fabapps.mediaforge.testing

import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Historial en memoria, ordenado como Room: del más nuevo al más viejo. */
class InMemoryHistoryStore : HistoryStore {
    val entries = MutableStateFlow<Map<String, HistoryEntry>>(emptyMap())

    override suspend fun put(entry: HistoryEntry) = entries.update { it + (entry.id to entry) }

    override suspend fun get(id: String): HistoryEntry? = entries.value[id]

    override fun observeAll(): Flow<List<HistoryEntry>> =
        entries.map { all -> all.values.sortedByDescending { it.completedAt } }

    override suspend fun delete(id: String) = entries.update { it - id }
}
