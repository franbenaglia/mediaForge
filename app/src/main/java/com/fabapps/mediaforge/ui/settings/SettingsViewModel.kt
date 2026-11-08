package com.fabapps.mediaforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.settings.PreferredMode
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.SettingsStore
import com.fabapps.mediaforge.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del motor de extracción en Ajustes (main.md §10). */
sealed interface EngineStatus {
    data object Idle : EngineStatus

    data object Checking : EngineStatus

    data object UpToDate : EngineStatus

    data class Available(val version: String) : EngineStatus

    /** Bajando, verificando firma y hash, y probando la versión nueva. */
    data object Updating : EngineStatus

    data class Updated(val version: String) : EngineStatus

    data class Failed(val error: MediaError) : EngineStatus
}

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    /** Versión de yt-dlp en uso; nula mientras se lee o si no se pudo leer. */
    val engineVersion: String? = null,
    val engine: EngineStatus = EngineStatus.Idle,
) {
    val bitrates: List<Int> get() = AudioOptions.bitratesFor(settings.audioFormat)
    val busyEngine: Boolean get() = engine == EngineStatus.Checking || engine == EngineStatus.Updating
    val canInstall: Boolean get() = engine is EngineStatus.Available
}

/** Ajustes (main.md §9, §10, ADR-019). Cada cambio se guarda al instante. */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore, private val engine: ExtractorEngine) :
    ViewModel() {
    private val version = MutableStateFlow<String?>(null)
    private val engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Idle)

    val state: StateFlow<SettingsUiState> = combine(store.settings, version, engineStatus) { settings, v, status ->
        SettingsUiState(loading = false, settings = settings, engineVersion = v, engine = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    init {
        viewModelScope.launch { version.value = engine.installedVersion() }
    }

    fun onModeChange(mode: PreferredMode) = edit { it.copy(mode = mode) }

    fun onMaxHeightChange(height: Int?) = edit { it.copy(maxHeight = height) }

    /** Cambiar de formato vuelve el bitrate a automático si el nuevo no lo ofrece. */
    fun onAudioFormatChange(format: AudioFormat) = edit { it.copy(audioFormat = format) }

    fun onAudioBitrateChange(kbps: Int?) = edit { it.copy(audioBitrateKbps = kbps) }

    fun onMaxDownloadsChange(count: Int) = edit { it.copy(maxDownloads = count) }

    fun onWifiOnlyChange(enabled: Boolean) = edit { it.copy(wifiOnly = enabled) }

    fun onThemeChange(theme: ThemeMode) = edit { it.copy(theme = theme) }

    fun onDynamicColorChange(enabled: Boolean) = edit { it.copy(dynamicColor = enabled) }

    fun onCheckEngine() {
        if (state.value.busyEngine) return
        engineStatus.value = EngineStatus.Checking
        viewModelScope.launch {
            engineStatus.value = when (val result = engine.checkForUpdate()) {
                is Outcome.Success -> result.value?.let { EngineStatus.Available(it) } ?: EngineStatus.UpToDate
                is Outcome.Failure -> EngineStatus.Failed(result.error)
            }
        }
    }

    /** Instala la versión encontrada; el motor verifica firma y hash antes de activarla. */
    fun onInstallEngine() {
        if (engineStatus.value !is EngineStatus.Available) return
        engineStatus.value = EngineStatus.Updating
        viewModelScope.launch {
            engineStatus.value = when (val result = engine.update()) {
                is Outcome.Success -> {
                    version.value = result.value
                    EngineStatus.Updated(result.value)
                }
                is Outcome.Failure -> EngineStatus.Failed(result.error)
            }
        }
    }

    private fun edit(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
