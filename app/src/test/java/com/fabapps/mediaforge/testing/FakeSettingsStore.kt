package com.fabapps.mediaforge.testing

import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Ajustes en memoria, normalizados como en DataStore. */
class FakeSettingsStore(initial: Settings = Settings()) : SettingsStore {
    override val settings = MutableStateFlow(initial.normalized())

    override suspend fun update(transform: (Settings) -> Settings) = settings.update { transform(it).normalized() }
}
