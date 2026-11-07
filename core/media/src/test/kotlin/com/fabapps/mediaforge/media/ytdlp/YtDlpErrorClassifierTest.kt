package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.MediaError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorClassifierTest {

    private val outdatedWarning = """
        WARNING: Your yt-dlp version (2025.11.12) is older than 90 days!
                 It is strongly recommended to always use the latest version.
                 Run "yt-dlp --update" or "yt-dlp -U" to update.
                 To suppress this warning, add --no-update to your command/config.
    """.trimIndent()

    // Mensajes reales de la prueba de la Etapa 0 (spikes/engine-spike/results-2026-11-06.log).

    @Test
    fun vimeoLoginRequiredFromStage0() {
        val stderr = """
            $outdatedWarning
            WARNING: [vimeo] The extractor is attempting impersonation, but no impersonate target is available.
            ERROR: [vimeo] 1084537: The web client only works when logged-in. Use --cookies, --cookies-from-browser, --username and --password, --netrc-cmd, or --netrc (vimeo) to provide account credentials.
        """.trimIndent()
        val error = YtDlpErrorClassifier.classify(stderr)
        assertTrue(error is MediaError.LoginRequired)
        assertTrue(error.detail!!.startsWith("ERROR: [vimeo] 1084537"))
    }

    @Test
    fun youtube403WithOutdatedEngineSuggestsUpdateFromStage0() {
        val stderr = """
            $outdatedWarning
            WARNING: [youtube] aqz-KE-bpKQ: Some web client https formats have been skipped as they are missing a url. YouTube is forcing SABR streaming for this client.
            ERROR: unable to download video data: HTTP Error 403: Forbidden
        """.trimIndent()
        assertTrue(YtDlpErrorClassifier.classify(stderr) is MediaError.EngineOutdated)
    }

    @Test
    fun forbiddenWithUpToDateEngineIsAccessDenied() {
        val error = YtDlpErrorClassifier.classify("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertTrue(error is MediaError.AccessDenied)
    }

    @Test
    fun dnsFailureIsNetworkFromStage0() {
        val stderr = "ERROR: [youtube] aqz-KE-bpKQ: Unable to download webpage: " +
            "<urlopen error [Errno 7] No address associated with hostname>"
        assertTrue(YtDlpErrorClassifier.classify(stderr) is MediaError.Network)
    }

    @Test
    fun unsupportedUrl() {
        val error = YtDlpErrorClassifier.classify("ERROR: Unsupported URL: https://example.com/page")
        assertTrue(error is MediaError.UnsupportedSite)
    }

    @Test
    fun invalidUrl() {
        val error = YtDlpErrorClassifier.classify(
            "ERROR: 'notaurl' is not a valid URL. Set --default-search \"ytsearch\"",
        )
        assertTrue(error is MediaError.InvalidUrl)
    }

    @Test
    fun extractionFailureMeansOutdatedEngine() {
        val stderr = "ERROR: [youtube] abc: Unable to extract yt initial data; please report this issue on " +
            "https://github.com/yt-dlp/yt-dlp/issues?q= , filling out the appropriate issue template."
        assertTrue(YtDlpErrorClassifier.classify(stderr) is MediaError.EngineOutdated)
    }

    @Test
    fun privateVideoAndAgeGateNeedLogin() {
        assertTrue(
            YtDlpErrorClassifier.classify(
                "ERROR: [youtube] x: Private video. Sign in if you've been granted access",
            ) is MediaError.LoginRequired,
        )
        assertTrue(
            YtDlpErrorClassifier.classify(
                "ERROR: [youtube] x: Sign in to confirm your age. This video may be inappropriate",
            ) is MediaError.LoginRequired,
        )
    }

    @Test
    fun botCheckIsAccessDeniedNotLogin() {
        val error = YtDlpErrorClassifier.classify(
            "ERROR: [youtube] x: Sign in to confirm you’re not a bot. Use --cookies-from-browser",
        )
        assertTrue(error is MediaError.AccessDenied)
    }

    @Test
    fun drmIsAccessDenied() {
        assertTrue(
            YtDlpErrorClassifier.classify("ERROR: [x] 1: This video is DRM protected") is MediaError.AccessDenied,
        )
    }

    @Test
    fun geoRestrictionIsAccessDenied() {
        val error = YtDlpErrorClassifier.classify(
            "ERROR: [dailymotion] x: This content is not available in your country",
        )
        assertTrue(error is MediaError.AccessDenied)
    }

    @Test
    fun rateLimitIsNetwork() {
        assertTrue(
            YtDlpErrorClassifier.classify("ERROR: [tiktok] 1: HTTP Error 429: Too Many Requests") is MediaError.Network,
        )
    }

    @Test
    fun diskFullIsStorage() {
        val error = YtDlpErrorClassifier.classify("ERROR: unable to write data: [Errno 28] No space left on device")
        assertTrue(error is MediaError.InsufficientStorage)
    }

    @Test
    fun warningsAloneDoNotDecideTheCategory() {
        // "unable to extract" como WARNING no debe ganarle al ERROR real.
        val stderr = """
            WARNING: [youtube] unable to extract yt initial data; please report this issue
            ERROR: Unsupported URL: https://example.com/a
        """.trimIndent()
        assertTrue(YtDlpErrorClassifier.classify(stderr) is MediaError.UnsupportedSite)
    }

    @Test
    fun unknownKeepsDetailAndEmptyHasNone() {
        val error = YtDlpErrorClassifier.classify("ERROR: something new happened")
        assertTrue(error is MediaError.Unknown)
        assertEquals("ERROR: something new happened", error.detail)
        assertNull(YtDlpErrorClassifier.classify("").detail)
    }

    @Test
    fun detailNeverContainsUrlQueries() {
        val stderr = "ERROR: unable to download video data: HTTP Error 403: Forbidden " +
            "https://rr3---sn.googlevideo.com/videoplayback?expire=1&sig=SECRET&ip=1.2.3.4"
        val detail = YtDlpErrorClassifier.classify(stderr).detail!!
        assertFalse(detail.contains("SECRET"))
        assertFalse(detail.contains("ip="))
        assertTrue(detail.contains("https://rr3---sn.googlevideo.com/videoplayback"))
    }

    @Test
    fun redactKeepsUrlsWithoutQuery() {
        assertEquals(
            "see https://a.com/x and https://b.com/y",
            YtDlpErrorClassifier.redact("see https://a.com/x#t and https://b.com/y?k=v"),
        )
    }
}
