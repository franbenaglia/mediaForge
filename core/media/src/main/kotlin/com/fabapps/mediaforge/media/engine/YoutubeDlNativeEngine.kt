package com.fabapps.mediaforge.media.engine

import android.content.Context
import android.os.StatFs
import androidx.core.content.edit
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
    NativeEngine {

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

    private companion object {
        const val PREFS = "media_engine"
        const val KEY_EXTRACTED_BYTES = "extracted_package_bytes"
        val PACKAGE_ZIPS = listOf("libpython.zip.so", "libffmpeg.zip.so")
        val EXTRACTED_DIRS = listOf("python/usr", "ffmpeg/usr")
    }
}
