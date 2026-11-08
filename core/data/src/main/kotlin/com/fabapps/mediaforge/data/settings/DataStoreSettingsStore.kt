package com.fabapps.mediaforge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Ajustes en DataStore Preferences (ADR-019). Los enums van por nombre; un valor que falta, es
 * desconocido o está fuera de rango vuelve al predeterminado. Un nulo se guarda quitando la clave.
 */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<Settings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> write(prefs, transform(read(prefs)).normalized()) }
    }

    private fun read(prefs: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            mode = enumOf(prefs[MODE], defaults.mode),
            maxHeight = prefs[MAX_HEIGHT],
            audioFormat = enumOf(prefs[AUDIO_FORMAT], defaults.audioFormat),
            audioBitrateKbps = prefs[AUDIO_BITRATE],
            maxDownloads = prefs[MAX_DOWNLOADS] ?: defaults.maxDownloads,
            wifiOnly = prefs[WIFI_ONLY] ?: defaults.wifiOnly,
            theme = enumOf(prefs[THEME], defaults.theme),
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            destinationTree = prefs[DESTINATION_TREE],
        ).normalized()
    }

    private fun write(prefs: MutablePreferences, settings: Settings) {
        prefs[MODE] = settings.mode.name
        prefs.putOrRemove(MAX_HEIGHT, settings.maxHeight)
        prefs[AUDIO_FORMAT] = settings.audioFormat.name
        prefs.putOrRemove(AUDIO_BITRATE, settings.audioBitrateKbps)
        prefs[MAX_DOWNLOADS] = settings.maxDownloads
        prefs[WIFI_ONLY] = settings.wifiOnly
        prefs[THEME] = settings.theme.name
        prefs[DYNAMIC_COLOR] = settings.dynamicColor
        prefs.putOrRemove(DESTINATION_TREE, settings.destinationTree)
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val MAX_HEIGHT = intPreferencesKey("max_height")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val MAX_DOWNLOADS = intPreferencesKey("max_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DESTINATION_TREE = stringPreferencesKey("destination_tree")
    }
}
