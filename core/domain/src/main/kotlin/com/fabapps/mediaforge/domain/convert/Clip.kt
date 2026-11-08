package com.fabapps.mediaforge.domain.convert

import java.util.Locale

/**
 * Fragmento de un archivo (ADR-020). Sin [exact], el video se copia si se puede y el corte cae en el
 * fotograma clave anterior a [startSeconds]; con [exact], el video se recodifica.
 */
data class Clip(val startSeconds: Double, val endSeconds: Double? = null, val exact: Boolean = false) {
    /** Duración del fragmento; nula si no hay fin y no se conoce la duración del archivo. */
    fun lengthSeconds(durationSeconds: Double?): Double? = (endSeconds ?: durationSeconds)?.minus(startSeconds)

    /** Nulo si el fragmento es válido para un archivo de esa duración (nula = desconocida). */
    fun problem(durationSeconds: Double?): ClipProblem? {
        if (!startSeconds.isFinite() || startSeconds < 0) return ClipProblem.INVALID
        if (endSeconds != null && (!endSeconds.isFinite() || endSeconds - startSeconds < MIN_SECONDS)) {
            return ClipProblem.TOO_SHORT
        }
        if (durationSeconds != null) {
            if (durationSeconds - startSeconds < MIN_SECONDS) return ClipProblem.BEYOND_END
            // Un poco de margen: la duración del análisis rápido y la de ffprobe pueden diferir.
            if (endSeconds != null && endSeconds > durationSeconds + END_TOLERANCE) return ClipProblem.BEYOND_END
        }
        return null
    }

    companion object {
        const val MIN_SECONDS = 0.5
        private const val END_TOLERANCE = 0.5
    }
}

enum class ClipProblem {
    /** Un tiempo negativo o ilegible. */
    INVALID,

    /** El fin no está al menos medio segundo después del inicio. */
    TOO_SHORT,

    /** El inicio o el fin pasan la duración del archivo. */
    BEYOND_END,
}

/** Tiempos escritos por el usuario: `83`, `1:23`, `1:02:03`, con décimas opcionales (`1:23.5` o `1:23,5`). */
object TimeCodes {
    private val PATTERN = Regex("""(?:(\d{1,3}):)?(?:(\d{1,4}):)?(\d{1,6})(?:[.,](\d{1,3}))?""")

    /** Segundos, o nulo si no es un tiempo válido. Con una unidad mayor, minutos y segundos van de 0 a 59. */
    fun parse(text: String): Double? {
        val match = PATTERN.matchEntire(text.trim()) ?: return null
        val (a, b, c, fraction) = match.destructured
        // Con dos grupos, el primero son minutos (`1:23`); con tres, horas.
        val hours = if (a.isNotEmpty() && b.isNotEmpty()) a.toLong() else 0L
        val minutes = when {
            b.isNotEmpty() -> b.toLong()
            a.isNotEmpty() -> a.toLong()
            else -> 0L
        }
        val seconds = c.toLong()
        val hasMinutes = a.isNotEmpty() || b.isNotEmpty()
        val hasHours = a.isNotEmpty() && b.isNotEmpty()
        if (hasMinutes && seconds >= 60) return null
        if (hasHours && minutes >= 60) return null
        val decimals = if (fraction.isEmpty()) 0.0 else "0.$fraction".toDouble()
        return hours * 3600.0 + minutes * 60.0 + seconds + decimals
    }

    /** `1:23`, `1:02:03` o `1:23.5`; décimas solo si hacen falta. */
    fun format(seconds: Double): String {
        val tenths = Math.round(seconds.coerceAtLeast(0.0) * 10)
        val whole = tenths / 10
        val h = whole / 3600
        val m = whole % 3600 / 60
        val s = whole % 60
        val base = if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
        val rest = tenths % 10
        return if (rest == 0L) base else "$base.$rest"
    }
}
