package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.NativeEngine
import com.fabapps.mediaforge.media.process.ProcessRunner
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El "python" es /bin/sh y el "yt-dlp" un script que responde según MF_MODE. Así se prueba el
 * extractor completo (validación, comando, proceso, parseo, errores) sin el motor real.
 */
class YtDlpExtractorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class ReadyEngine : NativeEngine {
        var initCalls = 0
        override fun compressedPackageBytes() = 0L
        override fun isExtracted() = true
        override fun freeBytes() = Long.MAX_VALUE
        override fun initialize() {
            initCalls++
        }
    }

    private val native = ReadyEngine()
    private val argsFile by lazy { File(tmp.root, "args.txt") }

    private fun extractor(mode: String): YtDlpExtractor {
        val json = checkNotNull(YtDlpExtractorTest::class.java.getResource("/ytdlp/youtube.json")).readText()
        val fixture = File(tmp.root, "youtube.json").apply { writeText(json) }
        val script = File(tmp.root, "fake-yt-dlp").apply {
            writeText(
                """
                for a in "${'$'}@"; do printf '%s\n' "${'$'}a"; done > "${argsFile.absolutePath}"
                case "${'$'}MF_MODE" in
                  ok) tr -d '\n' < "${fixture.absolutePath}"; echo ;;
                  login) echo "ERROR: [vimeo] 1: The web client only works when logged-in. Use --cookies" >&2; exit 1 ;;
                  slow) sleep 30 ;;
                  garbage) echo "not json" ;;
                esac
                """.trimIndent(),
            )
        }
        val runtime = YtDlpRuntime(
            python = File("/bin/sh"),
            ytDlp = script,
            ffmpeg = File("/opt/ffmpeg"),
            quickJs = File("/opt/qjs"),
            cacheDir = tmp.newFolder("cache"),
            environment = mapOf("MF_MODE" to mode),
        )
        return YtDlpExtractor(
            engine = EngineInitializer(native, Dispatchers.IO),
            runtimeProvider = { runtime },
            runner = ProcessRunner(Dispatchers.IO, shell = "/bin/sh"),
        )
    }

    @Test
    fun parsesSuccessfulOutput() = runBlocking {
        val result = extractor("ok").extract("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        val info = (result as Outcome.Success).value
        assertEquals("aqz-KE-bpKQ", info.id)
        assertEquals(6, info.formats.size)
        assertEquals(1, native.initCalls)
    }

    @Test
    fun passesNormalizedUrlLastAfterDoubleDash() = runBlocking {
        extractor("ok").extract("WWW.YouTube.com/watch?v=x")
        val args = argsFile.readLines()
        assertEquals(listOf("--", "https://www.youtube.com/watch?v=x"), args.takeLast(2))
        assertTrue("--ignore-config" in args)
        assertTrue("--no-playlist" in args)
        assertTrue("--dump-json" in args)
        assertEquals("quickjs:/opt/qjs", args[args.indexOf("--js-runtimes") + 1])
        assertEquals("/opt/ffmpeg", args[args.indexOf("--ffmpeg-location") + 1])
    }

    @Test
    fun invalidUrlNeverStartsTheEngine() = runBlocking {
        val result = extractor("ok").extract("--exec=rm -rf /")
        assertTrue((result as Outcome.Failure).error is MediaError.InvalidUrl)
        assertEquals(0, native.initCalls)
        assertTrue(!argsFile.exists())
    }

    @Test
    fun engineErrorIsClassified() = runBlocking {
        val result = extractor("login").extract("https://vimeo.com/1")
        assertTrue((result as Outcome.Failure).error is MediaError.LoginRequired)
    }

    @Test
    fun timeoutBecomesNetworkError() = runBlocking {
        val extractor = extractor("slow").apply { timeout = 300.milliseconds }
        val result = extractor.extract("https://example.com/v")
        assertTrue((result as Outcome.Failure).error is MediaError.Network)
    }

    @Test
    fun garbageOutputIsAFailure() = runBlocking {
        val result = extractor("garbage").extract("https://example.com/v")
        assertTrue((result as Outcome.Failure).error is MediaError.Unknown)
    }
}
