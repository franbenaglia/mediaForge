package com.fabapps.mediaforge.media.di

import com.fabapps.mediaforge.media.engine.IoDispatcher
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaModule {
    @Binds
    abstract fun nativeEngine(impl: YoutubeDlNativeEngine): NativeEngine

    companion object {
        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
