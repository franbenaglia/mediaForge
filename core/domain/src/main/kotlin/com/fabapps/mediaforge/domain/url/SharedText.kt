package com.fabapps.mediaforge.domain.url

/** Texto que llega de otra app con "Compartir" (main.md §8.1, ADR-018). No es confiable. */
object SharedText {
    /** Un enlace compartido nunca es tan largo: se corta antes de buscar. */
    const val MAX_LENGTH = 16_384

    /**
     * Primera URL válida del [text] ("Mirá esto https://…", o "youtube.com/…" sin esquema) y, si
     * no hay, del [subject]. Si no encuentra ninguna, devuelve el motivo del texto principal.
     */
    fun findUrl(text: String?, subject: String? = null): UrlCheck {
        val body = text.orEmpty().take(MAX_LENGTH).trim()
        val title = subject.orEmpty().take(MAX_LENGTH).trim()
        if (body.isEmpty() && title.isEmpty()) return UrlCheck.Invalid(UrlCheck.Reason.EMPTY)

        UrlValidator.findUrl(body)?.let { return it }
        val whole = UrlValidator.validate(body)
        if (whole is UrlCheck.Valid) return whole
        UrlValidator.findUrl(title)?.let { return it }
        return if (body.isNotEmpty()) whole else UrlValidator.validate(title)
    }
}
