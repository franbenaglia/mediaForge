package com.fabapps.mediaforge.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.engine.YoutubeDlNativeEngine
import com.fabapps.mediaforge.media.ffmpeg.ContentInputSource
import com.fabapps.mediaforge.media.ffmpeg.ExtractorInspector
import com.fabapps.mediaforge.media.ffmpeg.FfmpegCommand
import com.fabapps.mediaforge.media.ffmpeg.FfmpegConverter
import com.fabapps.mediaforge.media.ffmpeg.FfprobeParser
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.storage.MediaStoreLibrary
import com.fabapps.mediaforge.media.update.KeyValueStore
import com.fabapps.mediaforge.media.update.YtDlpVersions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Conversor completo en el teléfono (T6.3, T6.4, T6.6): archivos generados con FFmpeg (`lavfi`, sin
 * material de terceros), guardados en MediaStore para tener URIs `content://` reales.
 * Mediciones con la etiqueta MFDEVICE. Borra lo que crea.
 */
@RunWith(AndroidJUnit4::class)
class ConverterDeviceTest {
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
    private val library = MediaStoreLibrary(context, Dispatchers.IO)
    private val inputs = ContentInputSource(context)
    private val inspector = ExtractorInspector(context, inputs, Dispatchers.IO)
    private val jobs = File(context.noBackupFilesDir, "mediaforge/jobs")
    private val converter = FfmpegConverter(engine, native, runner, inputs, jobs, Dispatchers.IO)
    private val created = mutableListOf<Uri>()
    private val work = File(context.cacheDir, "converter-test").apply {
        deleteRecursively()
        mkdirs()
    }

    private fun log(msg: String) = Log.i("MFDEVICE", msg)

    @After
    fun cleanUp() {
        created.forEach { context.contentResolver.delete(it, null, null) }
        work.deleteRecursively()
    }

    private suspend fun ffmpeg(vararg args: String) {
        val runtime = native.ffmpegRuntime()
        val err = StringBuilder()
        var code: Int? = null
        val command = listOf(runtime.ffmpeg.absolutePath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y") + args
        runner.run(command, runtime.environment).collect {
            when (it) {
                is ProcessEvent.Stderr -> err.appendLine(it.line)
                is ProcessEvent.Exited -> code = it.code
                else -> Unit
            }
        }
        assertEquals("ffmpeg ${args.toList()}: $err", 0, code)
    }

    /** Genera un archivo y lo guarda en la galería para tener una URI de contenido. */
    private suspend fun generate(name: String, mime: String, vararg args: String): Uri {
        // Carpeta propia: al guardar, la galería borra la copia privada y su carpeta si queda vacía.
        val file = File(File(work, "gen-$name").apply { mkdirs() }, name)
        ffmpeg(*args, file.absolutePath)
        val saved = (
            library.save(
                OutputFile(file.absolutePath, "MediaForge converter test $name", mime, file.length()),
            ) as Outcome.Success
            ).value
        return Uri.parse(saved.location).also { created += it }
    }

    private suspend fun probe(file: File): LocalMedia {
        val runtime = native.ffmpegRuntime()
        val out = StringBuilder()
        runner.run(FfmpegCommand.probe(runtime, file), runtime.environment).collect {
            if (it is ProcessEvent.Stdout) out.appendLine(it.line)
        }
        return (FfprobeParser.parse(out.toString(), file.name) as Outcome.Success).value
    }

    private suspend fun convert(name: String, input: Uri, target: ConversionTarget): LocalMedia {
        val start = System.currentTimeMillis()
        val states = converter.convert(ConversionRequest("dev-$name", input.toString(), target, "out $name")).toList()
        val ms = System.currentTimeMillis() - start
        val last = states.last()
        log(
            "convert $name ms=$ms steps=${states.filterIsInstance<JobState.Processing>().map {
                it.step
            }.distinct()} last=$last",
        )
        assertTrue("$name: $last", last is JobState.Completed)
        val output = File((last as JobState.Completed).outputs.single().location)
        val media = probe(output)
        log("convert $name output=${output.name} size=${output.length()} video=${media.video} audio=${media.audio}")
        output.parentFile?.deleteRecursively()
        return media
    }

    @Test
    fun convertsTheSpecCases() = runBlocking {
        assertEquals(Outcome.Success(Unit), engine.ensureReady())
        val mp4 = generate(
            "in.mp4", "video/mp4",
            "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=20",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=20",
            "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
        )
        val webm = generate(
            "in.webm", "video/webm",
            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30:duration=10",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
            "-c:v", "libvpx", "-deadline", "realtime", "-b:v", "1M", "-c:a", "libvorbis",
        )
        val wav =
            generate(
                "in.wav",
                "audio/x-wav",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=10",
                "-c:a",
                "pcm_s16le",
            )

        // T6.4: análisis sin copiar.
        val inspected = (inspector.inspect(mp4.toString()) as Outcome.Success).value
        log("inspect mp4 $inspected")
        assertEquals("h264", inspected.video?.codec)
        assertEquals("aac", inspected.audio?.codec)
        assertEquals(720, inspected.video?.height)
        assertEquals(20.0, inspected.durationSeconds!!, 0.2)
        val inspectedWebm = (inspector.inspect(webm.toString()) as Outcome.Success).value
        log("inspect webm $inspectedWebm")
        assertEquals("vp8", inspectedWebm.video?.codec)
        assertEquals("vorbis", inspectedWebm.audio?.codec)
        assertEquals("pcm_s16le", (inspector.inspect(wav.toString()) as Outcome.Success).value.audio?.codec)

        // main.md §5.1 y criterio del MVP (MP4 → MP3).
        assertEquals("mp3", convert("mp4-mp3", mp4, ConversionTarget.Audio(AudioFormat.MP3)).audio?.codec)
        assertEquals("aac", convert("mp4-m4a", mp4, ConversionTarget.Audio(AudioFormat.M4A)).audio?.codec)
        assertEquals("pcm_s16le", convert("mp4-wav", mp4, ConversionTarget.Audio(AudioFormat.WAV)).audio?.codec)
        val mkv = convert("mp4-mkv", mp4, ConversionTarget.Video(VideoContainer.MKV))
        assertEquals("h264", mkv.video?.codec)
        val fromWebm = convert("webm-mp4", webm, ConversionTarget.Video(VideoContainer.MP4))
        assertEquals("h264", fromWebm.video?.codec)
        assertEquals("aac", fromWebm.audio?.codec)
        assertEquals(480, convert("mp4-480p", mp4, ConversionTarget.Video(VideoContainer.MP4, 480)).video?.height)
        assertEquals("flac", convert("wav-flac", wav, ConversionTarget.Audio(AudioFormat.FLAC)).audio?.codec)
        assertEquals("mp3", convert("webm-mp3", webm, ConversionTarget.Audio(AudioFormat.MP3)).audio?.codec)
    }

    @Test
    fun cancellingLeavesNoFiles() = runBlocking {
        assertEquals(Outcome.Success(Unit), engine.ensureReady())
        val mp4 = generate(
            "long.mp4", "video/mp4",
            "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=60",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
        )
        converter.convert(
            ConversionRequest("dev-cancel", mp4.toString(), ConversionTarget.Video(VideoContainer.WEBM), "x"),
        )
            .first { it is JobState.Processing && (it.progress ?: 0f) > 0.05f }
        Thread.sleep(1_000)
        assertFalse(File(jobs, "dev-cancel").exists())
        val leftovers = ProcessBuilder("ps", "-A", "-o", "NAME").start().inputStream.bufferedReader().readLines()
            .filter { it.trim().startsWith("libffmpeg") }
        log("cancel leftovers=$leftovers")
        assertTrue("leftover ffmpeg: $leftovers", leftovers.isEmpty())
    }
}
