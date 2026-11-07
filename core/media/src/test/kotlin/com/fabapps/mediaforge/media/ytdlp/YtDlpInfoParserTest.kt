package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpInfoParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/ytdlp/$name")) { "missing fixture $name" }.readText()

    private fun parseOk(name: String): MediaInfo {
        val result = YtDlpInfoParser.parse(fixture(name))
        check(result is Outcome.Success) { "expected success, got $result" }
        return result.value
    }

    @Test
    fun youtubeMetadata() {
        val info = parseOk("youtube.json")
        assertEquals("aqz-KE-bpKQ", info.id)
        assertEquals("Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film", info.title)
        assertEquals("Youtube", info.site.extractorKey)
        assertEquals("YouTube", info.site.displayName)
        assertEquals("Blender", info.uploader)
        assertEquals(635L, info.durationSeconds)
        assertEquals(LocalDate.of(2014, 1, 10), info.uploadDate)
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", info.sourceUrl)
        assertEquals("https://i.ytimg.com/vi/aqz-KE-bpKQ/maxresdefault.jpg", info.thumbnailUrl)
    }

    @Test
    fun youtubeAutomaticCaptionsDoNotCountAsSubtitles() {
        assertFalse(parseOk("youtube.json").hasSubtitles)
    }

    @Test
    fun storyboardsAreDropped() {
        val ids = parseOk("youtube.json").formats.map { it.formatId }
        assertFalse("sb0" in ids)
        assertEquals(listOf("139", "251", "18", "137", "313", "401"), ids)
    }

    @Test
    fun audioOnlyVideoOnlyAndMuxedAreDistinguished() {
        val formats = parseOk("youtube.json").formats.associateBy { it.formatId }
        formats.getValue("251").let {
            assertTrue(it.hasAudio)
            assertFalse(it.hasVideo)
            assertEquals("opus", it.audioCodec)
            assertEquals("webm", it.container)
        }
        formats.getValue("137").let {
            assertTrue(it.hasVideo)
            assertFalse(it.hasAudio)
            assertEquals(1080, it.height)
            assertEquals(60.0, it.fps)
        }
        formats.getValue("18").let {
            assertTrue(it.hasVideo && it.hasAudio)
        }
    }

    @Test
    fun exactApproximateAndMissingSizes() {
        val formats = parseOk("youtube.json").formats.associateBy { it.formatId }
        assertEquals(342_900_123L, formats.getValue("137").fileSizeBytes)
        assertFalse(formats.getValue("137").fileSizeIsApproximate)

        // filesize_approx en notación científica: no rompe el parseo.
        assertEquals(2_071_400_000L, formats.getValue("313").fileSizeBytes)
        assertTrue(formats.getValue("313").fileSizeIsApproximate)

        assertNull(formats.getValue("401").fileSizeBytes)
    }

    @Test
    fun dailymotionHlsFormatsWithoutSizes() {
        val info = parseOk("dailymotion.json")
        assertEquals("Dailymotion", info.site.displayName)
        assertEquals(596L, info.durationSeconds)
        assertTrue(info.hasSubtitles)
        assertEquals(listOf(288, 1080), info.formats.map { it.height })
        assertTrue(info.formats.all { it.fileSizeBytes == null && it.hasAudio && it.hasVideo })
        assertEquals(2176.5, info.formats.last().bitrateKbps)
    }

    @Test
    fun minimalOutputUsesFallbacks() {
        val info = parseOk("minimal.json")
        assertEquals("abc123", info.title)
        assertEquals("Generic", info.site.displayName)
        assertNull(info.uploader)
        assertNull(info.durationSeconds)
        assertNull(info.uploadDate)
        // Códecs ausentes = desconocidos (no "none"): el formato se conserva.
        assertEquals(1, info.formats.size)
        assertNull(info.formats.single().videoCodec)
    }

    @Test
    fun playlistIsRejected() {
        val result = YtDlpInfoParser.parse(fixture("playlist.json"))
        assertTrue((result as Outcome.Failure).error is MediaError.UnsupportedSite)
    }

    @Test
    fun invalidJsonIsAFailureNotACrash() {
        assertTrue(YtDlpInfoParser.parse("WARNING: something\n{oops") is Outcome.Failure)
        assertTrue(YtDlpInfoParser.parse("") is Outcome.Failure)
        assertTrue(YtDlpInfoParser.parse("[]") is Outcome.Failure)
    }

    @Test
    fun missingIdIsAFailure() {
        assertTrue(YtDlpInfoParser.parse("""{"title": "x"}""") is Outcome.Failure)
    }

    @Test
    fun badDateAndNegativeDurationAreIgnored() {
        val info = (
            YtDlpInfoParser.parse(
                """{"id": "a", "upload_date": "2014-01-10", "duration": -1}""",
            ) as Outcome.Success
            ).value
        assertNull(info.uploadDate)
        assertNull(info.durationSeconds)
    }

    @Test
    fun nonHttpThumbnailIsDropped() {
        val info = (YtDlpInfoParser.parse("""{"id": "a", "thumbnail": "file:///etc/x.jpg"}""") as Outcome.Success).value
        assertNull(info.thumbnailUrl)
    }

    @Test
    fun longDescriptionIsTruncated() {
        val long = "x".repeat(YtDlpInfoParser.MAX_DESCRIPTION_CHARS + 100)
        val info = (YtDlpInfoParser.parse("""{"id": "a", "description": "$long"}""") as Outcome.Success).value
        assertEquals(YtDlpInfoParser.MAX_DESCRIPTION_CHARS, info.description?.length)
    }

    // Salida real de yt-dlp 2025.11.12 en el teléfono (2026-11-07), recortada por EngineDeviceTest.

    @Test
    fun realYoutubeOutputFromPhone() {
        val info = parseOk("youtube-real.json")
        assertEquals("aqz-KE-bpKQ", info.id)
        assertEquals("YouTube", info.site.displayName)
        assertEquals(635L, info.durationSeconds)
        // 39 formatos menos 4 storyboards: los mismos 35 que contó la app en el teléfono.
        assertEquals(35, info.formats.size)
        assertEquals(
            listOf(144, 240, 360, 480, 720, 1080, 1440, 2160),
            info.formats.mapNotNull { it.height }.distinct().sorted(),
        )
        assertTrue(info.formats.any { it.hasAudio && !it.hasVideo })
        assertTrue(info.formats.any { it.videoCodec?.startsWith("av01") == true })
    }

    // Salida real de yt-dlp 2026.10.26 en el teléfono (2026-11-07).

    @Test
    fun realDailymotionOutputFromPhone() {
        val info = parseOk("dailymotion-real.json")
        assertEquals("x2kdx9f", info.id)
        assertEquals("Dailymotion", info.site.displayName)
        assertEquals("Philou", info.uploader)
        assertEquals(596L, info.durationSeconds)
        assertEquals(LocalDate.of(2015, 3, 23), info.uploadDate)
        val format = info.formats.single()
        assertEquals("hls-380", format.formatId)
        assertEquals(240, format.height)
        assertTrue(format.hasVideo && format.hasAudio)
        assertNull(format.fps)
        assertNull(format.fileSizeBytes)
    }
}
