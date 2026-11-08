package com.fabapps.mediaforge.media.ffmpeg

import java.io.File

/** Archivo elegido por el usuario (una URI `content://` en Android). */
interface InputSource {
    data class Info(val displayName: String, val sizeBytes: Long?)

    /** Nombre y tamaño sin leer el contenido; `null` si no se puede abrir. */
    suspend fun describe(input: String): Info?

    /** Copia el contenido a [target]. Tiene que poder cancelarse a mitad. */
    suspend fun copyTo(input: String, target: File)
}

fun interface FfmpegRuntimeProvider {
    fun ffmpegRuntime(): FfmpegRuntime
}
