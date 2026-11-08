package com.fabapps.mediaforge.media.ffmpeg

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.process.ProcessRunner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ffprobe y ffmpeg falsos (scripts de sh): devuelven un fixture real y simulan la conversión. */
class FfmpegConverterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class ReadyEngine : NativeEngine {
        override fun compressedPackageBytes() = 0L
        override fun isExtracted() = true
        override fun freeBytes() = Long.MAX_VALUE
        override fun initialize() = Unit
    }

    /** "URIs" que son rutas de archivos comunes. */
    private inner class FileInputs : InputSource {
        override suspend fun describe(input: String) = File(input).takeIf { it.isFile }?.let {
            InputSource.Info(it.name, it.length())
        }
        override suspend fun copyTo(input: String, target: File) {
            File(input).copyTo(target, overwrite = true)
        }
    }

    private val root by lazy { tmp.newFolder("jobs") }
    private val ffmpegArgs by lazy { File(tmp.root, "ffmpeg-args.txt") }
    private var free = Long.MAX_VALUE

    private fun converter(mode: String, probe: String = "ffprobe-mp4-h264-aac.json"): FfmpegConverter {
        val fixture = File(tmp.root, "probe.json").apply {
            writeText(checkNotNull(FfmpegConverterTest::class.java.getResource("/ffmpeg/$probe")).readText())
        }
        val ffprobe = File(tmp.root, "ffprobe").apply {
            writeText(
                """
                case "${'$'}MF_MODE" in
                  badinput) echo "input: Invalid data found when processing input" >&2; exit 1 ;;
                  *) cat "${fixture.absolutePath}" ;;
                esac
                """.trimIndent(),
            )
        }
        val ffmpeg = File(tmp.root, "ffmpeg").apply {
            writeText(
                """
                for a in "${'$'}@"; do printf '%s\n' "${'$'}a"; done > "${ffmpegArgs.absolutePath}"
                for last in "${'$'}@"; do :; done
                case "${'$'}MF_MODE" in
                  ok)
                    echo "out_time_us=5000000"; echo "progress=continue"
                    echo "out_time_us=20000000"; printf 'converted' > "${'$'}last"; echo "progress=end" ;;
                  fail) echo "Error while opening encoder" >&2; exit 1 ;;
                  nospace) echo "Error writing trailer: No space left on device" >&2; exit 1 ;;
                  slow) echo "out_time_us=1000000"; printf 'partial' > "${'$'}last"; sleep 30 ;;
                esac
                """.trimIndent(),
            )
        }
        // Sin shebang: el `exec` del runner cae en sh, igual que con los otros binarios falsos.
        ffmpeg.setExecutable(true)
        ffprobe.setExecutable(true)
        val runtime = FfmpegRuntime(ffmpeg, ffprobe, mapOf("MF_MODE" to mode))
        return FfmpegConverter(
            engine = EngineInitializer(ReadyEngine(), Dispatchers.IO),
            runtimeProvider = { runtime },
            runner = ProcessRunner(Dispatchers.IO, shell = "/bin/sh"),
            inputs = FileInputs(),
            workRoot = root,
            io = Dispatchers.IO,
            freeBytes = { free },
        )
    }

    private val inputFile by lazy { File(tmp.root, "Video original.mp4").apply { writeBytes(ByteArray(7_316)) } }

    private fun request(target: ConversionTarget = ConversionTarget.Audio(AudioFormat.MP3), jobId: String = "conv1") =
        ConversionRequest(jobId = jobId, input = inputFile.absolutePath, target = target, fileName = "Mi audio: final")

    private fun run(mode: String, request: ConversionRequest = request(), probe: String = "ffprobe-mp4-h264-aac.json") =
        runBlocking { withTimeout(10_000) { converter(mode, probe).convert(request).toList() } }

    @Test
    fun mp4ToMp3ReportsStepsProgressAndOutput() {
        val states = run("ok")
        assertEquals(JobState.Pending, states.first())
        assertTrue(JobState.Processing(ProcessingStep.PREPARE_INPUT, null) in states)
        val progress = states.filterIsInstance<JobState.Processing>().filter { it.step == ProcessingStep.TRANSCODE }
        assertEquals(listOf(0f, 0.25f, 1f), progress.map { it.progress })

        val output = (states.last() as JobState.Completed).outputs.single()
        assertEquals(File(root, "conv1/output.mp3").absolutePath, output.location)
        assertEquals("Mi audio final.mp3", output.displayName)
        assertEquals("audio/mpeg", output.mimeType)
        // La copia de la entrada se borra; queda solo la salida.
        assertEquals(listOf("output.mp3"), File(root, "conv1").list()!!.toList())
        val args = ffmpegArgs.readLines()
        assertEquals(File(root, "conv1/input").absolutePath, args[args.indexOf("-i") + 1])
        assertEquals("libmp3lame", args[args.indexOf("-c:a") + 1])
    }

    @Test
    fun remuxIsReportedAsRemux() {
        val states = run("ok", request(ConversionTarget.Video(VideoContainer.MKV)))
        assertTrue(states.any { it is JobState.Processing && it.step == ProcessingStep.REMUX })
        assertEquals("video/x-matroska", (states.last() as JobState.Completed).outputs.single().mimeType)
    }

    @Test
    fun audioOnlyInputCannotBecomeVideo() {
        val states = run("ok", request(ConversionTarget.Video(VideoContainer.MP4)), probe = "ffprobe-flac.json")
        assertTrue((states.last() as JobState.Failed).error is MediaError.UnsupportedConversion)
        assertFalse(File(root, "conv1").exists())
        assertFalse(ffmpegArgs.exists())
    }

    @Test
    fun unreadableInputIsUnsupported() {
        val states = run("badinput")
        val error = (states.last() as JobState.Failed).error
        assertTrue(error is MediaError.UnsupportedConversion)
        assertTrue(error.detail!!.contains("Invalid data"))
    }

    @Test
    fun ffmpegErrorsAreReportedAndCleaned() {
        assertTrue((run("fail").last() as JobState.Failed).error is MediaError.Unknown)
        assertTrue((run("nospace").last() as JobState.Failed).error is MediaError.InsufficientStorage)
        assertFalse(File(root, "conv1").exists())
    }

    @Test
    fun cancellingLeavesNoFiles() = runBlocking {
        withTimeout(10_000) {
            converter("slow").convert(request()).first {
                it is JobState.Processing &&
                    it.progress != null &&
                    it.progress!! > 0f
            }
        }
        assertFalse(File(root, "conv1").exists())
    }

    @Test
    fun notEnoughSpaceFailsBeforeCopying() {
        free = 1_000
        val states = run("ok")
        assertTrue((states.last() as JobState.Failed).error is MediaError.InsufficientStorage)
        assertFalse(File(root, "conv1").exists())
    }

    @Test
    fun missingInputOrBadJobId() {
        val missing = request().copy(input = File(tmp.root, "nope").absolutePath)
        assertTrue((run("ok", missing).last() as JobState.Failed).error is MediaError.UnsupportedConversion)
        assertTrue(run("ok", request(jobId = "../x")).last() is JobState.Failed)
    }
}
