package com.fabapps.mediaforge.domain.url

import com.fabapps.mediaforge.domain.url.UrlCheck.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlValidatorTest {

    private fun valid(input: String): UrlCheck.Valid {
        val result = UrlValidator.validate(input)
        check(result is UrlCheck.Valid) { "expected valid for '$input', got $result" }
        return result
    }

    private fun reason(input: String): Reason {
        val result = UrlValidator.validate(input)
        check(result is UrlCheck.Invalid) { "expected invalid for '$input', got $result" }
        return result.reason
    }

    @Test
    fun acceptsCommonVideoUrls() {
        assertEquals("www.youtube.com", valid("https://www.youtube.com/watch?v=aqz-KE-bpKQ").host)
        assertEquals("https://youtu.be/aqz-KE-bpKQ?t=10", valid("https://youtu.be/aqz-KE-bpKQ?t=10").url)
        assertEquals("www.dailymotion.com", valid("https://www.dailymotion.com/video/x8abcd").host)
        assertEquals("http://example.com/a", valid("http://example.com/a").url)
    }

    @Test
    fun trimsSurroundingSpaces() {
        assertEquals("https://example.com/v", valid("  https://example.com/v \n").url)
    }

    @Test
    fun normalizesSchemeAndHostCaseButKeepsPath() {
        assertEquals("https://www.youtube.com/Watch?v=AbC", valid("HTTPS://WWW.YouTube.COM/Watch?v=AbC").url)
    }

    @Test
    fun keepsPercentEncodingAsIs() {
        assertEquals("https://example.com/a%20b?q=%C3%B1", valid("https://example.com/a%20b?q=%C3%B1").url)
    }

    @Test
    fun keepsPortAndFragment() {
        assertEquals("https://example.com:8443/v#t=5", valid("https://example.com:8443/v#t=5").url)
    }

    @Test
    fun addsHttpsToBareDomain() {
        assertEquals("https://www.youtube.com/watch?v=x", valid("www.youtube.com/watch?v=x").url)
        assertEquals("https://dailymotion.com", valid("dailymotion.com").url)
    }

    @Test
    fun convertsInternationalHostToAscii() {
        assertEquals("xn--and-6ma2c.com.ar", valid("https://ñandú.com.ar/video").host)
    }

    @Test
    fun rejectsEmptyAndTooLong() {
        assertEquals(Reason.EMPTY, reason("   "))
        assertEquals(Reason.TOO_LONG, reason("https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH)))
    }

    @Test
    fun rejectsDangerousSchemes() {
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("javascript:alert(1)"))
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("file:///etc/passwd"))
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("content://media/external/video/1"))
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("ftp://example.com/a.mp4"))
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("data:text/html,hi"))
        assertEquals(Reason.UNSUPPORTED_SCHEME, reason("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun rejectsSpacesAndControlCharacters() {
        assertEquals(Reason.NOT_A_URL, reason("https://example.com/a b"))
        assertEquals(Reason.NOT_A_URL, reason("https://example.com/\u0000"))
        assertEquals(Reason.NOT_A_URL, reason("https://example.com/\nhttps://evil.com"))
    }

    @Test
    fun rejectsAnythingThatLooksLikeACommandLineOption() {
        assertEquals(Reason.NOT_A_URL, reason("--exec=rm -rf /"))
        assertEquals(Reason.NOT_A_URL, reason("-o/sdcard/x"))
    }

    @Test
    fun rejectsPlainText() {
        assertEquals(Reason.NOT_A_URL, reason("hola mundo"))
        assertEquals(Reason.NOT_A_URL, reason("notaurl"))
    }

    @Test
    fun rejectsMissingHost() {
        assertEquals(Reason.MISSING_HOST, reason("https:///path"))
    }

    @Test
    fun rejectsCredentials() {
        assertEquals(Reason.CREDENTIALS, reason("https://user:pass@example.com/v"))
        assertEquals(Reason.CREDENTIALS, reason("https://user@example.com/v"))
    }

    @Test
    fun rejectsLocalAndPrivateAddresses() {
        listOf(
            "http://localhost/x",
            "http://127.0.0.1/x",
            "http://10.0.0.5/x",
            "http://172.16.0.1/x",
            "http://192.168.1.1/x",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/",
            "http://[::1]/x",
            "http://[fd00::1]/x",
            "http://printer.local/x",
        ).forEach { assertEquals(it, Reason.LOCAL_ADDRESS, reason(it)) }
    }

    @Test
    fun acceptsPublicIpLiterals() {
        assertEquals("8.8.8.8", valid("http://8.8.8.8/x").host)
        assertEquals("172.32.0.1", valid("http://172.32.0.1/").host)
    }

    @Test
    fun findsUrlInsideSharedText() {
        val text = "Mirá este video: https://www.youtube.com/watch?v=aqz-KE-bpKQ ¡buenísimo!"
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", UrlValidator.findUrl(text)?.url)
    }

    @Test
    fun findUrlTrimsTrailingPunctuationAndUnbalancedParenthesis() {
        assertEquals("https://example.com/v", UrlValidator.findUrl("(ver https://example.com/v).")?.url)
        assertEquals(
            "https://en.wikipedia.org/wiki/Big_Buck_Bunny_(film)",
            UrlValidator.findUrl("https://en.wikipedia.org/wiki/Big_Buck_Bunny_(film)")?.url,
        )
    }

    @Test
    fun findUrlSkipsInvalidAndReturnsFirstValid() {
        val text = "http://localhost/a y después https://www.dailymotion.com/video/x1"
        assertEquals("https://www.dailymotion.com/video/x1", UrlValidator.findUrl(text)?.url)
    }

    @Test
    fun findUrlReturnsNullWithoutUrls() {
        assertNull(UrlValidator.findUrl("no hay enlaces acá"))
        assertNull(UrlValidator.findUrl("javascript:alert(1)"))
    }
}
