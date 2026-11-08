package com.fabapps.mediaforge.media.ffmpeg

import com.fabapps.mediaforge.domain.convert.Clip
import com.fabapps.mediaforge.domain.convert.ConversionPlan
import com.fabapps.mediaforge.domain.convert.ConversionPlanner
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.file.FileNames
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Conversor offline con FFmpeg, sin yt-dlp (ADR-002, ADR-016).
 *
 * Pasos: copia la entrada a `<workRoot>/<jobId>/input` → `ffprobe` → plan por pista → FFmpeg con
 * progreso. Termina en `Completed` con la salida en esa carpeta (la copia de la entrada ya borrada);
 * guardarla en la galería es otro paso. Si falla o se cancela, se borra la carpeta.
 */
class FfmpegConverter(
    private val engine: EngineInitializer,
    private val runtimeProvider: FfmpegRuntimeProvider,
    private val runner: ProcessRunner,
    private val inputs: InputSource,
    private val workRoot: File,
    private val io: CoroutineDispatcher,
    private val freeBytes: () -> Long = { workRoot.apply { mkdirs() }.usableSpace },
) : MediaConverter {

    override fun convert(request: ConversionRequest): Flow<JobState> = flow {
        emit(JobState.Pending)
        if (!JOB_ID.matches(request.jobId)) return@flow emit(failed(MediaError.Unknown("invalid job id")))
        val ready = engine.ensureReady()
        if (ready is Outcome.Failure) return@flow emit(failed(ready.error))
        val info = inputs.describe(request.input)
            ?: return@flow emit(failed(MediaError.UnsupportedConversion("cannot open the input")))
        // La copia más una salida de tamaño parecido, antes de saber el plan.
        spaceError((info.sizeBytes ?: 0) * 2)?.let { return@flow emit(failed(it)) }

        val workDir = File(workRoot, request.jobId)
        workDir.deleteRecursively()
        workDir.mkdirs()
        var keep = false
        try {
            val runtime = runtimeProvider.ffmpegRuntime()
            val input = File(workDir, INPUT)
            emit(JobState.Processing(ProcessingStep.PREPARE_INPUT, null))
            try {
                inputs.copyTo(request.input, input)
            } catch (e: IOException) {
                return@flow emit(
                    failed(
                        storageOr(e.message) {
                            MediaError.UnsupportedConversion("cannot read the input: $it")
                        },
                    ),
                )
            }

            val media = when (val probed = probe(runtime, input, info.displayName)) {
                is Outcome.Success -> probed.value
                is Outcome.Failure -> return@flow emit(failed(probed.error))
            }
            val plan = when (val planned = ConversionPlanner.plan(media, request.target, request.clip)) {
                is Outcome.Success -> planned.value
                is Outcome.Failure -> return@flow emit(failed(planned.error))
            }
            spaceError(ConversionPlanner.estimateBytes(media, plan, request.clip) ?: 0)?.let {
                return@flow emit(failed(it))
            }

            val output = File(workDir, "$OUTPUT.${plan.extension}")
            val ok = runFfmpeg(runtime, input, output, plan, media, request.clip)
            if (!ok) return@flow
            input.delete()
            keep = true
            emit(
                JobState.Completed(
                    listOf(
                        OutputFile(
                            location = output.absolutePath,
                            displayName = FileNames.sanitize(request.fileName, plan.extension),
                            mimeType = plan.mimeType,
                            sizeBytes = output.length(),
                        ),
                    ),
                ),
            )
        } finally {
            if (!keep) workDir.deleteRecursively()
        }
    }.flowOn(io)

    private suspend fun probe(runtime: FfmpegRuntime, input: File, name: String): Outcome<LocalMedia> {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var code: Int? = null
        runner.run(FfmpegCommand.probe(runtime, input), runtime.environment).collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> stdout.appendLine(event.line)
                is ProcessEvent.Stderr -> stderr.appendLine(event.line)
                is ProcessEvent.Exited -> code = event.code
                ProcessEvent.TimedOut -> Unit
            }
        }
        if (code !=
            0
        ) {
            return Outcome.Failure(MediaError.UnsupportedConversion(stderr.toString().trim().take(DETAIL_CHARS)))
        }
        return FfprobeParser.parse(stdout.toString(), name)
    }

    /** `true` si terminó bien. Si no, ya emitió el error. */
    private suspend fun FlowCollector<JobState>.runFfmpeg(
        runtime: FfmpegRuntime,
        input: File,
        output: File,
        plan: ConversionPlan,
        media: LocalMedia,
        clip: Clip?,
    ): Boolean {
        val step = stepFor(plan)
        // El progreso de FFmpeg cuenta desde el inicio del fragmento.
        // Un fotograma no tiene progreso intermedio.
        val total = when {
            plan.frameAtSeconds != null -> null
            else -> clip?.lengthSeconds(media.durationSeconds) ?: media.durationSeconds
        }
        val progress = FfmpegProgressParser(total)
        val stderr = ArrayDeque<String>()
        var code: Int? = null
        emit(JobState.Processing(step, 0f))
        runner.run(FfmpegCommand.convert(runtime, input, output, plan, clip), runtime.environment).collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> progress.feed(event.line)?.let { emit(JobState.Processing(step, it)) }
                is ProcessEvent.Stderr -> {
                    stderr.addLast(event.line)
                    if (stderr.size > STDERR_LINES) stderr.removeFirst()
                }
                is ProcessEvent.Exited -> code = event.code
                ProcessEvent.TimedOut -> Unit
            }
        }
        if (code == 0 && output.isFile && output.length() > 0) return true
        val detail = stderr.joinToString("\n").take(DETAIL_CHARS).ifBlank { "ffmpeg exit code $code" }
        emit(failed(storageOr(detail) { MediaError.Unknown(it) }))
        return false
    }

    private fun stepFor(plan: ConversionPlan): ProcessingStep = when {
        plan.frameAtSeconds != null -> ProcessingStep.EXTRACT_FRAME
        plan.reencodes -> ProcessingStep.TRANSCODE
        plan.video == StreamPlan.Drop -> ProcessingStep.EXTRACT_AUDIO
        else -> ProcessingStep.REMUX
    }

    private fun spaceError(needed: Long): MediaError.InsufficientStorage? {
        val required = needed + SPACE_MARGIN_BYTES
        val free = freeBytes()
        return if (free < required) MediaError.InsufficientStorage(required, free) else null
    }

    private fun storageOr(detail: String?, other: (String?) -> MediaError): MediaError {
        val text = detail.orEmpty().lowercase()
        return if ("no space left" in text || "enospc" in text) {
            MediaError.InsufficientStorage(null, null, detail)
        } else {
            other(detail)
        }
    }

    private fun failed(error: MediaError) = JobState.Failed(error)

    companion object {
        const val SPACE_MARGIN_BYTES = 100L * 1000 * 1000
        private const val INPUT = "input"
        private const val OUTPUT = "output"
        private const val STDERR_LINES = 30
        private const val DETAIL_CHARS = 1_000
        private val JOB_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
