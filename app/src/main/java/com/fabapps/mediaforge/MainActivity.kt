package com.fabapps.mediaforge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.settings.SettingsStore
import com.fabapps.mediaforge.domain.settings.ThemeMode
import com.fabapps.mediaforge.share.ShareInbox
import com.fabapps.mediaforge.share.ShareIntents
import com.fabapps.mediaforge.ui.MediaForgeApp
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scheduler: JobScheduler

    @Inject lateinit var shareInbox: ShareInbox

    @Inject lateinit var settingsStore: SettingsStore

    /** Sube con cada enlace recibido: la app pasa a Inicio. Arranca en 0 en cada instancia (al girar no repite). */
    private val shareCount = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Al girar o al reabrir desde recientes llega el mismo intent: ya se procesó (ADR-018).
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) receive(intent)
        setContent {
            val shares by shareCount.collectAsStateWithLifecycle()
            // Hasta leer los ajustes (milisegundos) rige el tema del sistema.
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            MediaForgeTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: true) {
                MediaForgeApp(openHomeRequest = shares)
            }
        }
    }

    /** `singleTask`: compartir con la app abierta llega acá, no a una copia nueva. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    /** Con la app visible la cola puede arrancar el servicio: recupera lo interrumpido y sigue (ADR-017). */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { scheduler.start() }
    }

    private fun receive(intent: Intent?) {
        val shared = ShareIntents.read(intent) ?: return
        shareInbox.offer(shared)
        shareCount.update { it + 1 }
    }
}
