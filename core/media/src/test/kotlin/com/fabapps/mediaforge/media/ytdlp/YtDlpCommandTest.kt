package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.SubtitleTrack
import com.fabapps.mediaforge.domain.model.VideoContainer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                "--dump-single-json", "--flat-playlist", "--playlist-items", "1-200", "--no-progress",
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
        selection: DownloadSelection,
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

    @Test
    fun audioTranscodeCommand() {
        val args = download(DownloadSelection.Audio(AudioFormat.MP3, bitrateKbps = 320, formatIds = listOf("251")))
        val from = args.indexOf("-f")
        assertEquals(
            listOf("-f", "251/bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "320K"),
            args.subList(from, from + 7),
        )
        assertEquals(listOf("--embed-metadata", "--embed-thumbnail", "--", "https://example.com/v"), args.takeLast(4))
        assertEquals(false, "--merge-output-format" in args)
    }

    @Test
    fun audioCopyHasNoQualityAndWavHasNoCover() {
        val m4a = download(DownloadSelection.Audio(AudioFormat.M4A, formatIds = listOf("140")))
        assertEquals(false, "--audio-quality" in m4a)
        assertEquals("m4a", m4a[m4a.indexOf("--audio-format") + 1])

        val wav = download(DownloadSelection.Audio(AudioFormat.WAV))
        assertEquals("bestaudio/best", wav[wav.indexOf("-f") + 1])
        assertEquals(false, "--embed-thumbnail" in wav)
        assertEquals(true, "--embed-metadata" in wav)
    }

    @Test
    fun oggIsVorbis() {
        assertEquals("vorbis", YtDlpCommand.ytDlpAudioFormat(AudioFormat.OGG))
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioFormatIdIsValidated() {
        download(DownloadSelection.Audio(AudioFormat.MP3, formatIds = listOf("251/worst")))
    }

    @Test
    fun manualSubtitleIsEmbeddedBeforeTheUrl() {
        val command = download(DownloadSelection.Video(subtitle = SubtitleTrack("es", "Spanish")))
        val start = command.indexOf("--write-subs")
        assertEquals(
            listOf("--write-subs", "--sub-langs", "es", "--sub-format", "vtt/srt/best", "--embed-subs"),
            command.subList(start, start + 6),
        )
        assertEquals(listOf("--", "https://example.com/v"), command.takeLast(2))
        assertFalse("--write-auto-subs" in command)
    }

    @Test
    fun automaticSubtitleUsesWriteAutoSubs() {
        val command = download(DownloadSelection.Video(subtitle = SubtitleTrack("en-orig", automatic = true)))
        assertTrue("--write-auto-subs" in command)
        assertFalse("--write-subs" in command)
        assertEquals("en-orig", command[command.indexOf("--sub-langs") + 1])
    }

    @Test
    fun noSubtitleOptionsByDefault() {
        assertTrue(download(DownloadSelection.Video()).none { it.contains("sub") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun subtitleLanguageCannotBeARegexOrOption() {
        SubtitleTrack("-all")
    }

    @Test(expected = IllegalArgumentException::class)
    fun movCannotCarrySubtitles() {
        DownloadSelection.Video(container = VideoContainer.MOV, subtitle = SubtitleTrack("es"))
    }
}
