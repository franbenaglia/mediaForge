package com.fabapps.mediaforge.media.ffmpeg

/**
 * Lee la salida de `-progress pipe:1` (bloques `clave=valor` que terminan en `progress=continue|end`) y
 * devuelve la fracción hecha. Sin duración conocida, no hay fracción.
 */
class FfmpegProgressParser(private val durationSeconds: Double?) {
    private var last: Float? = null

    /** Fracción nueva (0..1), o `null` si la línea no cambia nada. */
    fun feed(line: String): Float? {
        val trimmed = line.trim()
        val fraction = when {
            trimmed == "progress=end" -> 1f
            trimmed.startsWith("out_time_us=") -> {
                val micros = trimmed.substringAfter('=').toLongOrNull() ?: return null
                val total = durationSeconds?.takeIf { it > 0 } ?: return null
                (micros / 1_000_000.0 / total).toFloat().coerceIn(0f, 1f)
            }
            else -> return null
        }
        if (fraction == last) return null
        last = fraction
        return fraction
    }
}
