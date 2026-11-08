package com.fabapps.mediaforge.domain.url

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTest {
    private fun url(text: String?, subject: String? = null) = (SharedText.findUrl(text, subject) as UrlCheck.Valid).url

    private fun reason(text: String?, subject: String? = null) =
        (SharedText.findUrl(text, subject) as UrlCheck.Invalid).reason

    @Test
    fun youtubeAppSharesOnlyTheLink() {
        assertEquals("https://youtu.be/aqz-KE-bpKQ?si=abc", url("https://youtu.be/aqz-KE-bpKQ?si=abc"))
    }

    @Test
    fun linkInsideTextIsFound() {
        assertEquals(
            "https://vm.tiktok.com/ZM123/",
            url("Mirá este video de @alguien en TikTok: https://vm.tiktok.com/ZM123/ ¡genial!"),
        )
    }

    @Test
    fun linkWithoutSchemeGetsHttps() {
        assertEquals("https://www.youtube.com/watch?v=x", url("  www.youtube.com/watch?v=x \n"))
    }

    @Test
    fun linkInSubjectIsUsedWhenTextHasNone() {
        assertEquals("https://example.com/v/1", url("Un video", subject = "https://example.com/v/1"))
        assertEquals("https://example.com/v/2", url(null, subject = "Video: https://example.com/v/2"))
    }

    @Test
    fun textLinkWinsOverSubject() {
        assertEquals("https://a.com/1", url("https://a.com/1", subject = "https://b.com/2"))
    }

    @Test
    fun firstValidLinkIsTakenSkippingLocalOnes() {
        assertEquals("https://example.com/ok", url("http://localhost/x y https://example.com/ok"))
    }

    @Test
    fun textWithoutLinkExplainsWhy() {
        assertEquals(UrlCheck.Reason.NOT_A_URL, reason("solo un mensaje sin enlace"))
        assertEquals(UrlCheck.Reason.UNSUPPORTED_SCHEME, reason("file:///sdcard/video.mp4"))
        assertEquals(UrlCheck.Reason.LOCAL_ADDRESS, reason("http://192.168.0.1/admin"))
        assertEquals(UrlCheck.Reason.NOT_A_URL, reason(null, subject = "solo asunto"))
    }

    @Test
    fun emptyShareIsEmpty() {
        assertEquals(UrlCheck.Reason.EMPTY, reason(null))
        assertEquals(UrlCheck.Reason.EMPTY, reason("   ", subject = ""))
    }

    @Test
    fun hugeTextIsCutBeforeSearching() {
        val padding = "a ".repeat(SharedText.MAX_LENGTH)
        assertEquals(UrlCheck.Reason.TOO_LONG, reason(padding + "https://example.com/late"))
        assertEquals("https://example.com/early", url("https://example.com/early " + padding))
    }

    @Test
    fun optionLikeTextIsNeverAUrl() {
        assertEquals(UrlCheck.Reason.NOT_A_URL, reason("--exec rm"))
    }
}
