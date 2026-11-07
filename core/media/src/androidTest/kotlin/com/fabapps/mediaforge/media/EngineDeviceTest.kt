package com.fabapps.mediaforge.media

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.EngineState
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.ytdlp.YtDlpCommand
import com.fabapps.mediaforge.media.ytdlp.YtDlpExtractor
import com.fabapps.mediaforge.media.ytdlp.YtDlpInfoParser
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Validación del motor en un teléfono real (T3.3, T3.5, T3.6). Solo se corre a mano, con permiso
 * del usuario: `scripts/test-device.sh`. Registra mediciones con la etiqueta MFDEVICE y guarda las
 * salidas de yt-dlp (recortadas y sin URLs firmadas) en `files/fixtures` para usarlas como fixtures.
 *
 * Contenido de prueba: Big Buck Bunny (Blender Foundation, CC BY).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EngineDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val native = YoutubeDlNativeEngine(context)
    private val engine = EngineInitializer(native, Dispatchers.IO)
    private val runner = ProcessRunner(Dispatchers.IO)
    private val fixtures = File(context.filesDir, "fixtures").apply { mkdirs() }

    private fun log(msg: String) = Log.i(TAG, msg)

    @Test
    fun t1_firstInitialization() = runBlocking {
        log(
            "compressed_bytes=${native.compressedPackageBytes()} extracted_before=${native.isExtracted()} free=${native.freeBytes()}",
        )
        val start = System.currentTimeMillis()
        val result = engine.ensureReady()
        log("init result=$result ms=${System.currentTimeMillis() - start}")
        assertEquals(Outcome.Success(Unit), result)
        assertEquals(EngineState.Ready, engine.state.value)
        assertTrue(native.isExtracted())

        val events = mutableListOf<ProcessEvent>()
        runner.run(YtDlpCommand.version(native.runtime()), native.runtime().environment).collect { events += it }
        log("yt-dlp version events=$events")
        assertEquals(ProcessEvent.Exited(0), events.last())
    }

    @Test
    fun t2_youtube() = probe("youtube", YOUTUBE)

    @Test
    fun t3_dailymotion() = probe("dailymotion", DAILYMOTION)

    private fun probe(name: String, url: String) = runBlocking {
        engine.ensureReady()
        val runtime = native.runtime()

        // Salida cruda, para el fixture.
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val start = System.currentTimeMillis()
        var end: ProcessEvent? = null
        runner.run(YtDlpCommand.info(runtime, url), runtime.environment, timeout = 180.seconds).collect {
            when (it) {
                is ProcessEvent.Stdout -> stdout.appendLine(it.line)
                is ProcessEvent.Stderr -> stderr.appendLine(it.line)
                else -> end = it
            }
        }
        log("$name end=$end ms=${System.currentTimeMillis() - start}")
        stderr.lines().filter { it.isNotBlank() }.take(12).forEach { log("$name stderr: ${it.take(300)}") }
        if (stdout.isNotBlank()) {
            File(fixtures, "$name.json").writeText(trimForFixture(JSONObject(stdout.toString())).toString(2))
        }

        // Camino completo de la app.
        val viaExtractor = YtDlpExtractor(engine, native, runner).extract(url)
        when (viaExtractor) {
            is Outcome.Success -> viaExtractor.value.let { info ->
                log(
                    "$name extractor OK title='${info.title}' site=${info.site.displayName} duration=${info.durationSeconds} formats=${info.formats.size}",
                )
                log("$name heights=${info.formats.mapNotNull { it.height }.distinct().sorted()}")
            }
            is Outcome.Failure -> log("$name extractor FAIL ${viaExtractor.error}")
        }
        assertTrue("$name: ${(viaExtractor as? Outcome.Failure)?.error}", viaExtractor is Outcome.Success)
        assertTrue(YtDlpInfoParser.parse(stdout.toString()) is Outcome.Success)
    }

    /** Cancelar una descarga HLS que usa ffmpeg como descargador no debe dejar procesos vivos. */
    @Test
    fun t4_cancelKillsFfmpegChild() = runBlocking {
        engine.ensureReady()
        val runtime = native.runtime()
        val out = File(context.cacheDir, "cancel-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val command = listOf(
            runtime.python.absolutePath, runtime.ytDlp.absolutePath,
            "--ignore-config", "--no-playlist", "--cache-dir", runtime.cacheDir.absolutePath,
            "--js-runtimes", "quickjs:${runtime.quickJs.absolutePath}",
            "--ffmpeg-location", runtime.ffmpeg.absolutePath,
            "-f", "worst", "--downloader", "ffmpeg",
            "-o", "${out.absolutePath}/%(id)s.%(ext)s",
            "--", DAILYMOTION,
        )
        var sawFfmpeg = false
        val job = launch {
            runner.run(command, runtime.environment).collect {
                if (it is ProcessEvent.Stderr) log("cancel stderr: ${it.line.take(200)}")
            }
        }
        withTimeout(90.seconds) {
            while (!sawFfmpeg && job.isActive) {
                delay(500)
                sawFfmpeg = running("libffmpeg").isNotEmpty()
            }
        }
        log("cancel saw_ffmpeg=$sawFfmpeg before=${(running("libffmpeg") + running("libpython")).map { it.take(120) }}")
        job.cancel()
        job.join()
        delay(1_500)
        val leftovers = running("libffmpeg") + running("libpython")
        log("cancel leftovers=$leftovers")
        assertTrue("ffmpeg never started", sawFfmpeg)
        assertTrue("leftover processes: $leftovers", leftovers.isEmpty())
    }

    /** Procesos de esta app, como "PID NOMBRE ARGS". El nombre es el binario (no los argumentos). */
    private fun ownProcesses(): List<String> = ProcessBuilder("ps", "-A", "-o", "PID,NAME,ARGS")
        .redirectErrorStream(true).start().inputStream.bufferedReader().readLines()
        .drop(1)
        .filter { "ps -A" !in it }

    private fun processName(line: String): String = line.trim().split(Regex("\\s+")).getOrElse(1) { "" }

    private fun running(binary: String): List<String> = ownProcesses().filter { processName(it).startsWith(binary) }

    /** Solo los campos que usa el parser; URLs de formatos reemplazadas (pueden traer tokens). */
    private fun trimForFixture(full: JSONObject): JSONObject {
        val keep = listOf(
            "_type", "id", "title", "fulltitle", "webpage_url", "original_url", "extractor", "extractor_key",
            "uploader", "channel", "creator", "duration", "thumbnail", "upload_date", "description",
        )
        val formatKeys = listOf(
            "format_id", "format_note", "ext", "protocol", "vcodec", "acodec", "width", "height", "fps",
            "tbr", "abr", "vbr", "filesize", "filesize_approx",
        )
        val out = JSONObject()
        keep.forEach { if (full.has(it)) out.put(it, full.get(it)) }
        out.optString("description").takeIf { it.isNotEmpty() }?.let { out.put("description", it.take(300)) }
        val subs = JSONObject()
        full.optJSONObject("subtitles")?.keys()?.forEach { subs.put(it, JSONArray()) }
        out.put("subtitles", subs)
        val formats = JSONArray()
        val source = full.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until source.length()) {
            val f = source.getJSONObject(i)
            val trimmed = JSONObject()
            formatKeys.forEach { if (f.has(it)) trimmed.put(it, f.get(it)) }
            trimmed.put("url", "https://example.invalid/${f.optString("format_id")}")
            formats.put(trimmed)
        }
        out.put("formats", formats)
        return out
    }

    private companion object {
        const val TAG = "MFDEVICE"
        const val YOUTUBE = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        const val DAILYMOTION = "https://www.dailymotion.com/video/x2kdx9f"
    }
}
