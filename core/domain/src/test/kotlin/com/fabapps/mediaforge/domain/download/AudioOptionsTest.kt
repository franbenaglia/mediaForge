package com.fabapps.mediaforge.domain.download

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.SourceSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOptionsTest {
    private fun info(vararg formats: MediaFormat, duration: Long? = 100) = MediaInfo(
        id = "x",
        sourceUrl = "https://example.com/x",
        title = "x",
        site = SourceSite("Generic", "Generic"),
        durationSeconds = duration,
        formats = formats.toList(),
    )

    private fun audio(id: String, codec: String, kbps: Double, size: Long? = null) =
        MediaFormat(id, "m4a", audioCodec = codec, bitrateKbps = kbps, fileSizeBytes = size)

    private val aac = audio("140", "mp4a.40.2", 129.5, size = 1_600_000)
    private val opus = audio("251", "opus", 128.6, size = 1_610_000)
    private val video = MediaFormat("299", "mp4", videoCodec = "avc1", height = 1080)

    private fun option(format: AudioFormat, vararg formats: MediaFormat) =
        AudioOptions.from(info(*formats)).single { it.format == format }

    @Test
    fun mp3M4aAndOpusComeFirst() {
        val formats = AudioOptions.from(info(aac)).map { it.format }
        assertEquals(listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS), formats.take(3))
        assertEquals(AudioFormat.entries.toSet(), formats.toSet())
    }

    @Test
    fun matchingCodecIsCopiedWithoutBitrates() {
        val m4a = option(AudioFormat.M4A, aac, opus, video)
        assertTrue(m4a.copiesWithoutReencoding)
        assertEquals("140", m4a.sourceFormatId)
        assertTrue(m4a.bitrates.isEmpty())
        assertNull(m4a.selection(320).bitrateKbps)
        assertEquals(listOf("140"), m4a.selection().formatIds)

        val opusOption = option(AudioFormat.OPUS, aac, opus)
        assertTrue(opusOption.copiesWithoutReencoding)
        assertEquals("251", opusOption.sourceFormatId)
    }

    @Test
    fun copyEstimateUsesTheSourceSize() {
        val data = info(aac)
        val m4a = AudioOptions.from(data).single { it.format == AudioFormat.M4A }
        assertEquals(1_600_000L, m4a.estimatedBytes(sourceBytes = AudioOptions.sourceBytes(data, m4a)))
    }

    @Test
    fun mp3IsTranscodedFromOpusWithAutoAtLeastTheSource() {
        val mp3 = option(AudioFormat.MP3, aac, opus)
        assertFalse(mp3.copiesWithoutReencoding)
        assertEquals("251", mp3.sourceFormatId)
        assertEquals(listOf(128, 192, 256, 320), mp3.bitrates)
        assertEquals(128, mp3.autoKbps)
        assertEquals(128, mp3.selection().bitrateKbps)
        assertEquals(320, mp3.selection(320).bitrateKbps)
        // 320 kbps × 100 s / 8
        assertEquals(4_000_000L, mp3.estimatedBytes(320))
    }

    @Test
    fun autoPicksTheNextBitrateAboveTheSource() {
        assertEquals(192, option(AudioFormat.MP3, audio("a", "mp4a.40.2", 160.0)).autoKbps)
        assertEquals(320, option(AudioFormat.MP3, audio("a", "mp4a.40.2", 387.0)).autoKbps)
    }

    @Test
    fun warnsWhenBitrateExceedsTheSource() {
        val mp3 = option(AudioFormat.MP3, opus)
        assertFalse(mp3.exceedsSource(128))
        assertTrue(mp3.exceedsSource(192))
    }

    @Test
    fun invalidBitrateFallsBackToAuto() {
        assertEquals(128, option(AudioFormat.MP3, opus).selection(999).bitrateKbps)
    }

    @Test
    fun losslessHasNoBitratesAndWavSizeIsPcm() {
        val wav = option(AudioFormat.WAV, opus)
        assertTrue(wav.bitrates.isEmpty())
        assertNull(wav.selection().bitrateKbps)
        assertEquals(17_640_000L, wav.estimatedBytes())
        assertNull(option(AudioFormat.FLAC, opus).estimatedBytes())
    }

    @Test
    fun drcVariantsAreAvoided() {
        val drc = audio("140-drc", "mp4a.40.2", 200.0)
        assertEquals("140", option(AudioFormat.M4A, drc, aac).sourceFormatId)
    }

    @Test
    fun combinedFormatIsUsedWhenThereIsNoAudioTrack() {
        val hls = MediaFormat("hls-380", "mp4", "avc1.42001e", "mp4a.40.2", height = 240, bitrateKbps = 460.0)
        val m4a = option(AudioFormat.M4A, hls)
        assertEquals("hls-380", m4a.sourceFormatId)
        assertTrue(m4a.copiesWithoutReencoding)
        // El bitrate del combinado incluye el video: no sirve para avisar ni para elegir.
        val mp3 = option(AudioFormat.MP3, hls)
        assertNull(mp3.sourceKbps)
        assertFalse(mp3.exceedsSource(320))
        assertEquals(192, mp3.autoKbps)
    }

    @Test
    fun withoutFormatsEverythingIsAutomatic() {
        val mp3 = AudioOptions.from(info()).first()
        assertNull(mp3.sourceFormatId)
        assertTrue(mp3.selection().formatIds.isEmpty())
        assertEquals(192, mp3.selection().bitrateKbps)
    }
}
