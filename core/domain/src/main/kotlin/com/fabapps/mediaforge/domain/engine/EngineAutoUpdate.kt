package com.fabapps.mediaforge.domain.engine

import com.fabapps.mediaforge.domain.settings.SettingsStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first

/** Qué pasó al intentar poner al día el motor. */
enum class AutoUpdateResult {
    /** El ajuste está apagado. */
    DISABLED,

    /** Se buscó hace menos de [EngineAutoUpdate.interval]. */
    NOT_DUE,

    /** "Solo Wi‑Fi" con red medida: se intenta en el próximo arranque. */
    WAITING_NETWORK,
    UP_TO_DATE,
    UPDATED,

    /** No se pudo buscar o instalar; sigue la versión actual. */
    FAILED,
}

/**
 * Mantiene yt-dlp al día sin que haga falta entrar a Ajustes (T11.9). Al abrir la app y como mucho
 * una vez cada [interval], usa la misma actualización verificada (firma y hash) que el botón de
 * Ajustes. El yt-dlp del APK envejece rápido y YouTube lo rechaza (403) a los pocos meses.
 *
 * Un fallo de red no cuenta como búsqueda: se reintenta en el próximo arranque. Un fallo al instalar
 * sí cuenta, para no bajar lo mismo cada vez que se abre la app.
 */
class EngineAutoUpdate(
    private val engine: ExtractorEngine,
    private val settings: SettingsStore,
    private val clock: () -> Long,
    val interval: Duration = 7.days,
) {
    suspend fun runIfDue(networkAllowed: suspend () -> Boolean): AutoUpdateResult {
        val current = settings.settings.first()
        if (!current.autoUpdateEngine) return AutoUpdateResult.DISABLED
        val now = clock()
        val last = current.engineCheckedAt
        // Un reloj que retrocede no deja la búsqueda trabada.
        if (last != null && now >= last && now - last < interval.inWholeMilliseconds) return AutoUpdateResult.NOT_DUE
        if (!networkAllowed()) return AutoUpdateResult.WAITING_NETWORK

        val newer = when (val check = engine.checkForUpdate()) {
            is Outcome.Failure -> return AutoUpdateResult.FAILED
            is Outcome.Success -> check.value
        }
        val result = if (newer == null) {
            AutoUpdateResult.UP_TO_DATE
        } else {
            when (engine.update()) {
                is Outcome.Success -> AutoUpdateResult.UPDATED
                is Outcome.Failure -> AutoUpdateResult.FAILED
            }
        }
        settings.update { it.copy(engineCheckedAt = now) }
        return result
    }
}
