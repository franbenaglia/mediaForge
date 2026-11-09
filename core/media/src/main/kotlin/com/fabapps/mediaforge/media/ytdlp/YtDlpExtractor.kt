package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.domain.url.UrlValidator
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Obtiene la información de un recurso (video o lista) con `yt-dlp --dump-single-json`. */
class YtDlpExtractor @Inject constructor(
    private val engine: EngineInitializer,
    private val runtimeProvider: YtDlpRuntimeProvider,
    private val runner: ProcessRunner,
) : MediaExtractor {

    /** Se acorta en pruebas. */
    internal var timeout: Duration = DEFAULT_TIMEOUT

    /** Se achica en pruebas. Un video de YouTube con todos sus formatos ronda 1–3 MB. */
    internal var maxOutputChars: Int = MAX_OUTPUT_CHARS

    override suspend fun extract(url: String): Outcome<MediaInfo> {
        val valid = when (val check = UrlValidator.validate(url)) {
            is UrlCheck.Valid -> check
            is UrlCheck.Invalid -> return Outcome.Failure(MediaError.InvalidUrl(detail = check.reason.name))
        }
        when (val ready = engine.ensureReady()) {
            is Outcome.Failure -> return ready
            is Outcome.Success -> Unit
        }

        val command = YtDlpCommand.info(runtimeProvider.runtime(), valid.url)
        val stdout = StringBuilder()
        val stderr = ArrayDeque<String>()
        var end: ProcessEvent? = null
        var tooLarge = false
        runner.run(command, runtimeProvider.runtime().environment, timeout = timeout).collect { event ->
            when (event) {
                // Con un tope: un sitio no puede llenar la memoria con una respuesta enorme (main.md §11.3).
                is ProcessEvent.Stdout -> if (stdout.length + event.line.length < maxOutputChars) {
                    stdout.appendLine(event.line)
                } else {
                    tooLarge = true
                }
                is ProcessEvent.Stderr -> {
                    stderr.addLast(event.line.take(MAX_STDERR_LINE_CHARS))
                    if (stderr.size > MAX_STDERR_LINES) stderr.removeFirst()
                }
                is ProcessEvent.Exited, ProcessEvent.TimedOut -> end = event
            }
        }
        if (tooLarge) return Outcome.Failure(MediaError.Unknown(detail = "yt-dlp output over $maxOutputChars chars"))

        return when (val result = end) {
            ProcessEvent.TimedOut -> Outcome.Failure(MediaError.Network(detail = "yt-dlp timed out after $timeout"))
            is ProcessEvent.Exited -> if (result.code == 0) {
                YtDlpInfoParser.parse(stdout.toString())
            } else {
                Outcome.Failure(YtDlpErrorClassifier.classify(stderr.joinToString("\n")))
            }
            else -> Outcome.Failure(MediaError.Unknown(detail = "yt-dlp ended without exit code"))
        }
    }

    companion object {
        /** YouTube con QuickJS tardó 28 s en el emulador (Etapa 0); se deja margen para redes lentas. */
        val DEFAULT_TIMEOUT = 120.seconds
        const val MAX_OUTPUT_CHARS = 32 * 1024 * 1024
        private const val MAX_STDERR_LINES = 200
        private const val MAX_STDERR_LINE_CHARS = 2_000
    }
}

/** Separado de [com.fabapps.mediaforge.media.engine.NativeEngine] para poder probar el extractor en la JVM. */
fun interface YtDlpRuntimeProvider {
    fun runtime(): YtDlpRuntime
}
