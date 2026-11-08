package com.fabapps.mediaforge.ui.format

import java.util.Locale

/** Formatos de números para mostrar. Funciones puras: se prueban en la JVM. */
object Formatters {
    private val units = listOf("B", "kB", "MB", "GB", "TB")

    /** Tamaño con unidades decimales (1 kB = 1000 B), como el sistema de Android. */
    fun bytes(value: Long, locale: Locale): String {
        require(value >= 0) { "negative size: $value" }
        if (value < 1000) return "$value B"
        var size = value.toDouble()
        var unit = 0
        while (size >= 999.95 && unit < units.lastIndex) {
            size /= 1000
            unit++
        }
        return String.format(locale, "%.1f %s", size, units[unit])
    }

    /** Duración como `m:ss` o `h:mm:ss`. */
    fun duration(totalSeconds: Long): String {
        require(totalSeconds >= 0) { "negative duration: $totalSeconds" }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** `1080p`, o `1080p60` cuando el video tiene más de 30 cuadros por segundo. */
    fun quality(height: Int, fps: Double?): String {
        val rounded = fps?.let { kotlin.math.round(it).toInt() }
        return if (rounded != null && rounded > 30) "${height}p$rounded" else "${height}p"
    }

    /** Nombre corto de un códec de FFmpeg para mostrar. Son nombres técnicos: no se traducen. */
    fun codec(name: String): String = when (name) {
        "h264" -> "H.264"
        "hevc" -> "H.265"
        "vp8" -> "VP8"
        "vp9" -> "VP9"
        "av1" -> "AV1"
        "mpeg4" -> "MPEG-4"
        "aac" -> "AAC"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "flac" -> "FLAC"
        "alac" -> "ALAC"
        "ac3" -> "AC-3"
        "eac3" -> "E-AC-3"
        "pcm_s16le" -> "PCM"
        else -> name.substringAfterLast('/').uppercase()
    }
}
