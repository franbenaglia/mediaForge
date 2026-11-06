package dev.mediaforge.spike

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Prueba técnica de la Etapa 0. Cada test registra sus mediciones con la etiqueta SPIKE en logcat.
 * Contenido de prueba: "Big Buck Bunny" (Blender Foundation, CC BY 3.0).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EngineSpikeTest {

    companion object {
        private const val TAG = "SPIKE"
        private const val YOUTUBE_URL = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        private const val VIMEO_URL = "https://vimeo.com/1084537"

        private lateinit var ctx: Context
        private lateinit var work: File

        fun log(msg: String) = msg.lines().forEach { Log.i(TAG, it) }

        @BeforeClass
        @JvmStatic
        fun setUp() {
            ctx = InstrumentationRegistry.getInstrumentation().targetContext
            work = File(ctx.cacheDir, "spike").apply { deleteRecursively(); mkdirs() }
            val t0 = System.currentTimeMillis()
            YoutubeDL.getInstance().init(ctx)
            FFmpeg.getInstance().init(ctx)
            log("init_ms=${System.currentTimeMillis() - t0}")
        }

        private val nativeDir get() = File(ctx.applicationInfo.nativeLibraryDir)
        private val packagesDir get() = File(ctx.noBackupFilesDir, "youtubedl-android/packages")

        /** Ejecuta un binario empaquetado con el mismo entorno que usa la librería. */
        fun runNative(bin: String, vararg args: String, timeoutSec: Long = 120): Pair<Int, String> {
            val pb = ProcessBuilder(listOf(File(nativeDir, bin).absolutePath) + args)
                .redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] =
                "${packagesDir}/python/usr/lib:${packagesDir}/ffmpeg/usr/lib"
            pb.environment()["PYTHONHOME"] = "${packagesDir}/python/usr"
            pb.environment()["TMPDIR"] = ctx.cacheDir.absolutePath
            val p = pb.start()
            val out = StringBuilder()
            val reader = thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            reader.join(2000)
            return (if (finished) p.exitValue() else -999) to out.toString()
        }
    }

    @Test
    fun t01_binariesAndVersions() {
        nativeDir.listFiles()!!.sortedBy { it.name }.forEach {
            log("native ${it.name} ${it.length() / 1024} KiB")
        }
        log("packages_dir_kib=${packagesDir.walkBottomUp().sumOf { it.length() } / 1024}")
        log("yt-dlp version=${YoutubeDL.getInstance().version(ctx)}")
        val (pc, py) = runNative("libpython.so", "--version")
        log("python exit=$pc out=${py.trim()}")
        val (qc, qjs) = runNative("libqjs.so", "-h")
        log("qjs exit=$qc first=${qjs.lineSequence().firstOrNull()}")
        val (fc, ff) = runNative("libffmpeg.so", "-hide_banner", "-version")
        log("ffmpeg exit=$fc")
        log(ff.lineSequence().take(3).joinToString("\n"))
        assertEquals(0, fc)
    }

    @Test
    fun t02_ffmpegEncoders() {
        val (_, enc) = runNative("libffmpeg.so", "-hide_banner", "-encoders")
        val wanted = listOf(
            "libmp3lame", "aac", "libopus", "opus", "libvorbis", "flac", "pcm_s16le", "alac",
            "libx264", "libx265", "libopenh264", "mpeg4", "libvpx", "libvpx-vp9", "libaom-av1",
            "libsvtav1", "librav1e", "h264_mediacodec", "hevc_mediacodec", "av1_mediacodec",
        )
        wanted.forEach { name ->
            val present = enc.lineSequence().any { it.trim().split(Regex("\\s+")).getOrNull(1) == name }
            log("encoder $name=${if (present) "YES" else "no"}")
        }
        val (_, conf) = runNative("libffmpeg.so", "-hide_banner", "-buildconf")
        log("buildconf:\n$conf")
    }

    @Test
    fun t03_offlineConversion() {
        val src = File(work, "src.mp4")
        val t0 = System.currentTimeMillis()
        val (gc, gout) = runNative(
            "libffmpeg.so", "-hide_banner", "-y",
            "-f", "lavfi", "-i", "testsrc=duration=10:size=640x360:rate=30",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
            "-c:v", "libx264", "-c:a", "aac", "-shortest", src.absolutePath,
        )
        log("gen_libx264 exit=$gc ms=${System.currentTimeMillis() - t0} size=${src.length()}")
        if (gc != 0) {
            log(gout.takeLast(800))
            val (gc2, _) = runNative(
                "libffmpeg.so", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=10:size=640x360:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
                "-c:v", "mpeg4", "-c:a", "aac", "-shortest", src.absolutePath,
            )
            log("gen_mpeg4 exit=$gc2 size=${src.length()}")
        }
        assertTrue(src.length() > 0)

        val mp3 = File(work, "out.mp3")
        val t1 = System.currentTimeMillis()
        val (mc, mout) = runNative(
            "libffmpeg.so", "-hide_banner", "-y", "-i", src.absolutePath,
            "-vn", "-c:a", "libmp3lame", "-b:a", "192k", "-progress", "pipe:1", mp3.absolutePath,
        )
        log("mp4_to_mp3 exit=$mc ms=${System.currentTimeMillis() - t1} size=${mp3.length()}")
        log("progress_lines=${mout.lineSequence().count { it.startsWith("out_time_us=") }}")

        val mkv = File(work, "remux.mkv")
        val (rc, _) = runNative("libffmpeg.so", "-hide_banner", "-y", "-i", src.absolutePath, "-c", "copy", mkv.absolutePath)
        log("remux_mkv exit=$rc size=${mkv.length()}")

        val opus = File(work, "out.opus")
        val (oc, _) = runNative("libffmpeg.so", "-hide_banner", "-y", "-i", src.absolutePath, "-vn", "-c:a", "libopus", opus.absolutePath)
        log("to_opus exit=$oc size=${opus.length()}")

        val flac = File(work, "out.flac")
        val (flc, _) = runNative("libffmpeg.so", "-hide_banner", "-y", "-i", mp3.absolutePath, flac.absolutePath)
        log("to_flac exit=$flc size=${flac.length()}")
        assertEquals(0, mc)
    }

    private fun probeInfo(label: String, url: String) {
        val t0 = System.currentTimeMillis()
        try {
            val resp = YoutubeDL.getInstance().execute(
                YoutubeDLRequest(url).addOption("--dump-json").addOption("--no-playlist"),
            )
            val json = org.json.JSONObject(resp.out)
            val arr = json.optJSONArray("formats") ?: org.json.JSONArray()
            val formats = (0 until arr.length()).map { arr.getJSONObject(it) }
            log("$label OK ms=${System.currentTimeMillis() - t0} title='${json.optString("title")}' extractor=${json.optString("extractor_key")} duration=${json.optDouble("duration")} formats=${formats.size}")
            log("$label heights=${formats.map { it.optInt("height", 0) }.filter { it > 0 }.distinct().sorted()}")
            log("$label vcodecs=${formats.map { it.optString("vcodec").substringBefore('.') }.distinct()}")
            log("$label acodecs=${formats.map { it.optString("acodec").substringBefore('.') }.distinct()}")
            log("$label stderr_warnings=${resp.err.lines().filter { it.contains("WARNING") }.take(6).joinToString(" | ").take(1200)}")
        } catch (e: Exception) {
            log("$label FAIL ms=${System.currentTimeMillis() - t0} ${e.javaClass.simpleName}: ${e.message?.take(1500)}")
        }
    }

    @Test
    fun t04_infoYoutube() = probeInfo("youtube", YOUTUBE_URL)

    @Test
    fun t05_infoVimeo() = probeInfo("vimeo", VIMEO_URL)

    @Test
    fun t06_downloadAudioMp3() {
        val out = File(work, "dl")
        var lastProgress = -1f
        var progressEvents = 0
        val t0 = System.currentTimeMillis()
        try {
            val req = YoutubeDLRequest(YOUTUBE_URL)
                .addOption("-f", "ba[ext=m4a]/ba")
                .addOption("-x")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "192K")
                .addOption("-o", "${out.absolutePath}/%(id)s.%(ext)s")
            YoutubeDL.getInstance().execute(req, "dl-audio") { p, _, _ ->
                progressEvents++
                lastProgress = p
            }
            val files = out.listFiles().orEmpty().map { "${it.name}:${it.length()}" }
            log("download_mp3 OK ms=${System.currentTimeMillis() - t0} progressEvents=$progressEvents last=$lastProgress files=$files")
        } catch (e: Exception) {
            log("download_mp3 FAIL ms=${System.currentTimeMillis() - t0} ${e.javaClass.simpleName}: ${e.message?.take(1500)}")
        }
    }

    @Test
    fun t07_cancel() {
        val out = File(work, "cancel")
        val t0 = System.currentTimeMillis()
        val canceller = thread {
            Thread.sleep(8000)
            val ok = YoutubeDL.getInstance().destroyProcessById("dl-cancel")
            log("cancel_called result=$ok")
        }
        try {
            val req = YoutubeDLRequest(YOUTUBE_URL)
                .addOption("-f", "bv*[height<=720]+ba/b")
                .addOption("-o", "${out.absolutePath}/%(id)s.%(ext)s")
            YoutubeDL.getInstance().execute(req, "dl-cancel") { _, _, _ -> }
            log("cancel NOT_CANCELLED finished ms=${System.currentTimeMillis() - t0}")
        } catch (e: YoutubeDL.CanceledException) {
            log("cancel OK CanceledException ms=${System.currentTimeMillis() - t0}")
        } catch (e: Exception) {
            log("cancel other ${e.javaClass.simpleName}: ${e.message?.take(500)}")
        }
        canceller.join()
        Thread.sleep(1000)
        val (_, ps) = runNative("libffmpeg.so", "-version") // dummy para asegurar entorno
        val leftovers = ProcessBuilder("ps", "-A", "-o", "PID,NAME,ARGS").redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().lines()
            .filter { it.contains("libpython") || it.contains("libffmpeg") }
        log("cancel leftover_processes=${leftovers.size} ${leftovers.joinToString(" | ").take(500)} ${ps.length}")
        log("cancel partial_files=${out.listFiles().orEmpty().map { "${it.name}:${it.length()}" }}")
    }

    @Test
    fun t08_update() {
        val before = YoutubeDL.getInstance().version(ctx)
        val t0 = System.currentTimeMillis()
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
            log("update status=$status before=$before after=${YoutubeDL.getInstance().version(ctx)} ms=${System.currentTimeMillis() - t0}")
        } catch (e: Exception) {
            log("update FAIL ${e.javaClass.simpleName}: ${e.message?.take(800)}")
        }
    }

    @Test
    fun t09_infoYoutubeAfterUpdate() = probeInfo("youtube_updated", YOUTUBE_URL)

    @Test
    fun t10_downloadAudioAfterUpdate() {
        val out = File(work, "dl2")
        val t0 = System.currentTimeMillis()
        try {
            val req = YoutubeDLRequest(YOUTUBE_URL)
                .addOption("-f", "ba[ext=m4a]/ba")
                .addOption("-x").addOption("--audio-format", "mp3")
                .addOption("-o", "${out.absolutePath}/%(id)s.%(ext)s")
            YoutubeDL.getInstance().execute(req, "dl-audio2") { _, _, _ -> }
            log("download_mp3_updated OK ms=${System.currentTimeMillis() - t0} files=${out.listFiles().orEmpty().map { "${it.name}:${it.length()}" }}")
        } catch (e: Exception) {
            log("download_mp3_updated FAIL ms=${System.currentTimeMillis() - t0} ${e.javaClass.simpleName}: ${e.message?.take(1500)}")
        }
    }
}
