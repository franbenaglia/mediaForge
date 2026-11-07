package com.fabapps.mediaforge.domain.download

import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.SourceSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOptionsTest {
    private fun info(vararg formats: MediaFormat, duration: Long? = 100) = MediaInfo(
        id = "x",
        sourceUrl = "https://example.com/x",
        title = "x",
        site = SourceSite("Generic", "Generic"),
        durationSeconds = duration,
        formats = formats.toList(),
    )

    private fun video(id: String, height: Int, codec: String = "avc1.64002A", size: Long? = 1000, fps: Double = 30.0) =
        MediaFormat(id, "mp4", videoCodec = codec, height = height, fps = fps, fileSizeBytes = size)

    private fun audio(id: String, codec: String = "mp4a.40.2", kbps: Double = 128.0, size: Long? = 100) =
        MediaFormat(id, "m4a", audioCodec = codec, bitrateKbps = kbps, fileSizeBytes = size)

    @Test
    fun oneOptionPerHeightFromHighestToLowest() {
        val options = VideoOptions.from(info(video("a", 360), video("b", 1080), video("c", 720), audio("x")))
        assertEquals(listOf(1080, 720, 360), options.map { it.height })
    }

    @Test
    fun separateVideoIsMergedWithBestAudioAndSizesAdd() {
        val option = VideoOptions.from(info(video("299", 1080, size = 2000), audio("140", size = 300))).single()
        assertEquals(listOf("299", "140"), option.selection.formatIds)
        assertEquals(1080, option.selection.maxHeight)
        assertEquals(2300L, option.estimatedBytes)
        assertFalse(option.sizeIsApproximate)
        assertTrue(option.hasAudio)
    }

    @Test
    fun prefersH264ThenVp9ThenAv1AtTheSameHeight() {
        val formats =
            arrayOf(video("av1", 720, "av01.0.08M.08"), video("vp9", 720, "vp9"), video("h264", 720), audio("a"))
        assertEquals("h264", VideoOptions.from(info(*formats)).single().selection.formatIds.first())
        val noH264 = arrayOf(video("av1", 720, "av01.0.08M.08"), video("vp9", 720, "vp9"), audio("a"))
        assertEquals("vp9", VideoOptions.from(info(*noH264)).single().selection.formatIds.first())
    }

    @Test
    fun prefersHigherFpsAtTheSameCodec() {
        val formats = arrayOf(video("30", 720, fps = 30.0), video("60", 720, fps = 60.0), audio("a"))
        val option = VideoOptions.from(info(*formats)).single()
        assertEquals("60", option.selection.formatIds.first())
        assertEquals(60.0, option.fps)
    }

    @Test
    fun audioPrefersAacOverOpusAndAvoidsDrc() {
        val formats = arrayOf(
            video("v", 720),
            audio("251", "opus", 160.0),
            audio("140-drc", kbps = 200.0),
            audio("140", kbps = 129.0),
        )
        assertEquals(listOf("v", "140"), VideoOptions.from(info(*formats)).single().selection.formatIds)
    }

    @Test
    fun lowHeightsUseLighterAudio() {
        val formats = arrayOf(video("hd", 720), video("sd", 480), audio("hi", kbps = 384.0), audio("mid", kbps = 129.0))
        val (hd, sd) = VideoOptions.from(info(*formats))
        assertEquals(listOf("hd", "hi"), hd.selection.formatIds)
        assertEquals(listOf("sd", "mid"), sd.selection.formatIds)
    }

    @Test
    fun combinedFormatIsUsedWhenThereIsNoSeparateAudio() {
        val hls = MediaFormat("hls-380", "mp4", "avc1.42001e", "mp4a.40.2", height = 240, bitrateKbps = 460.56)
        val option = VideoOptions.from(info(hls, duration = 596)).single()
        assertEquals(listOf("hls-380"), option.selection.formatIds)
        // 460,56 kbps × 596 s / 8
        assertEquals(34_311_720L, option.estimatedBytes)
        assertTrue(option.sizeIsApproximate)
        assertTrue(option.hasAudio)
    }

    @Test
    fun separateVideoBeatsCombinedAtTheSameHeight() {
        val combined = MediaFormat("18", "mp4", "avc1.42001E", "mp4a.40.2", height = 360, fileSizeBytes = 500)
        val formats = arrayOf(combined, video("134", 360), audio("140"))
        assertEquals(listOf("134", "140"), VideoOptions.from(info(*formats)).single().selection.formatIds)
    }

    @Test
    fun videoWithoutAnyAudioIsMarkedSilent() {
        val option = VideoOptions.from(info(video("v", 480))).single()
        assertEquals(listOf("v"), option.selection.formatIds)
        assertFalse(option.hasAudio)
    }

    @Test
    fun unknownSizeIsNull() {
        val option = VideoOptions.from(info(video("v", 480, size = null), audio("a"), duration = null)).single()
        assertNull(option.estimatedBytes)
        assertTrue(option.sizeIsApproximate)
    }

    @Test
    fun withoutFormatsOffersSingleGenericBest() {
        val option = VideoOptions.from(info(audio("a"))).single()
        assertNull(option.height)
        assertTrue(option.selection.formatIds.isEmpty())
    }
}
