package com.fabapps.mediaforge.data.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.fabapps.mediaforge.data.history.RoomHistoryStore
import com.fabapps.mediaforge.data.jobs.MediaForgeDatabase
import com.fabapps.mediaforge.data.jobs.RoomJobStore
import com.fabapps.mediaforge.data.settings.DataStoreSettingsStore
import com.fabapps.mediaforge.domain.history.HistoryStore
import com.fabapps.mediaforge.domain.jobs.JobStore
import com.fabapps.mediaforge.domain.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MediaForgeDatabase =
        Room.databaseBuilder(context, MediaForgeDatabase::class.java, MediaForgeDatabase.NAME).build()

    @Provides
    @Singleton
    fun jobStore(database: MediaForgeDatabase): JobStore = RoomJobStore(database.jobs())

    @Provides
    @Singleton
    fun historyStore(database: MediaForgeDatabase): HistoryStore = RoomHistoryStore(database.history())

    /** Un solo DataStore por archivo en todo el proceso: por eso es singleton. Un archivo dañado se reemplaza. */
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore = DataStoreSettingsStore(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { File(context.filesDir, "datastore/settings.preferences_pb") },
        ),
    )
}
