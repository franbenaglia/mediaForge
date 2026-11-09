package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.Playlist
import com.fabapps.mediaforge.domain.model.SubtitleTrack
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
     * Información de un recurso en un solo JSON. [url] tiene que venir de `UrlValidator`. Igual va
     * después de `--`, así nunca puede interpretarse como opción.
     * - Un video sale completo, igual que con `--dump-json`.
     * - Una lista sale con sus elementos sin analizar cada video (`--flat-playlist`), hasta 200 (ADR-021).
     */
    fun info(runtime: YtDlpRuntime, url: String): List<String> {
        require(!url.startsWith("-")) { "url looks like an option" }
        return base(runtime) + listOf(
            "--dump-single-json",
            "--flat-playlist",
            "--playlist-items",
            "1-${Playlist.MAX_ENTRIES}",
            "--no-progress",
            "--",
            url,
        )
    }

    /** Prefijo de las líneas de progreso (ver [YtDlpProgressParser]). */
    const val PROGRESS_PREFIX = "MFPROG"

    /** Nombre base del archivo dentro de la carpeta del trabajo. El nombre visible se pone al guardar. */
    const val OUTPUT_BASENAME = "media"

    /**
     * Descarga a [workDir], una carpeta vacía y propia del trabajo. El nombre elegido por el usuario no
     * pasa por yt-dlp: se aplica al guardar, con `FileNames`.
     * - Plantilla de progreso propia, una línea por actualización (`--newline`).
     * - Video: `--merge-output-format`, video y audio se unen sin recodificar.
     * - Audio: `-x`; yt-dlp copia sin recodificar si el códec ya es el de salida (ADR-015).
     */
    fun download(
        runtime: YtDlpRuntime,
        url: String,
        selection: DownloadSelection,
        workDir: File,
        embedMetadata: Boolean,
    ): List<String> {
        require(!url.startsWith("-")) { "url looks like an option" }
        val common = listOf(
            "-o",
            File(workDir, "$OUTPUT_BASENAME.%(ext)s").absolutePath,
            "--no-mtime",
            "--newline",
            "--progress-template",
            "download:$PROGRESS_PREFIX %(progress.status)s %(progress.downloaded_bytes)s " +
                "%(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s",
        )
        val specific = when (selection) {
            is DownloadSelection.Video -> listOf(
                "-f",
                formatSelector(selection),
                "--merge-output-format",
                selection.container.extension,
            ) + subtitleOptions(selection.subtitle)
            is DownloadSelection.Audio -> audioOptions(selection)
        }
        val metadata = if (embedMetadata) listOf("--embed-metadata") else emptyList()
        // WAV no tiene dónde guardar una portada.
        val cover = if (embedMetadata && selection is DownloadSelection.Audio && selection.format != AudioFormat.WAV) {
            listOf("--embed-thumbnail")
        } else {
            emptyList()
        }
        return base(runtime) + specific + common + metadata + cover + listOf("--", url)
    }

    /**
     * Un solo idioma, incrustado como pista de texto (ADR-021). WebVTT primero: lo aceptan los tres
     * contenedores (en MP4 FFmpeg lo pasa a `mov_text`). yt-dlp borra el archivo suelto al incrustarlo.
     */
    internal fun subtitleOptions(track: SubtitleTrack?): List<String> {
        if (track == null) return emptyList()
        require(SubtitleTrack.LANGUAGE.matches(track.language)) { "invalid subtitle language" }
        return listOf(
            if (track.automatic) "--write-auto-subs" else "--write-subs",
            "--sub-langs",
            track.language,
            "--sub-format",
            "vtt/srt/best",
            "--embed-subs",
        )
    }

    /** Lo que escribe yt-dlp cuando no puede bajar un subtítulo (y sin `-i`, corta la descarga). */
    const val SUBTITLE_FAILURE = "Unable to download video subtitles"

    private fun audioOptions(selection: DownloadSelection.Audio): List<String> {
        selection.formatIds.forEach { id -> require(FORMAT_ID.matches(id)) { "invalid format id: $id" } }
        val source = (selection.formatIds + listOf("bestaudio", "best")).joinToString("/")
        val quality = selection.bitrateKbps?.let { kbps ->
            require(kbps in 8..512) { "invalid bitrate: $kbps" }
            listOf("--audio-quality", "${kbps}K")
        } ?: emptyList()
        return listOf("-f", source, "-x", "--audio-format", ytDlpAudioFormat(selection.format)) + quality
    }

    /** Nombre de `--audio-format`. OGG es Vorbis; yt-dlp lo guarda como `.ogg`. */
    internal fun ytDlpAudioFormat(format: AudioFormat): String = when (format) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.M4A -> "m4a"
        AudioFormat.OPUS -> "opus"
        AudioFormat.OGG -> "vorbis"
        AudioFormat.WAV -> "wav"
        AudioFormat.FLAC -> "flac"
    }

    /**
     * Selector de `-f`. Con formatos concretos (`299+258`) se baja exactamente lo que se mostró; si el
     * sitio ya no los ofrece, sigue con lo mejor hasta la altura elegida.
     */
    internal fun formatSelector(selection: DownloadSelection.Video): String {
        selection.formatIds.forEach { id -> require(FORMAT_ID.matches(id)) { "invalid format id: $id" } }
        val byHeight = selection.maxHeight?.let { h -> "bv*[height<=$h]+ba/b[height<=$h]" } ?: "bv*+ba/b"
        return if (selection.formatIds.isEmpty()) {
            byHeight
        } else {
            selection.formatIds.joinToString("+") + "/" + byHeight
        }
    }

    /** Los IDs vienen del sitio: solo caracteres que no cambian la sintaxis del selector. */
    private val FORMAT_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")

    fun version(runtime: YtDlpRuntime): List<String> =
        listOf(runtime.python.absolutePath, runtime.ytDlp.absolutePath, "--ignore-config", "--version")
}
