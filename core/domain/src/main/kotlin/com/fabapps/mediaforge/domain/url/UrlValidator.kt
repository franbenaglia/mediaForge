package com.fabapps.mediaforge.domain.url

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/** Resultado de validar lo que pegó o compartió el usuario. */
sealed interface UrlCheck {
    /** [url] está normalizada (esquema y host en minúsculas, host en ASCII) y es segura para pasar al motor. */
    data class Valid(val url: String, val host: String) : UrlCheck

    data class Invalid(val reason: Reason) : UrlCheck

    enum class Reason {
        EMPTY,
        TOO_LONG,
        NOT_A_URL,
        UNSUPPORTED_SCHEME,
        MISSING_HOST,

        /** `usuario:clave@host`: no se aceptan credenciales dentro de la URL. */
        CREDENTIALS,

        /** localhost o direcciones de red privada: no hay caso de uso y abre la puerta a abusos. */
        LOCAL_ADDRESS,
    }
}

/**
 * Valida URLs antes de que lleguen al motor de extracción.
 *
 * Solo `http` y `https`. Nunca devuelve como válido algo que empiece con `-`, para que una URL
 * no se pueda interpretar como opción de línea de comandos (además, el adaptador pone `--`).
 */
object UrlValidator {
    const val MAX_LENGTH = 2048

    private val allowedSchemes = setOf("http", "https")
    private val whitespaceOrControl = Regex("""[\s\p{Cntrl}]""")
    private val urlInText = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val bareDomain = Regex("""^[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)+([/:?#].*)?$""")

    fun validate(input: String): UrlCheck {
        val text = input.trim()
        if (text.isEmpty()) return invalid(UrlCheck.Reason.EMPTY)
        if (text.length > MAX_LENGTH) return invalid(UrlCheck.Reason.TOO_LONG)
        if (text.startsWith("-") || whitespaceOrControl.containsMatchIn(text)) {
            return invalid(UrlCheck.Reason.NOT_A_URL)
        }

        // "youtube.com/watch?v=…" sin esquema: se asume https.
        val withScheme = when {
            text.contains("://") -> text
            text.contains(':') && !bareDomain.matches(text) -> return invalid(UrlCheck.Reason.UNSUPPORTED_SCHEME)
            bareDomain.matches(text) -> "https://$text"
            else -> return invalid(UrlCheck.Reason.NOT_A_URL)
        }

        val uri = parse(withScheme) ?: return invalid(UrlCheck.Reason.NOT_A_URL)
        val scheme = uri.scheme?.lowercase() ?: return invalid(UrlCheck.Reason.NOT_A_URL)
        if (scheme !in allowedSchemes) return invalid(UrlCheck.Reason.UNSUPPORTED_SCHEME)
        if (uri.rawUserInfo != null) return invalid(UrlCheck.Reason.CREDENTIALS)

        val host = uri.host?.lowercase()?.trimEnd('.')
        if (host.isNullOrEmpty()) return invalid(UrlCheck.Reason.MISSING_HOST)
        if (isLocal(host)) return invalid(UrlCheck.Reason.LOCAL_ADDRESS)

        // Se arma con las partes "raw" para no volver a codificar lo que ya venía codificado.
        val normalized = buildString {
            append(scheme).append("://").append(host)
            if (uri.port != -1) append(':').append(uri.port)
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
        return UrlCheck.Valid(url = normalized, host = host)
    }

    /**
     * Busca la primera URL válida dentro de un texto compartido por otra app
     * ("Mirá este video https://… ¡buenísimo!"). Devuelve `null` si no hay ninguna.
     */
    fun findUrl(text: String): UrlCheck.Valid? = urlInText.findAll(text)
        .map { trimTrailingPunctuation(it.value) }
        .map { validate(it) }
        .filterIsInstance<UrlCheck.Valid>()
        .firstOrNull()

    /** Parsea aceptando hosts internacionales (`ñandú.com.ar`), que `URI` no reconoce como host. */
    private fun parse(text: String): URI? {
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.host != null || uri.rawAuthority == null) return uri
        val authority = uri.rawAuthority
        if (authority.contains('@')) return uri // credenciales: se rechazan después
        val hostPart = authority.substringBefore(':')
        val portPart = authority.substring(hostPart.length)
        val asciiHost = try {
            IDN.toASCII(hostPart, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            URI(text.replaceFirst(authority, asciiHost + portPart))
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun isLocal(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
        val ipv4 = parseIpv4(host)
        if (ipv4 != null) return isLocalIpv4(ipv4)
        // IPv6 solo llega entre corchetes. Con ':' InetAddress lo trata como literal: no hace DNS.
        if (!host.startsWith("[")) return false
        val address = try {
            InetAddress.getByName(host.removePrefix("[").removeSuffix("]"))
        } catch (_: Exception) {
            return true // literal IPv6 inválido: se rechaza
        }
        if (address is Inet4Address) return isLocalIpv4(address.address.map { it.toInt() and 0xFF })
        return address is Inet6Address &&
            (
                address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isAnyLocalAddress ||
                    (address.address[0].toInt() and 0xFE) == 0xFC
                )
    }

    /** `a.b.c.d` con cuatro octetos decimales; cualquier otra forma no se considera IP. */
    private fun parseIpv4(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            part.toInt().takeIf { it <= 255 } ?: return null
        }
    }

    private fun isLocalIpv4(o: List<Int>): Boolean = o[0] == 0 ||
        o[0] == 10 ||
        o[0] == 127 ||
        (o[0] == 169 && o[1] == 254) ||
        (o[0] == 172 && o[1] in 16..31) ||
        (o[0] == 192 && o[1] == 168) ||
        (o[0] == 100 && o[1] in 64..127)

    /** Quita signos que suelen quedar pegados al final de una URL dentro de un texto. */
    private fun trimTrailingPunctuation(url: String): String {
        var result = url.trimEnd('.', ',', ';', ':', '!', '?', '"', '\'', '»', '”', '’')
        while (result.endsWith(")") && result.count { it == ')' } > result.count { it == '(' }) {
            result = result.dropLast(1).trimEnd('.', ',', ';', ':', '!', '?')
        }
        return result
    }

    private fun invalid(reason: UrlCheck.Reason) = UrlCheck.Invalid(reason)
}
