package com.fabapps.mediaforge.media.ffmpeg

import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.StreamPlan
import java.io.File

/** Binarios de FFmpeg del APK y su entorno. */
data class FfmpegRuntime(val ffmpeg: File, val ffprobe: File, val environment: Map<String, String>)

/** Arma comandos de FFmpeg. Funciones puras: se prueban en la JVM. */
object FfmpegCommand {
    fun probe(runtime: FfmpegRuntime, input: File): List<String> = listOf(
        runtime.ffprobe.absolutePath,
        "-v",
        "error",
        "-print_format",
        "json",
        "-show_format",
        "-show_streams",
        input.absolutePath,
    )

    /**
     * Conversión según [plan]. Entrada y salida son rutas absolutas dentro de la carpeta del trabajo (nunca
     * empiezan con `-`). `-map` explícito: solo la primera pista de video y la primera de audio, sin
     * subtítulos ni pistas de datos que el contenedor de destino podría rechazar.
     */
    fun convert(runtime: FfmpegRuntime, input: File, output: File, plan: ConversionPlan): List<String> {
        val args = mutableListOf(
            runtime.ffmpeg.absolutePath,
            "-nostdin",
            "-hide_banner",
            "-nostats",
            "-loglevel",
            "error",
            "-progress",
            "pipe:1",
            "-y",
            "-i",
            input.absolutePath,
        )
        args += when (plan.video) {
            StreamPlan.Drop -> listOf("-vn")
            else -> listOf("-map", "0:v:0")
        }
        if (plan.audio != StreamPlan.Drop) args += listOf("-map", "0:a:0?") else args += "-an"
        args += videoArgs(plan)
        args += audioArgs(plan.audio)
        args += listOf("-map_metadata", "0")
        if (plan.extension == "mp4" || plan.extension == "mov" || plan.extension == "m4a") {
            // El índice al principio: el archivo se puede empezar a reproducir sin leerlo entero.
            args += listOf("-movflags", "+faststart")
        }
        args += output.absolutePath
        return args
    }

    private fun videoArgs(plan: ConversionPlan): List<String> = when (val video = plan.video) {
        StreamPlan.Drop -> emptyList()
        StreamPlan.Copy -> listOf("-c:v", "copy")
        is StreamPlan.Encode -> {
            val scale = plan.scaleToHeight?.let { listOf("-vf", "scale=-2:$it") }.orEmpty()
            val encoder = when (video.codec) {
                "h264" -> listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p")
                "vp9" -> listOf(
                    "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8",
                    "-crf", "32", "-b:v", "0", "-row-mt", "1",
                )
                else -> error("no encoder for ${video.codec}")
            }
            scale + encoder
        }
    }

    private fun audioArgs(audio: StreamPlan): List<String> = when (audio) {
        StreamPlan.Drop -> emptyList()
        StreamPlan.Copy -> listOf("-c:a", "copy")
        is StreamPlan.Encode -> {
            val encoder = when (audio.codec) {
                "aac" -> "aac"
                "mp3" -> "libmp3lame"
                "opus" -> "libopus"
                "vorbis" -> "libvorbis"
                "flac" -> "flac"
                "pcm_s16le" -> "pcm_s16le"
                else -> error("no encoder for ${audio.codec}")
            }
            listOf("-c:a", encoder) + (audio.bitrateKbps?.let { listOf("-b:a", "${it}k") } ?: emptyList())
        }
    }
}
