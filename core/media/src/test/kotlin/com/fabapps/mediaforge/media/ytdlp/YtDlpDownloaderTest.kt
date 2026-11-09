package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobNote
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.domain.model.SubtitleTrack
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.process.ProcessRunner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** yt-dlp falso: un script de sh que imita la salida y los archivos de una descarga con merge. */
class YtDlpDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class ReadyEngine : NativeEngine {
        override fun compressedPackageBytes() = 0L
        override fun isExtracted() = true
        override fun freeBytes() = Long.MAX_VALUE
        override fun initialize() = Unit
    }

    private val root by lazy { tmp.newFolder("jobs") }
    private val argsFile by lazy { File(tmp.root, "args.txt") }
    private var free = Long.MAX_VALUE

    private fun downloader(mode: String): YtDlpDownloader {
        val script = File(tmp.root, "fake-yt-dlp").apply {
            writeText(
                """
                for a in "${'$'}@"; do printf '%s\n' "${'$'}a"; done > "${argsFile.absolutePath}"
                case "${'$'}MF_MODE" in
                  ok)
                    echo "[info] x: Downloading 1 format(s): 299+140"
                    echo "[download] Destination: ${'$'}PWD/media.f299.mp4"
                    echo "MFPROG downloading 500 1000 NA 100"
                    printf 'v%.0s' 1 2 3 > media.f299.mp4
                    echo "MFPROG finished 1000 1000 NA NA"
                    echo "[download] Destination: ${'$'}PWD/media.f140.m4a"
                    echo "MFPROG finished 200 200 NA NA"
                    echo "[Merger] Merging formats into \"${'$'}PWD/media.mp4\""
                    printf 'merged' > media.mp4
                    rm media.f299.mp4 ;;
                  forbidden)
                    echo "ERROR: [youtube] x: Unable to download video data: HTTP Error 403: Forbidden" >&2; exit 1 ;;
                  slow)
                    echo "[download] Destination: ${'$'}PWD/media.f299.mp4"
                    printf 'partial' > media.f299.mp4.part
                    echo "MFPROG downloading 10 1000 NA NA"
                    sleep 30 ;;
                  nofile) echo "[info] nothing" ;;
                  subfail)
                    if grep -q -- '--embed-subs' "${argsFile.absolutePath}"; then
                      echo "ERROR: Unable to download video subtitles for 'es': HTTP Error 429: Too Many Requests" >&2
                      exit 1
                    fi
                    printf 'merged' > media.mp4 ;;
                  audio)
                    echo "[info] Writing video thumbnail 41 to: ${'$'}PWD/media.webp"
                    printf 'a-much-bigger-thumbnail-than-the-audio' > media.webp
                    echo "[download] Destination: ${'$'}PWD/media.webm"
                    printf 'opus' > media.webm
                    echo "MFPROG finished 4 4 NA NA"
                    echo "[ExtractAudio] Destination: ${'$'}PWD/media.mp3"
                    printf 'mp3' > media.mp3
                    rm media.webm
                    echo "[EmbedThumbnail] ffmpeg: Adding thumbnail to \"${'$'}PWD/media.mp3\"" ;;
                esac
                """.trimIndent(),
            )
        }
        val runtime = YtDlpRuntime(
            python = File("/bin/sh"),
            ytDlp = script,
            ffmpeg = File("/opt/ffmpeg"),
            quickJs = File("/opt/qjs"),
            cacheDir = File(tmp.root, "cache").apply { mkdirs() },
            environment = mapOf("MF_MODE" to mode),
        )
        return YtDlpDownloader(
            engine = EngineInitializer(ReadyEngine(), Dispatchers.IO),
            runtimeProvider = { runtime },
            runner = ProcessRunner(Dispatchers.IO, shell = "/bin/sh"),
            workRoot = root,
            io = Dispatchers.IO,
            freeBytes = { free },
        )
    }

    private fun request(
        url: String = "https://www.youtube.com/watch?v=x",
        jobId: String = "job1",
        expected: Long? = null,
    ) = DownloadRequest(
        jobId = jobId,
        sourceUrl = url,
        selection = DownloadSelection.Video(listOf("299", "140"), maxHeight = 1080),
        fileName = "Big Buck Bunny: 4K / 60fps",
        expectedBytes = expected,
    )

    private fun run(mode: String, request: DownloadRequest = request()) = runBlocking {
        withTimeout(10_000) { downloader(mode).download(request).toList() }
    }

    @Test
    fun successfulDownloadReportsProgressMergeAndOutput() {
        val states = run("ok")
        assertEquals(JobState.Pending, states.first())
        val progress = states.filterIsInstance<JobState.Downloading>().mapNotNull { it.progress }
        assertEquals(listOf(0.25f, 0.5f, 1f), progress)
        assertTrue(JobState.Processing(ProcessingStep.MERGE, null) in states)

        val output = (states.last() as JobState.Completed).outputs.single()
        assertEquals(File(root, "job1/media.mp4").absolutePath, output.location)
        assertEquals("Big Buck Bunny 4K 60fps.mp4", output.displayName)
        assertEquals("video/mp4", output.mimeType)
        assertEquals(6L, output.sizeBytes)
        // Queda solo el archivo final; lo borra quien lo guarde.
        assertEquals(listOf("media.mp4"), File(root, "job1").list()!!.toList())
    }

    @Test
    fun commandUsesSelectionAndUrlAfterDoubleDash() {
        run("ok")
        val args = argsFile.readLines()
        assertEquals("299+140/bv*[height<=1080]+ba/b[height<=1080]", args[args.indexOf("-f") + 1])
        assertEquals(listOf("--", "https://www.youtube.com/watch?v=x"), args.takeLast(2))
    }

    @Test
    fun engineErrorIsClassifiedAndWorkDirRemoved() {
        val states = run("forbidden")
        assertTrue((states.last() as JobState.Failed).error is MediaError.AccessDenied)
        assertFalse(File(root, "job1").exists())
    }

    @Test
    fun cancellingKillsTheProcessAndLeavesNoPartialFiles() = runBlocking {
        val downloader = downloader("slow")
        val partial = File(root, "job1/media.f299.mp4.part")
        withTimeout(10_000) {
            downloader.download(request())
                .onEach { if (it is JobState.Downloading) assertTrue(partial.exists() || it.progress == null) }
                .first { it is JobState.Downloading && it.progress != null }
        }
        assertFalse(File(root, "job1").exists())
    }

    @Test
    fun missingOutputIsAFailure() {
        val states = run("nofile")
        assertTrue((states.last() as JobState.Failed).error is MediaError.Unknown)
        assertFalse(File(root, "job1").exists())
    }

    @Test
    fun notEnoughSpaceFailsBeforeStarting() {
        free = 150L * 1000 * 1000
        val states = run("ok", request(expected = 100L * 1000 * 1000))
        val error = (states.last() as JobState.Failed).error as MediaError.InsufficientStorage
        assertEquals(300L * 1000 * 1000, error.requiredBytes)
        assertFalse(argsFile.exists())
    }

    @Test
    fun invalidUrlAndJobIdNeverRunTheEngine() {
        assertTrue((run("ok", request(url = "--exec=rm")).last() as JobState.Failed).error is MediaError.InvalidUrl)
        assertTrue(run("ok", request(jobId = "../evil")).last() is JobState.Failed)
        assertFalse(argsFile.exists())
        assertFalse(File(tmp.root, "evil").exists())
    }

    @Test
    fun audioOutputIsTheConvertedFileNotTheCover() {
        val audio = request().copy(selection = DownloadSelection.Audio(AudioFormat.MP3, 320, listOf("251")))
        val states = run("audio", audio)
        assertTrue(JobState.Processing(ProcessingStep.TRANSCODE, null) in states)
        assertTrue(JobState.Processing(ProcessingStep.METADATA, null) in states)
        val output = (states.last() as JobState.Completed).outputs.single()
        assertEquals(File(root, "job1/media.mp3").absolutePath, output.location)
        assertEquals("Big Buck Bunny 4K 60fps.mp3", output.displayName)
        assertEquals("audio/mpeg", output.mimeType)
        val args = argsFile.readLines()
        assertEquals("320K", args[args.indexOf("--audio-quality") + 1])
        assertTrue("--embed-thumbnail" in args)
    }

    private fun withSubtitle(request: DownloadRequest) =
        request.copy(selection = DownloadSelection.Video(subtitle = SubtitleTrack("es")))

    @Test
    fun subtitleFailureRetriesWithoutSubtitlesAndNotes() {
        val states = run("subfail", withSubtitle(request()))
        val done = states.last() as JobState.Completed
        assertEquals(setOf(JobNote.SUBTITLES_SKIPPED), done.notes)
        assertEquals("merged", File(done.outputs.single().location).readText())
        assertFalse("--embed-subs" in argsFile.readLines())
    }

    @Test
    fun otherErrorsWithSubtitlesAreNotRetried() {
        val states = run("forbidden", withSubtitle(request()))
        assertTrue(states.last() is JobState.Failed)
        assertTrue("--embed-subs" in argsFile.readLines())
    }

    @Test
    fun downloadWithoutProblemsHasNoNotes() {
        assertEquals(emptySet<JobNote>(), (run("ok").last() as JobState.Completed).notes)
    }
}
