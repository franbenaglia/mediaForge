package com.fabapps.mediaforge.media.ytdlp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpCommandTest {
    private val runtime = YtDlpRuntime(
        python = File("/n/libpython.so"),
        ytDlp = File("/d/yt-dlp"),
        ffmpeg = File("/n/libffmpeg.so"),
        quickJs = File("/n/libqjs.so"),
        cacheDir = File("/c/yt-dlp"),
        environment = emptyMap(),
    )

    @Test
    fun infoCommandIsExact() {
        assertEquals(
            listOf(
                "/n/libpython.so", "/d/yt-dlp",
                "--ignore-config", "--no-playlist",
                "--cache-dir", "/c/yt-dlp",
                "--js-runtimes", "quickjs:/n/libqjs.so",
                "--ffmpeg-location", "/n/libffmpeg.so",
                "--dump-json", "--no-progress",
                "--", "https://example.com/v",
            ),
            YtDlpCommand.info(runtime, "https://example.com/v"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun infoRejectsOptionLikeUrl() {
        YtDlpCommand.info(runtime, "-o/sdcard/x")
    }

    @Test
    fun versionCommand() {
        assertEquals(
            listOf("/n/libpython.so", "/d/yt-dlp", "--ignore-config", "--version"),
            YtDlpCommand.version(runtime),
        )
    }
}
