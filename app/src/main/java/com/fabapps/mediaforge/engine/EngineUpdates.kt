package com.fabapps.mediaforge.engine

import com.fabapps.mediaforge.domain.engine.EngineAutoUpdate
import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.settings.SettingsStore
import com.fabapps.mediaforge.jobs.ApplicationScope
import com.fabapps.mediaforge.jobs.UnmeteredNetwork
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Pone al día yt-dlp una vez por arranque de la app, si toca (T11.9). Respeta "Solo Wi‑Fi". */
@Singleton
class EngineUpdates @Inject constructor(
    engine: ExtractorEngine,
    private val settings: SettingsStore,
    private val network: UnmeteredNetwork,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val auto = EngineAutoUpdate(engine, settings, System::currentTimeMillis)

    fun install() {
        scope.launch {
            auto.runIfDue { !settings.settings.first().wifiOnly || network.available.first() }
        }
    }
}
