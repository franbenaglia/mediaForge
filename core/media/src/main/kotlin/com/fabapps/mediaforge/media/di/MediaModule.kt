package com.fabapps.mediaforge.media.di

import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.media.engine.IoDispatcher
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.ytdlp.YtDlpExtractor
import com.fabapps.mediaforge.media.ytdlp.YtDlpRuntimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaModule {
    @Binds
    abstract fun nativeEngine(impl: YoutubeDlNativeEngine): NativeEngine

    @Binds
    abstract fun ytDlpRuntime(impl: YoutubeDlNativeEngine): YtDlpRuntimeProvider

    @Binds
    abstract fun mediaExtractor(impl: YtDlpExtractor): MediaExtractor

    companion object {
        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun processRunner(@IoDispatcher io: CoroutineDispatcher): ProcessRunner = ProcessRunner(io)
    }
}
