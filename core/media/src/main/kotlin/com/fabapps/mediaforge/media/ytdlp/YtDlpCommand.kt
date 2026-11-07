package com.fabapps.mediaforge.media.ytdlp

import java.io.File

/** Dónde están los binarios y con qué entorno se ejecutan. Lo provee la implementación nativa. */
data class YtDlpRuntime(
    val python: File,
    val ytDlp: File,
    val ffmpeg: File,
    val quickJs: File,
    val cacheDir: File,
    val environment: Map<String, String>,
)

/** Arma los comandos de yt-dlp. Funciones puras: se prueban en la JVM. */
object YtDlpCommand {
    /**
     * Opciones comunes a toda ejecución:
     * - `--ignore-config`: ninguna configuración externa cambia el comportamiento.
     * - `--no-playlist`: un enlace de video dentro de una lista trae solo ese video.
     * - `--cache-dir` propio: la librería lo desactiva y eso hace más lenta cada consulta a YouTube.
     * - QuickJS y FFmpeg del APK, con ruta explícita.
     */
    private fun base(runtime: YtDlpRuntime): List<String> = listOf(
        runtime.python.absolutePath,
        runtime.ytDlp.absolutePath,
        "--ignore-config",
        "--no-playlist",
        "--cache-dir",
        runtime.cacheDir.absolutePath,
        "--js-runtimes",
        "quickjs:${runtime.quickJs.absolutePath}",
        "--ffmpeg-location",
        runtime.ffmpeg.absolutePath,
    )

    /**
     * `--dump-json` de un recurso. [url] tiene que venir de `UrlValidator`. Igual va después de `--`,
     * así nunca puede interpretarse como opción.
     */
    fun info(runtime: YtDlpRuntime, url: String): List<String> {
        require(!url.startsWith("-")) { "url looks like an option" }
        return base(runtime) + listOf("--dump-json", "--no-progress", "--", url)
    }

    fun version(runtime: YtDlpRuntime): List<String> =
        listOf(runtime.python.absolutePath, runtime.ytDlp.absolutePath, "--ignore-config", "--version")
}
