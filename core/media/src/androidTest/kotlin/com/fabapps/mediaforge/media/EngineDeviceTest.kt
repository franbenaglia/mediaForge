package com.fabapps.mediaforge.media

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.EngineState
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.update.AndroidVersionProbe
import com.fabapps.mediaforge.media.update.KeyValueStore
import com.fabapps.mediaforge.media.update.UrlConnectionFetcher
import com.fabapps.mediaforge.media.update.YtDlpUpdater
import com.fabapps.mediaforge.media.update.YtDlpVersions
import com.fabapps.mediaforge.media.ytdlp.YtDlpCommand
import com.fabapps.mediaforge.media.ytdlp.YtDlpDownloader
import com.fabapps.mediaforge.media.ytdlp.YtDlpExtractor
import com.fabapps.mediaforge.media.ytdlp.YtDlpInfoParser
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
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
    private val prefs = context.getSharedPreferences("ytdlp_versions", Context.MODE_PRIVATE)
    private val versions = YtDlpVersions(
        File(context.noBackupFilesDir, "mediaforge/yt-dlp"),
        object : KeyValueStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun set(key: String, value: String?) = prefs.edit().putString(key, value).apply()
        },
    )
    private val native = YoutubeDlNativeEngine(context, versions)
    private val engine = EngineInitializer(native, Dispatchers.IO)
    private val runner = ProcessRunner(Dispatchers.IO)
    private val updater = YtDlpUpdater(
        http = UrlConnectionFetcher(),
        versions = versions,
        probe = AndroidVersionProbe(engine, native, runner, native.bundledYtDlp()),
        io = Dispatchers.IO,
    )
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

    /** T3.7: actualización real desde GitHub, con firma y hash verificados. */
    @Test
    fun t1b_updateYtDlp() = runBlocking {
        engine.ensureReady()
        val before = updater.installedVersion()
        val start = System.currentTimeMillis()
        val result = updater.update()
        log("update before=$before result=$result ms=${System.currentTimeMillis() - start}")
        log("update active=${versions.active()} previous=${versions.previous()}")
        assertTrue("update failed: $result", result is Outcome.Success)
        assertEquals((result as Outcome.Success).value, updater.installedVersion())
        assertEquals(versions.active()?.file?.absolutePath, native.runtime().ytDlp.absolutePath)
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

    /** T4.8: descarga real de Dailymotion (HLS) con el camino de la app. Registra la salida sin query strings. */
    @Test
    fun t5_downloadDailymotion() = download("dailymotion", DAILYMOTION, listOf("hls-380"), 240)

    private fun download(name: String, url: String, formatIds: List<String>, height: Int) = runBlocking {
        engine.ensureReady()
        log("$name ytdlp=${updater.installedVersion()}")
        val runtime = native.runtime()
        val work = File(context.cacheDir, "download-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val selection = DownloadSelection.Video(formatIds, maxHeight = height)
        val command = YtDlpCommand.download(runtime, url, selection, work, embedMetadata = true)
        var end: ProcessEvent? = null
        val start = System.currentTimeMillis()
        runner.run(command, runtime.environment, workingDir = work).collect {
            when (it) {
                is ProcessEvent.Stdout -> if (!it.line.startsWith(YtDlpCommand.PROGRESS_PREFIX)) {
                    log("$name out: ${redact(it.line)}")
                }
                is ProcessEvent.Stderr -> log("$name err: ${redact(it.line)}")
                else -> end = it
            }
        }
        log("$name raw end=$end ms=${System.currentTimeMillis() - start} files=${work.list()?.toList()}")

        val downloader =
            YtDlpDownloader(engine, native, runner, File(context.noBackupFilesDir, "mediaforge/jobs"), Dispatchers.IO)
        val request = DownloadRequest("device-$name", url, selection, "MediaForge device test")
        val last = downloader.download(request).toList().last()
        log("$name downloader last=$last")
        work.deleteRecursively()
        (last as? JobState.Completed)?.outputs?.forEach { File(it.location).parentFile?.deleteRecursively() }
        assertTrue("$name: $last", last is JobState.Completed)
    }

    private fun redact(line: String) = line.replace(Regex("\\?[^\\s\"']*"), "?…").take(400)

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
