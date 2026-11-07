package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.VideoContainer
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

    private fun download(
        selection: DownloadSelection.Video,
        url: String = "https://example.com/v",
        metadata: Boolean = true,
    ) = YtDlpCommand.download(runtime, url, selection, File("/w/job1"), metadata)

    @Test
    fun downloadCommandIsExact() {
        assertEquals(
            listOf(
                "/n/libpython.so", "/d/yt-dlp",
                "--ignore-config", "--no-playlist",
                "--cache-dir", "/c/yt-dlp",
                "--js-runtimes", "quickjs:/n/libqjs.so",
                "--ffmpeg-location", "/n/libffmpeg.so",
                "-f", "299+258/bv*[height<=1080]+ba/b[height<=1080]",
                "--merge-output-format", "mp4",
                "-o", "/w/job1/media.%(ext)s",
                "--no-mtime", "--newline",
                "--progress-template",
                "download:MFPROG %(progress.status)s %(progress.downloaded_bytes)s " +
                    "%(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s",
                "--embed-metadata",
                "--", "https://example.com/v",
            ),
            download(DownloadSelection.Video(listOf("299", "258"), maxHeight = 1080)),
        )
    }

    @Test
    fun selectorForBestAndMaxHeight() {
        assertEquals("bv*+ba/b", YtDlpCommand.formatSelector(DownloadSelection.Video()))
        assertEquals(
            "bv*[height<=720]+ba/b[height<=720]",
            YtDlpCommand.formatSelector(DownloadSelection.Video(maxHeight = 720)),
        )
        assertEquals("hls-380/bv*+ba/b", YtDlpCommand.formatSelector(DownloadSelection.Video(listOf("hls-380"))))
    }

    @Test
    fun containerAndMetadataOptions() {
        val args = download(DownloadSelection.Video(container = VideoContainer.MKV), metadata = false)
        assertEquals("mkv", args[args.indexOf("--merge-output-format") + 1])
        assertEquals(false, "--embed-metadata" in args)
    }

    @Test(expected = IllegalArgumentException::class)
    fun downloadRejectsOptionLikeUrl() {
        download(DownloadSelection.Video(), url = "--exec=rm")
    }

    @Test(expected = IllegalArgumentException::class)
    fun formatIdCannotInjectSelectorSyntax() {
        download(DownloadSelection.Video(listOf("299/bestaudio")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun formatIdCannotLookLikeAnOption() {
        download(DownloadSelection.Video(listOf("-x")))
    }
}
