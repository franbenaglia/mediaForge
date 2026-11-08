package com.fabapps.mediaforge.media.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Test

class MimeCodecsTest {
    @Test
    fun videoMimes() {
        assertEquals("h264", MimeCodecs.video("video/avc"))
        assertEquals("vp9", MimeCodecs.video("video/x-vnd.on2.vp9"))
        assertEquals("av1", MimeCodecs.video("video/av01"))
        assertEquals("video/x-unknown", MimeCodecs.video("video/x-unknown"))
    }

    @Test
    fun audioMimes() {
        assertEquals("aac", MimeCodecs.audio("audio/mp4a-latm"))
        assertEquals("mp3", MimeCodecs.audio("audio/mpeg"))
        assertEquals("opus", MimeCodecs.audio("audio/opus"))
        assertEquals("pcm_s16le", MimeCodecs.audio("audio/raw"))
        assertEquals("pcm_s16le", MimeCodecs.audio("audio/raw", 2))
        assertEquals("pcm", MimeCodecs.audio("audio/raw", 4))
    }
}
