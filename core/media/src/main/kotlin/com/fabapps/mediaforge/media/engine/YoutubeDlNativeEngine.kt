package com.fabapps.mediaforge.media.engine

import android.content.Context
import android.os.StatFs
import androidx.core.content.edit
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
internal class YoutubeDlNativeEngine @Inject constructor(@ApplicationContext private val context: Context) :
    NativeEngine,
    YtDlpRuntimeProvider {

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
        val libs = listOf("python", "ffmpeg", "aria2c").joinToString(":") {
            File(packagesDir, "$it/usr/lib").absolutePath
        }
        return YtDlpRuntime(
            python = File(nativeDir, "libpython.so"),
            ytDlp = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp"),
            ffmpeg = File(nativeDir, "libffmpeg.so"),
            quickJs = File(nativeDir, "libqjs.so"),
            cacheDir = File(context.cacheDir, "yt-dlp").apply { mkdirs() },
            environment = mapOf(
                "LD_LIBRARY_PATH" to libs,
                "SSL_CERT_FILE" to File(python, "etc/tls/cert.pem").absolutePath,
                "PYTHONHOME" to python.absolutePath,
                "HOME" to python.absolutePath,
                "TMPDIR" to context.cacheDir.absolutePath,
                "PATH" to "${System.getenv("PATH")}:${nativeDir.absolutePath}",
            ),
        )
    }

    private companion object {
        const val PREFS = "media_engine"
        const val KEY_EXTRACTED_BYTES = "extracted_package_bytes"
        val PACKAGE_ZIPS = listOf("libpython.zip.so", "libffmpeg.zip.so")
        val EXTRACTED_DIRS = listOf("python/usr", "ffmpeg/usr")
    }
}
