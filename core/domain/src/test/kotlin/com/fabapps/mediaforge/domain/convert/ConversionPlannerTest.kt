package com.fabapps.mediaforge.domain.convert

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos de main.md §5.1. */
class ConversionPlannerTest {
    private fun media(video: String? = "h264", audio: String? = "aac", height: Int = 1080, kbps: Double? = 128.0) =
        LocalMedia(
            displayName = "x",
            sizeBytes = 10_000_000,
            durationSeconds = 100.0,
            video = video?.let { VideoStream(it, 1920, height, 30.0) },
            audio = audio?.let { AudioStream(it, kbps, 2, 44_100) },
        )

    private fun plan(media: LocalMedia, target: ConversionTarget): ConversionPlan =
        (ConversionPlanner.plan(media, target) as Outcome.Success).value

    private fun video(container: VideoContainer, maxHeight: Int? = null) = ConversionTarget.Video(container, maxHeight)

    private fun audio(format: AudioFormat, kbps: Int? = null) = ConversionTarget.Audio(format, kbps)

    @Test
    fun mp4ToMkvAndMkvToMp4AreRemux() {
        val toMkv = plan(media(), video(VideoContainer.MKV))
        assertEquals(StreamPlan.Copy, toMkv.video)
        assertEquals(StreamPlan.Copy, toMkv.audio)
        assertFalse(toMkv.reencodes)
        assertEquals("mkv", toMkv.extension)

        val toMp4 = plan(media(), video(VideoContainer.MP4))
        assertFalse(toMp4.reencodes)
        assertEquals("video/mp4", toMp4.mimeType)
    }

    @Test
    fun mkvWithOpusToMp4CopiesVideoAndEncodesAudio() {
        val result = plan(media(video = "h264", audio = "opus"), video(VideoContainer.MP4))
        assertEquals(StreamPlan.Copy, result.video)
        assertEquals(StreamPlan.Encode("aac", 160), result.audio)
    }

    @Test
    fun webmVp9OpusToMp4CopiesVp9AndEncodesOpus() {
        val result = plan(media(video = "vp9", audio = "opus"), video(VideoContainer.MP4))
        assertEquals(StreamPlan.Copy, result.video)
        assertEquals(StreamPlan.Encode("aac", 160), result.audio)
    }

    @Test
    fun vp8ToMp4EncodesH264() {
        assertEquals(
            StreamPlan.Encode("h264"),
            plan(media(video = "vp8", audio = "vorbis"), video(VideoContainer.MP4)).video,
        )
    }

    @Test
    fun h264ToWebmEncodesVp9AndOpus() {
        val result = plan(media(), video(VideoContainer.WEBM))
        assertEquals(StreamPlan.Encode("vp9"), result.video)
        assertEquals(StreamPlan.Encode("opus", 128), result.audio)
    }

    @Test
    fun movToMp4CopiesH264() {
        assertFalse(plan(media(video = "h264", audio = "aac"), video(VideoContainer.MP4)).reencodes)
    }

    @Test
    fun lowerResolutionForcesVideoEncodeButKeepsAudio() {
        val result = plan(media(height = 1080), video(VideoContainer.MP4, maxHeight = 720))
        assertEquals(StreamPlan.Encode("h264"), result.video)
        assertEquals(720, result.scaleToHeight)
        assertEquals(StreamPlan.Copy, result.audio)
    }

    @Test
    fun maxHeightAboveTheSourceDoesNothing() {
        val result = plan(media(height = 480), video(VideoContainer.MP4, maxHeight = 720))
        assertEquals(StreamPlan.Copy, result.video)
        assertEquals(null, result.scaleToHeight)
    }

    @Test
    fun videoWithoutAudioDropsAudio() {
        assertEquals(StreamPlan.Drop, plan(media(audio = null), video(VideoContainer.MKV)).audio)
    }

    @Test
    fun mp4ToMp3DropsVideoAndEncodes() {
        val result = plan(media(), audio(AudioFormat.MP3))
        assertEquals(StreamPlan.Drop, result.video)
        // Origen de 128 kbps: automático = 128.
        assertEquals(StreamPlan.Encode("mp3", 128), result.audio)
        assertEquals("mp3", result.extension)
        assertEquals(1_600_000L, ConversionPlanner.estimateBytes(media(), result))
    }

    @Test
    fun mp4ToM4aCopiesAac() {
        val result = plan(media(), audio(AudioFormat.M4A))
        assertEquals(StreamPlan.Copy, result.audio)
        assertEquals(10_000_000L, ConversionPlanner.estimateBytes(media(), result))
    }

    @Test
    fun choosingABitrateReencodesEvenWithTheSameCodec() {
        assertEquals(StreamPlan.Encode("aac", 256), plan(media(), audio(AudioFormat.M4A, 256)).audio)
    }

    @Test
    fun bitrateNotOfferedFallsBackToAuto() {
        assertEquals(StreamPlan.Encode("mp3", 128), plan(media(), audio(AudioFormat.MP3, 999)).audio)
    }

    @Test
    fun wavFlacAndMp3Conversions() {
        val wav = media(video = null, audio = "pcm_s16le", kbps = 1411.0)
        assertEquals(StreamPlan.Encode("mp3", 320), plan(wav, audio(AudioFormat.MP3)).audio)
        val toFlac = plan(wav, audio(AudioFormat.FLAC))
        assertEquals(StreamPlan.Encode("flac", null), toFlac.audio)
        assertEquals(17_640_000L, ConversionPlanner.estimateBytes(wav, toFlac))

        val flac = media(video = null, audio = "flac", kbps = null)
        assertEquals(StreamPlan.Encode("mp3", 192), plan(flac, audio(AudioFormat.MP3)).audio)
        assertEquals(StreamPlan.Copy, plan(flac, audio(AudioFormat.FLAC)).audio)

        val mp3 = media(video = null, audio = "mp3")
        assertEquals(StreamPlan.Encode("pcm_s16le", null), plan(mp3, audio(AudioFormat.WAV)).audio)
    }

    @Test
    fun audioToVideoIsNotPossible() {
        val result = ConversionPlanner.plan(media(video = null, audio = "mp3"), video(VideoContainer.MP4))
        assertTrue((result as Outcome.Failure).error is MediaError.UnsupportedConversion)
    }

    @Test
    fun silentVideoToAudioIsNotPossible() {
        val result = ConversionPlanner.plan(media(audio = null), audio(AudioFormat.MP3))
        assertTrue((result as Outcome.Failure).error is MediaError.UnsupportedConversion)
    }
}
