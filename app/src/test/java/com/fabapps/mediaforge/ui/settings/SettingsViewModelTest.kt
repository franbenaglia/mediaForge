package com.fabapps.mediaforge.ui.settings

import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.settings.PreferredMode
import com.fabapps.mediaforge.domain.settings.ThemeMode
import com.fabapps.mediaforge.testing.FakeSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeEngine : ExtractorEngine {
        var version = "2025.11.12"
        var check = CompletableDeferred<Outcome<String?>>()
        var update = CompletableDeferred<Outcome<String>>()
        var updates = 0
        override suspend fun installedVersion() = version
        override suspend fun checkForUpdate() = check.await()
        override suspend fun update(): Outcome<String> {
            updates++
            return update.await()
        }
    }

    private val store = FakeSettingsStore()
    private val engine = FakeEngine()
    private lateinit var vm: SettingsViewModel
    private val state get() = vm.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = SettingsViewModel(store, engine)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        block()
    }

    @Test
    fun showsStoredSettingsAndEngineVersion() = test {
        assertFalse(state.loading)
        assertEquals("2025.11.12", state.engineVersion)
        assertEquals(store.settings.value, state.settings)
    }

    @Test
    fun everyChangeIsSaved() = test {
        vm.onModeChange(PreferredMode.AUDIO)
        vm.onMaxHeightChange(720)
        vm.onAudioFormatChange(AudioFormat.OPUS)
        vm.onAudioBitrateChange(160)
        vm.onMaxDownloadsChange(3)
        vm.onThemeChange(ThemeMode.DARK)
        vm.onDynamicColorChange(false)
        advanceUntilIdle()
        val saved = store.settings.value
        assertEquals(PreferredMode.AUDIO, saved.mode)
        assertEquals(720, saved.maxHeight)
        assertEquals(AudioFormat.OPUS, saved.audioFormat)
        assertEquals(160, saved.audioBitrateKbps)
        assertEquals(3, saved.maxDownloads)
        assertEquals(ThemeMode.DARK, saved.theme)
        assertFalse(saved.dynamicColor)
        assertEquals(saved, state.settings)
        assertEquals(listOf(96, 128, 160), state.bitrates)
    }

    @Test
    fun changingFormatDropsABitrateItDoesNotOffer() = test {
        vm.onAudioBitrateChange(320)
        advanceUntilIdle()
        assertEquals(320, state.settings.audioBitrateKbps)
        vm.onAudioFormatChange(AudioFormat.FLAC)
        advanceUntilIdle()
        assertNull(state.settings.audioBitrateKbps)
        assertTrue(state.bitrates.isEmpty())
    }

    @Test
    fun checkFindsNothingNew() = test {
        vm.onCheckEngine()
        advanceUntilIdle()
        assertEquals(EngineStatus.Checking, state.engine)
        vm.onCheckEngine()
        engine.check.complete(Outcome.Success(null))
        advanceUntilIdle()
        assertEquals(EngineStatus.UpToDate, state.engine)
        assertFalse(state.canInstall)
    }

    @Test
    fun checkThenInstallUpdatesTheShownVersion() = test {
        vm.onCheckEngine()
        engine.check.complete(Outcome.Success("2026.10.26"))
        advanceUntilIdle()
        assertEquals(EngineStatus.Available("2026.10.26"), state.engine)

        vm.onInstallEngine()
        advanceUntilIdle()
        assertEquals(EngineStatus.Updating, state.engine)
        assertTrue(state.busyEngine)
        engine.update.complete(Outcome.Success("2026.10.26"))
        advanceUntilIdle()
        assertEquals(EngineStatus.Updated("2026.10.26"), state.engine)
        assertEquals("2026.10.26", state.engineVersion)
        assertEquals(1, engine.updates)
    }

    @Test
    fun installIsOnlyOfferedAfterFindingAVersion() = test {
        vm.onInstallEngine()
        advanceUntilIdle()
        assertEquals(0, engine.updates)
    }

    @Test
    fun errorsAreShown() = test {
        vm.onCheckEngine()
        engine.check.complete(Outcome.Failure(MediaError.Network("offline")))
        advanceUntilIdle()
        assertEquals(EngineStatus.Failed(MediaError.Network("offline")), state.engine)
    }
}
