package com.fabapps.mediaforge.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.settings.PreferredMode
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    /** Cada instancia es como un arranque nuevo de la app sobre el mismo archivo. */
    private fun dataStore(file: File) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it },
        produceFile = { file },
    )

    private val file get() = File(folder.root, "settings.preferences_pb")

    @Test
    fun startsWithDefaults() = runBlocking {
        assertEquals(Settings(), DataStoreSettingsStore(dataStore(file)).settings.first())
    }

    @Test
    fun changesAreKeptAcrossRestarts() = runBlocking {
        val changed = Settings(
            mode = PreferredMode.AUDIO,
            maxHeight = 720,
            audioFormat = AudioFormat.OPUS,
            audioBitrateKbps = 160,
            maxDownloads = 3,
            wifiOnly = true,
            theme = ThemeMode.DARK,
            dynamicColor = false,
            destinationTree = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
        )
        val first = dataStore(file)
        DataStoreSettingsStore(first).update { changed }
        scopes.removeAt(scopes.lastIndex).cancel()
        assertEquals(changed, DataStoreSettingsStore(dataStore(file)).settings.first())
    }

    @Test
    fun clearingAnOptionalValueRemovesIt() = runBlocking {
        val store = DataStoreSettingsStore(dataStore(file))
        store.update { it.copy(maxHeight = 1080, audioBitrateKbps = 256) }
        store.update { it.copy(maxHeight = null, audioBitrateKbps = null) }
        val settings = store.settings.first()
        assertEquals(null, settings.maxHeight)
        assertEquals(null, settings.audioBitrateKbps)
    }

    @Test
    fun invalidValuesAreNormalizedOnWriteAndRead() = runBlocking {
        val prefs = dataStore(file)
        val store = DataStoreSettingsStore(prefs)
        store.update { it.copy(maxDownloads = 9) }
        assertEquals(3, store.settings.first().maxDownloads)

        // Lo que haya escrito otra versión de la app.
        prefs.edit {
            it[stringPreferencesKey("theme")] = "NEON"
            it[stringPreferencesKey("audio_format")] = "AIFF"
            it[intPreferencesKey("max_height")] = 999
            it[intPreferencesKey("max_downloads")] = -4
        }
        val read = store.settings.first()
        assertEquals(ThemeMode.SYSTEM, read.theme)
        assertEquals(AudioFormat.MP3, read.audioFormat)
        assertEquals(null, read.maxHeight)
        assertEquals(1, read.maxDownloads)
    }
}
