package com.fabapps.mediaforge.files

import com.fabapps.mediaforge.domain.settings.SettingsStore
import com.fabapps.mediaforge.domain.storage.FolderLibrary
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.jobs.ApplicationScope
import com.fabapps.mediaforge.jobs.JobNotifications
import com.fabapps.mediaforge.media.storage.GalleryLibrary
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {
    @Binds
    abstract fun savedFiles(files: ContentSavedFiles): SavedFiles

    @Binds
    abstract fun tempStorage(storage: JobFoldersTempStorage): TempStorage

    @Binds
    abstract fun destinationFolders(folders: SafDestinationFolders): DestinationFolders

    companion object {
        @Provides
        @Singleton
        fun mediaLibrary(
            @GalleryLibrary gallery: MediaLibrary,
            folders: FolderLibrary,
            access: DestinationFolders,
            settings: SettingsStore,
            notifications: JobNotifications,
            @ApplicationScope scope: CoroutineScope,
        ): MediaLibrary = RoutingLibrary(gallery, folders, access, settings, notifications::showFolderLost, scope)
    }
}
