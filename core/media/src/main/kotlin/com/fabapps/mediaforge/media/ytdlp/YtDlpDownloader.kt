package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.file.FileNames
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobNote
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.domain.url.UrlValidator
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Descarga con yt-dlp a una carpeta privada por trabajo (`<workRoot>/<jobId>`).
 *
 * Termina en `Completed` con el archivo todavía en esa carpeta: guardarlo en la galería es otro paso
 * (`MediaLibrary`), que después borra la carpeta. Si falla o se cancela, la carpeta se borra acá:
 * no quedan parciales. Cancelar la colección termina yt-dlp y el ffmpeg que haya lanzado (ADR-005).
 *
 * No hay timeout total: una descarga grande puede tardar horas. Los cortes de red los informa yt-dlp.
 */
class YtDlpDownloader(
    private val engine: EngineInitializer,
    private val runtimeProvider: YtDlpRuntimeProvider,
    private val runner: ProcessRunner,
    private val workRoot: File,
    private val io: CoroutineDispatcher,
    private val freeBytes: () -> Long = { workRoot.apply { mkdirs() }.usableSpace },
) : MediaDownloader {

    override fun download(request: DownloadRequest): Flow<JobState> = flow {
        emit(JobState.Pending)
        val failure = precheck(request)
        if (failure != null) {
            emit(JobState.Failed(failure))
            return@flow
        }
        val url = (UrlValidator.validate(request.sourceUrl) as UrlCheck.Valid).url
        val selection = request.selection

        val workDir = File(workRoot, request.jobId)
        workDir.deleteRecursively()
        workDir.mkdirs()
        var keep = false
        try {
            val runtime = runtimeProvider.runtime()
            emit(JobState.Downloading(null, null, null, null))
            var result = attempt(runtime, url, selection, request.embedMetadata, workDir)
            val notes = mutableSetOf<JobNote>()
            // Un subtítulo que no se puede bajar no hace fallar el video: se baja de nuevo sin él (ADR-021).
            if (result.code != 0 &&
                selection is DownloadSelection.Video &&
                selection.subtitle != null &&
                YtDlpCommand.SUBTITLE_FAILURE in result.stderr
            ) {
                notes += JobNote.SUBTITLES_SKIPPED
                workDir.deleteRecursively()
                workDir.mkdirs()
                result = attempt(runtime, url, selection.copy(subtitle = null), request.embedMetadata, workDir)
            }
            if (result.code != 0) {
                emit(JobState.Failed(YtDlpErrorClassifier.classify(result.stderr)))
                return@flow
            }
            val output = findOutput(workDir, expectedExtension(selection))
            if (output == null) {
                emit(JobState.Failed(MediaError.Unknown(detail = "yt-dlp finished without an output file")))
                return@flow
            }
            keep = true
            emit(JobState.Completed(listOf(describe(output, request, selection)), notes))
        } finally {
            if (!keep) workDir.deleteRecursively()
        }
    }.flowOn(io)

    private class Attempt(val code: Int?, val stderr: String)

    /** Una ejecución de yt-dlp; emite el progreso y devuelve el código de salida y el final de stderr. */
    private suspend fun FlowCollector<JobState>.attempt(
        runtime: YtDlpRuntime,
        url: String,
        selection: DownloadSelection,
        embedMetadata: Boolean,
        workDir: File,
    ): Attempt {
        val command = YtDlpCommand.download(runtime, url, selection, workDir, embedMetadata)
        val parser = YtDlpProgressParser(audioStep = audioStep(selection))
        val stderr = ArrayDeque<String>()
        var exit: ProcessEvent? = null
        runner.run(command, runtime.environment, workingDir = workDir).collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> parser.feed(event.line)?.let { emit(it) }
                is ProcessEvent.Stderr -> {
                    stderr.addLast(event.line)
                    if (stderr.size > STDERR_LINES) stderr.removeFirst()
                }
                is ProcessEvent.Exited, ProcessEvent.TimedOut -> exit = event
            }
        }
        return Attempt((exit as? ProcessEvent.Exited)?.code, stderr.joinToString("\n"))
    }

    private suspend fun precheck(request: DownloadRequest): MediaError? {
        if (!JOB_ID.matches(request.jobId)) return MediaError.Unknown(detail = "invalid job id")
        val check = UrlValidator.validate(request.sourceUrl)
        if (check is UrlCheck.Invalid) return MediaError.InvalidUrl(detail = check.reason.name)
        val ready = engine.ensureReady()
        if (ready is Outcome.Failure) return ready.error
        return spaceError(request.expectedBytes)
    }

    /** Video y audio quedan en disco mientras se unen: hace falta el doble, más un margen. */
    private fun spaceError(expected: Long?): MediaError.InsufficientStorage? {
        val required = (expected ?: 0L) * 2 + SPACE_MARGIN_BYTES
        val free = freeBytes()
        return if (free < required) MediaError.InsufficientStorage(required, free) else null
    }

    /** Con la extensión esperada si existe; si no, el archivo más grande (no una portada suelta). */
    private fun findOutput(workDir: File, extension: String): File? {
        val candidates = workDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("${YtDlpCommand.OUTPUT_BASENAME}.") }
            .filterNot { it.name.endsWith(".part") || it.name.endsWith(".ytdl") || PART_FILE.matches(it.name) }
            .filterNot { it.extension.lowercase() in IMAGE_EXTENSIONS }
        return candidates.firstOrNull { it.extension.equals(extension, ignoreCase = true) }
            ?: candidates.maxByOrNull { it.length() }
    }

    private fun expectedExtension(selection: DownloadSelection) = when (selection) {
        is DownloadSelection.Video -> selection.container.extension
        is DownloadSelection.Audio -> selection.format.extension
    }

    /** Recodificar (bitrate fijo o sin pérdida) o copiar; el parser corrige si yt-dlp copia igual. */
    private fun audioStep(selection: DownloadSelection) = when {
        selection is DownloadSelection.Audio && (selection.bitrateKbps != null || selection.format.lossless) ->
            ProcessingStep.TRANSCODE
        else -> ProcessingStep.EXTRACT_AUDIO
    }

    private fun describe(file: File, request: DownloadRequest, selection: DownloadSelection): OutputFile {
        val extension = file.extension.lowercase()
        val mime = when {
            selection is DownloadSelection.Video && extension == selection.container.extension ->
                selection.container.mimeType
            selection is DownloadSelection.Audio && extension == selection.format.extension -> selection.format.mimeType
            selection is DownloadSelection.Audio -> "audio/$extension"
            else -> "video/$extension"
        }
        return OutputFile(
            location = file.absolutePath,
            displayName = FileNames.sanitize(request.fileName, extension),
            mimeType = mime,
            sizeBytes = file.length(),
        )
    }

    companion object {
        const val SPACE_MARGIN_BYTES = 100L * 1000 * 1000
        private const val STDERR_LINES = 50

        /** El ID forma una ruta: sin separadores ni puntos al inicio. */
        private val JOB_ID = Regex("[A-Za-z0-9_-]{1,64}")

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

        /** Partes antes del merge: `media.f299.mp4`, `media.fhls-380.mp4`. */
        private val PART_FILE = Regex("""${YtDlpCommand.OUTPUT_BASENAME}\.f[^.]+\..+""")
    }
}
