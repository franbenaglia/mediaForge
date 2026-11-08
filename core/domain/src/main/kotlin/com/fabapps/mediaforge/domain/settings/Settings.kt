package com.fabapps.mediaforge.domain.settings

import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.model.AudioFormat
import kotlinx.coroutines.flow.Flow

enum class PreferredMode { VIDEO, AUDIO }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias del usuario (main.md §9, ADR-019). Los valores por defecto son los de una instalación nueva. */
data class Settings(
    val mode: PreferredMode = PreferredMode.VIDEO,
    /** Calidad preferida: la mayor que no la supere. Nulo = la mejor disponible. */
    val maxHeight: Int? = null,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    /** Nulo = automático. Solo valores que el formato ofrece. */
    val audioBitrateKbps: Int? = null,
    val maxDownloads: Int = DEFAULT_DOWNLOADS,
    /** Con red medida (datos móviles), las descargas nuevas esperan. */
    val wifiOnly: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Colores del fondo de pantalla (Android 12+). */
    val dynamicColor: Boolean = true,
    /** URI de una carpeta elegida con el selector del sistema. Nulo = la galería. */
    val destinationTree: String? = null,
) {
    /** Corrige lo que no es válido (por ejemplo, de una versión anterior de la app). */
    fun normalized(): Settings = copy(
        maxHeight = maxHeight?.takeIf { it in HEIGHTS },
        audioBitrateKbps = audioBitrateKbps?.takeIf { it in AudioOptions.bitratesFor(audioFormat) },
        maxDownloads = maxDownloads.coerceIn(DOWNLOAD_RANGE),
        destinationTree = destinationTree?.takeIf { it.startsWith("content://") },
    )

    companion object {
        /** Calidades que se pueden elegir como preferidas. */
        val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
        val DOWNLOAD_RANGE = 1..3
        const val DEFAULT_DOWNLOADS = 2
    }
}

/** Dónde se guardan los ajustes. La implementación (DataStore) vive en `:core:data`. */
interface SettingsStore {
    /** Siempre normalizados. */
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
