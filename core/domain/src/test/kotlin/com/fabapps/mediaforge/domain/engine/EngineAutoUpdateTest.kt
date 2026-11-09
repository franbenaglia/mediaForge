package com.fabapps.mediaforge.domain.engine

import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.SettingsStore
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineAutoUpdateTest {
    private class Store(initial: Settings = Settings()) : SettingsStore {
        override val settings = MutableStateFlow(initial)
        override suspend fun update(transform: (Settings) -> Settings) = settings.update(transform)
    }

    private class Engine(
        var check: Outcome<String?> = Outcome.Success(null),
        var install: Outcome<String> = Outcome.Success("2026.10.26"),
    ) : ExtractorEngine {
        var checks = 0
        var updates = 0
        override suspend fun installedVersion() = "2025.11.12"
        override suspend fun checkForUpdate(): Outcome<String?> = check.also { checks++ }
        override suspend fun update(): Outcome<String> = install.also { updates++ }
    }

    private val day = 1.days.inWholeMilliseconds
    private var now = 100 * day

    private fun run(engine: Engine, store: Store, network: Boolean = true) = runBlocking {
        EngineAutoUpdate(engine, store, clock = { now }).runIfDue { network }
    }

    @Test
    fun firstRunInstallsANewerVersionAndRecordsTheCheck() {
        val engine = Engine(check = Outcome.Success("2026.10.26"))
        val store = Store()
        assertEquals(AutoUpdateResult.UPDATED, run(engine, store))
        assertEquals(1, engine.updates)
        assertEquals(now, store.settings.value.engineCheckedAt)
    }

    @Test
    fun upToDateDoesNotDownload() {
        val engine = Engine()
        val store = Store()
        assertEquals(AutoUpdateResult.UP_TO_DATE, run(engine, store))
        assertEquals(0, engine.updates)
        assertEquals(now, store.settings.value.engineCheckedAt)
    }

    @Test
    fun checksAtMostOncePerWeek() {
        val engine = Engine()
        val store = Store(Settings(engineCheckedAt = now - 6 * day))
        assertEquals(AutoUpdateResult.NOT_DUE, run(engine, store))
        assertEquals(0, engine.checks)
        now += day
        assertEquals(AutoUpdateResult.UP_TO_DATE, run(engine, store))
    }

    @Test
    fun clockGoingBackwardsDoesNotBlockChecks() {
        val store = Store(Settings(engineCheckedAt = now + 30 * day))
        assertEquals(AutoUpdateResult.UP_TO_DATE, run(Engine(), store))
    }

    @Test
    fun disabledSettingDoesNothing() {
        val engine = Engine()
        assertEquals(AutoUpdateResult.DISABLED, run(engine, Store(Settings(autoUpdateEngine = false))))
        assertEquals(0, engine.checks)
    }

    @Test
    fun noAllowedNetworkWaitsWithoutRecording() {
        val engine = Engine()
        val store = Store()
        assertEquals(AutoUpdateResult.WAITING_NETWORK, run(engine, store, network = false))
        assertEquals(0, engine.checks)
        assertNull(store.settings.value.engineCheckedAt)
    }

    @Test
    fun failedCheckIsRetriedNextLaunch() {
        val store = Store()
        val result = run(Engine(check = Outcome.Failure(MediaError.Network())), store)
        assertEquals(AutoUpdateResult.FAILED, result)
        assertNull(store.settings.value.engineCheckedAt)
    }

    @Test
    fun failedInstallCountsAsACheckSoItIsNotDownloadedEveryLaunch() {
        val engine = Engine(check = Outcome.Success("2026.10.26"), install = Outcome.Failure(MediaError.Unknown()))
        val store = Store()
        assertEquals(AutoUpdateResult.FAILED, run(engine, store))
        assertEquals(now, store.settings.value.engineCheckedAt)
    }
}
