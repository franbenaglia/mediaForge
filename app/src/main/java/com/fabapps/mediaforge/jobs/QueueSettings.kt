package com.fabapps.mediaforge.jobs

import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lleva a la cola los ajustes que la afectan (ADR-019). Hasta que DataStore responde (unos
 * milisegundos al arrancar) rige el límite predeterminado.
 */
@Singleton
class QueueSettings @Inject constructor(
    private val scheduler: JobScheduler,
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun install() {
        scope.launch {
            settings.settings.map { it.maxDownloads }.distinctUntilChanged().collect { scheduler.setDownloadLimit(it) }
        }
    }
}
