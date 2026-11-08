package com.fabapps.mediaforge.domain.convert

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.ImageFormat
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTest {
    @Test
    fun parsesTheUsualWaysOfWritingATime() {
        assertEquals(83.0, TimeCodes.parse("83")!!, 0.0)
        assertEquals(83.0, TimeCodes.parse("1:23")!!, 0.0)
        assertEquals(83.0, TimeCodes.parse(" 01:23 ")!!, 0.0)
        assertEquals(3723.0, TimeCodes.parse("1:02:03")!!, 0.0)
        assertEquals(83.5, TimeCodes.parse("1:23.5")!!, 1e-9)
        assertEquals(83.25, TimeCodes.parse("1:23,25")!!, 1e-9)
        assertEquals(0.0, TimeCodes.parse("0")!!, 0.0)
        // Muchos minutos sin horas.
        assertEquals(6000.0, TimeCodes.parse("100:00")!!, 0.0)
    }

    @Test
    fun rejectsWhatIsNotATime() {
        listOf("", "abc", "-5", "1:60", "1:75:00", "1::2", "1:2:3:4", "1.2.3", "1:23.", "12a").forEach {
            assertNull(it, TimeCodes.parse(it))
        }
    }

    @Test
    fun formatsBackToShortText() {
        assertEquals("0:00", TimeCodes.format(0.0))
        assertEquals("1:23", TimeCodes.format(83.0))
        assertEquals("1:23.5", TimeCodes.format(83.5))
        assertEquals("1:02:03", TimeCodes.format(3723.0))
        assertEquals("0:10", TimeCodes.format(9.98))
    }

    @Test
    fun validatesAgainstTheDuration() {
        assertNull(Clip(10.0, 20.0).problem(100.0))
        assertNull(Clip(10.0).problem(100.0))
        assertNull(Clip(10.0, 20.0).problem(null))
        // La duración del análisis rápido puede quedarse un poco corta.
        assertNull(Clip(10.0, 100.3).problem(100.0))
        assertEquals(ClipProblem.INVALID, Clip(-1.0).problem(100.0))
        assertEquals(ClipProblem.TOO_SHORT, Clip(10.0, 10.2).problem(100.0))
        assertEquals(ClipProblem.TOO_SHORT, Clip(20.0, 10.0).problem(100.0))
        assertEquals(ClipProblem.BEYOND_END, Clip(100.0).problem(100.0))
        assertEquals(ClipProblem.BEYOND_END, Clip(10.0, 150.0).problem(100.0))
    }

    @Test
    fun lengthUsesTheDurationWhenThereIsNoEnd() {
        assertEquals(10.0, Clip(10.0, 20.0).lengthSeconds(null)!!, 0.0)
        assertEquals(90.0, Clip(10.0).lengthSeconds(100.0)!!, 0.0)
        assertNull(Clip(10.0).lengthSeconds(null))
    }

    private val media = LocalMedia(
        displayName = "x.mp4",
        sizeBytes = 10_000_000,
        durationSeconds = 100.0,
        video = VideoStream("h264", 1920, 1080, 30.0),
        audio = AudioStream("aac", 128.0, 2, 44_100),
    )

    private fun plan(target: ConversionTarget, clip: Clip?) =
        (ConversionPlanner.plan(media, target, clip) as Outcome.Success).value

    @Test
    fun quickCutCopiesAndExactCutEncodesTheVideo() {
        val target = ConversionTarget.Video(VideoContainer.MP4)
        assertEquals(StreamPlan.Copy, plan(target, Clip(10.0, 20.0)).video)
        val exact = plan(target, Clip(10.0, 20.0, exact = true))
        assertEquals(StreamPlan.Encode("h264"), exact.video)
        // El audio se corta bien aunque se copie.
        assertEquals(StreamPlan.Copy, exact.audio)
    }

    @Test
    fun audioFragmentDoesNotDependOnExact() {
        val target = ConversionTarget.Audio(AudioFormat.M4A)
        assertEquals(StreamPlan.Copy, plan(target, Clip(10.0, 20.0, exact = true)).audio)
    }

    @Test
    fun clipOutsideTheFileCannotBePlanned() {
        val result = ConversionPlanner.plan(media, ConversionTarget.Video(VideoContainer.MP4), Clip(10.0, 500.0))
        assertTrue((result as Outcome.Failure).error is MediaError.UnsupportedConversion)
    }

    @Test
    fun frameIsAnImageAtTheOriginalResolution() {
        val jpg = plan(ConversionTarget.Image(12.5), clip = null)
        assertEquals(StreamPlan.Encode("mjpeg"), jpg.video)
        assertEquals(StreamPlan.Drop, jpg.audio)
        assertEquals(null, jpg.scaleToHeight)
        assertEquals(12.5, jpg.frameAtSeconds!!, 0.0)
        assertEquals("image/jpeg", jpg.mimeType)
        val png = plan(ConversionTarget.Image(0.0, ImageFormat.PNG), clip = Clip(50.0))
        assertEquals("png", png.extension)
        // El recorte no aplica a una imagen.
        assertEquals(0.0, png.frameAtSeconds!!, 0.0)
        assertEquals(1920L * 1080 * 4, ConversionPlanner.estimateBytes(media, png))
    }

    @Test
    fun frameNeedsVideoAndATimeInsideTheFile() {
        val noVideo = media.copy(video = null)
        listOf(
            ConversionPlanner.plan(noVideo, ConversionTarget.Image(1.0)),
            ConversionPlanner.plan(media, ConversionTarget.Image(100.0)),
            ConversionPlanner.plan(media, ConversionTarget.Image(-1.0)),
        ).forEach { assertTrue((it as Outcome.Failure).error is MediaError.UnsupportedConversion) }
    }

    @Test
    fun estimateIsProportional() {
        val copy = plan(ConversionTarget.Video(VideoContainer.MKV), Clip(10.0, 35.0))
        assertEquals(2_500_000L, ConversionPlanner.estimateBytes(media, copy, Clip(10.0, 35.0)))
        val mp3 = plan(ConversionTarget.Audio(AudioFormat.MP3, 128), Clip(0.0, 10.0))
        assertEquals(160_000L, ConversionPlanner.estimateBytes(media, mp3, Clip(0.0, 10.0)))
        assertEquals(10_000_000L, ConversionPlanner.estimateBytes(media, copy))
    }
}
