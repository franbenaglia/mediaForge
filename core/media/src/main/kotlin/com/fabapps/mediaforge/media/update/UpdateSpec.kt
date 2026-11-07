package com.fabapps.mediaforge.media.update

/**
 * Reglas de `_update_spec` de yt-dlp: "esta variante en este entorno no puede pasar de la versión X".
 * Por ejemplo, `lockV2 yt-dlp/yt-dlp 2026.10.21 zip Python 3\.10` congela el zip con Python 3.10.
 *
 * Igual que yt-dlp, el patrón se compara desde el inicio contra `"<variante> Python <versión>"`.
 * El archivo no está firmado: solo puede bajar la versión elegida, nunca habilitar otra.
 */
object UpdateSpec {
    const val REPO = "yt-dlp/yt-dlp"

    /** Variante del ejecutable que usa la app: el zipapp de Python (asset `yt-dlp`). */
    const val VARIANT = "zip"

    /** Versión a instalar: [latest] o la de un bloqueo que aplique, la menor. */
    fun resolveTarget(spec: String, latest: String, pythonVersion: String): String {
        val identity = "$VARIANT Python $pythonVersion"
        val locks = spec.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("lockV2 ") }
            .mapNotNull { line ->
                val parts = line.split(' ', limit = 4)
                if (parts.size < 4 || parts[1] != REPO) return@mapNotNull null
                val pattern = runCatching { Regex(parts[3]) }.getOrNull() ?: return@mapNotNull null
                parts[2].takeIf { pattern.matchesAt(identity, 0) }
            }
        return (locks + latest).minWith(::compareVersions)
    }

    /** Compara versiones de yt-dlp (`2026.10.26`, `2026.10.21.202512`) numéricamente por partes. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
