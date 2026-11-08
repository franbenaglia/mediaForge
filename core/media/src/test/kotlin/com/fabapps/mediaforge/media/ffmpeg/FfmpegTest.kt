package com.fabapps.mediaforge.media.ffmpeg

import com.fabapps.mediaforge.domain.convert.Clip
import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.ConversionPlanner
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.ImageFormat
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.VideoContainer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegTest {
    private fun fixture(name: String) = checkNotNull(FfmpegTest::class.java.getResource("/ffmpeg/$name")).readText()

    private fun parse(name: String): LocalMedia = (FfprobeParser.parse(fixture(name), name) as Outcome.Success).value

    // ffprobe (salida real del teléfono)

    @Test
    fun probeMp4() {
        val media = parse("ffprobe-mp4-h264-aac.json")
        assertEquals(20.0, media.durationSeconds)
        assertEquals(7_316_660L, media.sizeBytes)
        assertEquals("h264", media.video?.codec)
        assertEquals(1280, media.video?.width)
        assertEquals(720, media.video?.height)
        assertEquals(30.0, media.video?.fps)
        assertEquals("aac", media.audio?.codec)
        assertEquals(127.8, media.audio?.bitrateKbps)
        assertEquals(1, media.audio?.channels)
        assertEquals(44_100, media.audio?.sampleRate)
    }

    @Test
    fun probeAudioOnly() {
        val flac = parse("ffprobe-flac.json")
        assertNull(flac.video)
        assertEquals("flac", flac.audio?.codec)
        assertNull(flac.audio?.bitrateKbps)
        assertEquals(5.0, flac.durationSeconds)

        val mp3 = parse("ffprobe-mp3.json")
        assertEquals("mp3", mp3.audio?.codec)
        assertEquals(128.0, mp3.audio?.bitrateKbps)
    }

    @Test
    fun coverArtIsNotVideo() {
        val json = """
            {"streams": [
              {"codec_name": "mp3", "codec_type": "audio", "bit_rate": "320000"},
              {"codec_name": "mjpeg", "codec_type": "video", "width": 500, "height": 500,
               "disposition": {"attached_pic": 1}}
            ], "format": {"duration": "60.0", "size": "2400000"}}
        """.trimIndent()
        val media = (FfprobeParser.parse(json, "a.mp3") as Outcome.Success).value
        assertNull(media.video)
        assertEquals("mp3", media.audio?.codec)
    }

    @Test
    fun noStreamsOrGarbageIsUnsupported() {
        assertTrue((FfprobeParser.parse("{}", "x") as Outcome.Failure).error is MediaError.UnsupportedConversion)
        assertTrue((FfprobeParser.parse("not json", "x") as Outcome.Failure).error is MediaError.UnsupportedConversion)
    }

    @Test
    fun frameRates() {
        assertEquals(29.97, FfprobeParser.frameRate("30000/1001")!!, 0.01)
        assertNull(FfprobeParser.frameRate("0/0"))
        assertNull(FfprobeParser.frameRate(null))
    }

    // Comando

    private val runtime = FfmpegRuntime(File("/n/libffmpeg.so"), File("/n/libffprobe.so"), emptyMap())
    private val input = File("/w/job/input")

    private fun command(plan: ConversionPlan, ext: String = plan.extension) =
        FfmpegCommand.convert(runtime, input, File("/w/job/output.$ext"), plan)

    private fun plan(target: ConversionTarget): ConversionPlan =
        (ConversionPlanner.plan(parse("ffprobe-mp4-h264-aac.json"), target) as Outcome.Success).value

    @Test
    fun probeCommand() {
        assertEquals(
            listOf(
                "/n/libffprobe.so",
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                "/w/job/input",
            ),
            FfmpegCommand.probe(runtime, input),
        )
    }

    @Test
    fun mp4ToMp3IsExact() {
        assertEquals(
            listOf(
                "/n/libffmpeg.so", "-nostdin", "-hide_banner", "-nostats", "-loglevel", "error",
                "-progress", "pipe:1", "-y", "-i", "/w/job/input",
                "-vn", "-map", "0:a:0?",
                "-c:a", "libmp3lame", "-b:a", "128k",
                "-map_metadata", "0",
                "/w/job/output.mp3",
            ),
            command(plan(ConversionTarget.Audio(AudioFormat.MP3))),
        )
    }

    @Test
    fun remuxCopiesBothTracks() {
        val args = command(plan(ConversionTarget.Video(VideoContainer.MKV)))
        assertEquals(listOf("-map", "0:v:0", "-map", "0:a:0?", "-c:v", "copy", "-c:a", "copy"), args.subList(11, 19))
        assertFalse("-movflags" in args)
    }

    @Test
    fun clipSeeksBeforeTheInputAndLimitsTheLength() {
        val clip = Clip(83.5, 93.5)
        val args = FfmpegCommand.convert(
            runtime,
            input,
            File("/w/job/output.mkv"),
            plan(ConversionTarget.Video(VideoContainer.MKV)),
            clip,
        )
        val ss = args.indexOf("-ss")
        assertEquals(listOf("-ss", "83.500", "-i", "/w/job/input", "-t", "10.000"), args.subList(ss, ss + 6))
        assertEquals("make_zero", args[args.indexOf("-avoid_negative_ts") + 1])
    }

    @Test
    fun clipFromTheStartOrToTheEnd() {
        val target = plan(ConversionTarget.Audio(AudioFormat.MP3))
        val fromStart = FfmpegCommand.convert(runtime, input, File("/w/job/output.mp3"), target, Clip(0.0, 30.0))
        assertFalse("-ss" in fromStart)
        assertEquals("30.000", fromStart[fromStart.indexOf("-t") + 1])
        val toEnd = FfmpegCommand.convert(runtime, input, File("/w/job/output.mp3"), target, Clip(12.0))
        assertEquals("12.000", toEnd[toEnd.indexOf("-ss") + 1])
        assertFalse("-t" in toEnd)
        assertFalse("-ss" in command(target))
    }

    @Test
    fun frameSeeksAndWritesOneImage() {
        val jpg = command(plan(ConversionTarget.Image(3.5)))
        assertEquals(
            listOf(
                "/n/libffmpeg.so", "-nostdin", "-hide_banner", "-nostats", "-loglevel", "error",
                "-progress", "pipe:1", "-y", "-ss", "3.500", "-i", "/w/job/input",
                "-map", "0:v:0", "-an", "-sn", "-frames:v", "1", "-c:v", "mjpeg", "-q:v", "2",
                "-update", "1", "/w/job/output.jpg",
            ),
            jpg,
        )
        val png = command(plan(ConversionTarget.Image(0.0, ImageFormat.PNG)))
        assertFalse("-ss" in png)
        assertEquals("png", png[png.indexOf("-c:v") + 1])
        assertFalse("-map_metadata" in png)
    }

    @Test
    fun mp4OutputGetsFaststart() {
        val args = command(plan(ConversionTarget.Video(VideoContainer.MP4)))
        assertEquals("+faststart", args[args.indexOf("-movflags") + 1])
    }

    @Test
    fun webmEncodesVp9AndOpus() {
        val args = command(plan(ConversionTarget.Video(VideoContainer.WEBM)))
        assertEquals("libvpx-vp9", args[args.indexOf("-c:v") + 1])
        assertEquals("libopus", args[args.indexOf("-c:a") + 1])
        assertEquals("128k", args[args.indexOf("-b:a") + 1])
    }

    @Test
    fun scalingUsesX264AndEvenWidth() {
        val args = command(plan(ConversionTarget.Video(VideoContainer.MP4, maxHeight = 480)))
        assertEquals("scale=-2:480", args[args.indexOf("-vf") + 1])
        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("copy", args[args.indexOf("-c:a") + 1])
    }

    @Test
    fun losslessHasNoBitrate() {
        val wav = command(ConversionPlan(StreamPlan.Drop, StreamPlan.Encode("pcm_s16le"), null, "wav", "audio/x-wav"))
        assertEquals("pcm_s16le", wav[wav.indexOf("-c:a") + 1])
        assertFalse("-b:a" in wav)
    }

    // Progreso

    @Test
    fun progressFromOutTime() {
        val parser = FfmpegProgressParser(durationSeconds = 20.0)
        val lines = listOf(
            "frame=0",
            "out_time_us=5000000",
            "out_time_ms=5000000",
            "progress=continue",
            "out_time_us=5000000",
            "out_time_us=N/A",
            "out_time_us=10000000",
            "progress=end",
        )
        assertEquals(listOf(0.25f, 0.5f, 1f), lines.mapNotNull { parser.feed(it) })
    }

    @Test
    fun progressWithoutDurationOnlyReportsTheEnd() {
        val parser = FfmpegProgressParser(durationSeconds = null)
        assertEquals(listOf(1f), listOf("out_time_us=5000000", "progress=end").mapNotNull { parser.feed(it) })
    }
}
