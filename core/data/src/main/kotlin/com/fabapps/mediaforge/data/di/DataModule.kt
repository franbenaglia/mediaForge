package com.fabapps.mediaforge.data.di

import android.content.Context
import androidx.room.Room
import com.fabapps.mediaforge.data.jobs.MediaForgeDatabase
import com.fabapps.mediaforge.data.jobs.RoomJobStore
import com.fabapps.mediaforge.domain.jobs.JobStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}
