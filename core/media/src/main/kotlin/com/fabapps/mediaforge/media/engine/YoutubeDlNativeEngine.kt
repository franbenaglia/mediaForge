package com.fabapps.mediaforge.media.engine

import android.content.Context
import android.os.StatFs
import androidx.core.content.edit
import com.fabapps.mediaforge.media.ffmpeg.FfmpegRuntime
import com.fabapps.mediaforge.media.ffmpeg.FfmpegRuntimeProvider
import com.fabapps.mediaforge.media.update.YtDlpVersions
import com.fabapps.mediaforge.media.ytdlp.YtDlpRuntime
import com.fabapps.mediaforge.media.ytdlp.YtDlpRuntimeProvider
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Implementación con youtubedl-android. Las rutas internas de la librería están solo acá:
 * si se cambia de librería (ADR-001), se reemplaza esta clase.
 */
internal class YoutubeDlNativeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val versions: YtDlpVersions,
) : NativeEngine,
    YtDlpRuntimeProvider,
    FfmpegRuntimeProvider {

    private val nativeDir get() = File(context.applicationInfo.nativeLibraryDir)
    private val packagesDir get() = File(context.noBackupFilesDir, "youtubedl-android/packages")
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun compressedPackageBytes(): Long = PACKAGE_ZIPS.sumOf { File(nativeDir, it).length() }

    /** Descomprimido y de esta misma versión: si el APK trae otros paquetes, hay que volver a descomprimir. */
    override fun isExtracted(): Boolean = EXTRACTED_DIRS.all { File(packagesDir, it).isDirectory } &&
        prefs.getLong(KEY_EXTRACTED_BYTES, -1) == compressedPackageBytes()

    override fun freeBytes(): Long = StatFs(context.noBackupFilesDir.absolutePath).availableBytes

    override fun initialize() {
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        prefs.edit { putLong(KEY_EXTRACTED_BYTES, compressedPackageBytes()) }
    }

    /**
     * Mismo entorno que arma `YoutubeDL.executeImpl` de la librería (0.18.1), para ejecutar con
     * nuestro [com.fabapps.mediaforge.media.process.ProcessRunner].
     */
    override fun runtime(): YtDlpRuntime {
        val python = File(packagesDir, "python/usr")
        return YtDlpRuntime(
            python = File(nativeDir, "libpython.so"),
            // La versión actualizada y verificada (T3.7), o la que vino con el APK.
            ytDlp = versions.active()?.file ?: bundledYtDlp(),
            ffmpeg = File(nativeDir, "libffmpeg.so"),
            quickJs = File(nativeDir, "libqjs.so"),
            cacheDir = File(context.cacheDir, "yt-dlp").apply { mkdirs() },
            environment = mapOf(
                "LD_LIBRARY_PATH" to libraryPath(),
                "SSL_CERT_FILE" to File(python, "etc/tls/cert.pem").absolutePath,
                "PYTHONHOME" to python.absolutePath,
                "HOME" to python.absolutePath,
                "TMPDIR" to context.cacheDir.absolutePath,
                "PATH" to "${System.getenv("PATH")}:${nativeDir.absolutePath}",
            ),
        )
    }

    /**
     * Carpetas de librerías de los paquetes. FFmpeg también necesita la de Python: `librubberband.so`
     * depende de `libc++_shared.so`, que viene en el paquete de Python (visto en el teléfono, 2026-11-08).
     */
    private fun libraryPath(): String = listOf("python", "ffmpeg", "aria2c").joinToString(":") {
        File(packagesDir, "$it/usr/lib").absolutePath
    }

    /** FFmpeg y ffprobe del APK; el conversor no ejecuta yt-dlp ni Python (ADR-002). */
    override fun ffmpegRuntime(): FfmpegRuntime = FfmpegRuntime(
        ffmpeg = File(nativeDir, "libffmpeg.so"),
        ffprobe = File(nativeDir, "libffprobe.so"),
        environment = mapOf(
            "LD_LIBRARY_PATH" to libraryPath(),
            "TMPDIR" to context.cacheDir.absolutePath,
        ),
    )

    /** Copia de yt-dlp que la librería deja al inicializar. Nunca la modifica la app. */
    fun bundledYtDlp() = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")

    private companion object {
        const val PREFS = "media_engine"
        const val KEY_EXTRACTED_BYTES = "extracted_package_bytes"
        val PACKAGE_ZIPS = listOf("libpython.zip.so", "libffmpeg.zip.so")
        val EXTRACTED_DIRS = listOf("python/usr", "ffmpeg/usr")
    }
}
