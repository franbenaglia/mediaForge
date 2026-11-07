package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.download.AudioOptions
import com.fabapps.mediaforge.domain.download.VideoOptions
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opciones de calidad con la salida real de yt-dlp 2026.10.26 en el teléfono. */
class VideoOptionsRealTest {
    private fun parse(name: String): MediaInfo {
        val json = checkNotNull(javaClass.getResource("/ytdlp/$name")).readText()
        return (YtDlpInfoParser.parse(json) as Outcome.Success).value
    }

    @Test
    fun youtube() {
        val options = VideoOptions.from(parse("youtube-real.json"))
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360, 240, 144), options.map { it.height })

        val p1080 = options.single { it.height == 1080 }
        // H.264 a 60 fps + el mejor AAC sin DRC.
        assertEquals(listOf("299", "258"), p1080.selection.formatIds)
        assertEquals(257_619_653L + 30_767_611L, p1080.estimatedBytes)
        assertFalse(p1080.sizeIsApproximate)

        // En 1440p no hay H.264: VP9 antes que AV1.
        assertEquals("308", options.single { it.height == 1440 }.selection.formatIds.first())
        // En 360p hay un combinado (18), pero separado da más calidad; debajo de 720p, audio liviano.
        assertEquals(listOf("134", "140"), options.single { it.height == 360 }.selection.formatIds)
        assertEquals(listOf("160", "140"), options.single { it.height == 144 }.selection.formatIds)
        assertTrue(options.all { it.hasAudio })
    }

    @Test
    fun dailymotion() {
        val option = VideoOptions.from(parse("dailymotion-real.json")).single()
        assertEquals(240, option.height)
        assertEquals(listOf("hls-380"), option.selection.formatIds)
        assertTrue(option.sizeIsApproximate)
        assertTrue(option.hasAudio)
    }

    @Test
    fun youtubeAudio() {
        val options = AudioOptions.from(parse("youtube-real.json")).associateBy { it.format }
        // M4A copia el mejor AAC sin DRC; Opus copia 251; MP3 recodifica desde Opus.
        assertEquals("258", options.getValue(AudioFormat.M4A).sourceFormatId)
        assertTrue(options.getValue(AudioFormat.M4A).copiesWithoutReencoding)
        assertEquals("251", options.getValue(AudioFormat.OPUS).sourceFormatId)
        assertEquals("251", options.getValue(AudioFormat.MP3).sourceFormatId)
        assertEquals(128, options.getValue(AudioFormat.MP3).autoKbps)
    }

    @Test
    fun dailymotionAudio() {
        val options = AudioOptions.from(parse("dailymotion-real.json")).associateBy { it.format }
        assertEquals("hls-380", options.getValue(AudioFormat.MP3).sourceFormatId)
        assertTrue(options.getValue(AudioFormat.M4A).copiesWithoutReencoding)
    }
}
