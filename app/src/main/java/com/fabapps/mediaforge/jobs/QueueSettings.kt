package com.fabapps.mediaforge.jobs

import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lleva a la cola los ajustes que la afectan (ADR-019): el límite de descargas y "Solo Wi‑Fi". Hasta
 * que DataStore responde (unos milisegundos al arrancar) rigen los predeterminados.
 */
@Singleton
class QueueSettings @Inject constructor(
    private val scheduler: JobScheduler,
    private val settings: SettingsStore,
    private val network: UnmeteredNetwork,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun install() {
        scope.launch {
            settings.settings.map { it.maxDownloads }.distinctUntilChanged().collect { scheduler.setDownloadLimit(it) }
        }
        scope.launch {
            // La red se escucha solo mientras "Solo Wi‑Fi" está activo.
            settings.settings.map { it.wifiOnly }.distinctUntilChanged()
                .flatMapLatest { wifiOnly -> if (wifiOnly) network.available else flowOf(true) }
                .collect { scheduler.setDownloadsAllowed(it) }
        }
    }
}
