package com.fabapps.mediaforge.media.di

import android.content.Context
import androidx.core.content.edit
import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.IoDispatcher
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import com.fabapps.mediaforge.media.ffmpeg.ContentInputSource
import com.fabapps.mediaforge.media.ffmpeg.FfmpegConverter
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.storage.MediaStoreLibrary
import com.fabapps.mediaforge.media.update.AndroidVersionProbe
import com.fabapps.mediaforge.media.update.KeyValueStore
import com.fabapps.mediaforge.media.update.UrlConnectionFetcher
import com.fabapps.mediaforge.media.update.YtDlpUpdater
import com.fabapps.mediaforge.media.update.YtDlpVersions
import com.fabapps.mediaforge.media.ytdlp.YtDlpDownloader
import com.fabapps.mediaforge.media.ytdlp.YtDlpExtractor
import com.fabapps.mediaforge.media.ytdlp.YtDlpRuntimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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

        @Provides
        @Singleton
        fun ytDlpVersions(@ApplicationContext context: Context): YtDlpVersions {
            val prefs = context.getSharedPreferences("ytdlp_versions", Context.MODE_PRIVATE)
            val store = object : KeyValueStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun set(key: String, value: String?) = prefs.edit { putString(key, value) }
            }
            return YtDlpVersions(File(context.noBackupFilesDir, "mediaforge/yt-dlp"), store)
        }

        @Provides
        @Singleton
        fun mediaLibrary(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): MediaLibrary =
            MediaStoreLibrary(context, io)

        /** Carpeta privada para los trabajos en curso (no entra en backups ni la limpia el sistema). */
        @Provides
        @Singleton
        fun mediaDownloader(
            @ApplicationContext context: Context,
            engine: EngineInitializer,
            native: YoutubeDlNativeEngine,
            runner: ProcessRunner,
            @IoDispatcher io: CoroutineDispatcher,
        ): MediaDownloader = YtDlpDownloader(
            engine = engine,
            runtimeProvider = native,
            runner = runner,
            workRoot = File(context.noBackupFilesDir, "mediaforge/jobs"),
            io = io,
        )

        /** Comparte la carpeta de trabajos con las descargas; cada trabajo tiene su propio ID. */
        @Provides
        @Singleton
        fun mediaConverter(
            @ApplicationContext context: Context,
            engine: EngineInitializer,
            native: YoutubeDlNativeEngine,
            runner: ProcessRunner,
            @IoDispatcher io: CoroutineDispatcher,
        ): MediaConverter = FfmpegConverter(
            engine = engine,
            runtimeProvider = native,
            runner = runner,
            inputs = ContentInputSource(context),
            workRoot = File(context.noBackupFilesDir, "mediaforge/jobs"),
            io = io,
        )

        @Provides
        @Singleton
        fun extractorEngine(
            versions: YtDlpVersions,
            engine: EngineInitializer,
            native: YoutubeDlNativeEngine,
            runner: ProcessRunner,
            @IoDispatcher io: CoroutineDispatcher,
        ): ExtractorEngine = YtDlpUpdater(
            http = UrlConnectionFetcher(),
            versions = versions,
            probe = AndroidVersionProbe(engine, native, runner, native.bundledYtDlp()),
            io = io,
        )
    }
}
