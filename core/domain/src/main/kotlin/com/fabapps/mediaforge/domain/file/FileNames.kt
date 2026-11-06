package com.fabapps.mediaforge.domain.file

/**
 * Nombres de archivo seguros para MediaStore, SAF y tarjetas SD (FAT/exFAT).
 *
 * Los títulos vienen de sitios externos: pueden traer separadores de ruta, `..`,
 * caracteres de control o tener cientos de caracteres.
 */
object FileNames {
    /** Límite de la mayoría de los sistemas de archivos, en bytes UTF-8 del nombre completo. */
    const val MAX_NAME_BYTES = 255

    const val FALLBACK_NAME = "media"

    private val forbidden = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val whitespace = Regex("""\s+""")
    private val reservedWindowsNames = Regex("""(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$""")

    /**
     * Devuelve `base.extension` saneado y recortado a [MAX_NAME_BYTES].
     * [extension] se espera sin punto (`mp3`) y debe venir de la app, no del usuario.
     */
    fun sanitize(base: String, extension: String): String {
        val ext = extension.trim().trimStart('.').lowercase()
        require(ext.isNotEmpty() && ext.all { it.isLetterOrDigit() }) { "invalid extension: $extension" }

        var name = base
            .replace(forbidden, " ")
            .replace(whitespace, " ")
            .trimStart('.', ' ')
            .trimEnd('.', ' ')
        if (reservedWindowsNames.matches(name)) name = "${name}_"

        val maxBaseBytes = MAX_NAME_BYTES - ext.toByteArray().size - 1
        name = truncateUtf8(name, maxBaseBytes).trimEnd('.', ' ')
        if (name.isEmpty()) name = FALLBACK_NAME
        return "$name.$ext"
    }

    /**
     * Si [fileName] ya existe, agrega ` (1)`, ` (2)`, … antes de la extensión.
     * Evita sobrescribir archivos del usuario.
     */
    fun unique(fileName: String, exists: (String) -> Boolean): String {
        if (!exists(fileName)) return fileName
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        for (n in 1..9_999) {
            val suffix = " ($n)"
            val trimmedBase = truncateUtf8(base, MAX_NAME_BYTES - suffix.length - ext.toByteArray().size)
            val candidate = "$trimmedBase$suffix$ext"
            if (!exists(candidate)) return candidate
        }
        error("no free name for $fileName")
    }

    /** Recorta sin partir un carácter (ni un par sustituto) por la mitad. */
    private fun truncateUtf8(text: String, maxBytes: Int): String {
        if (text.toByteArray().size <= maxBytes) return text
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = text.substring(i, i + chars).toByteArray().size
            if (bytes + size > maxBytes) break
            out.appendCodePoint(cp)
            bytes += size
            i += chars
        }
        return out.toString()
    }
}
