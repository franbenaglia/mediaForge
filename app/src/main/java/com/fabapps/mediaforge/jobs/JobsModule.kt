package com.fabapps.mediaforge.jobs

import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.jobs.JobStore
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Alcance que vive tanto como el proceso: ahí corren los trabajos (ADR-017). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object JobsModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun scheduler(
        store: JobStore,
        downloader: MediaDownloader,
        converter: MediaConverter,
        library: MediaLibrary,
        @ApplicationScope scope: CoroutineScope,
    ): JobScheduler = JobScheduler(store, downloader, converter, library, scope)
}
