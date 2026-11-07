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

/** Obtiene la información de un recurso con `yt-dlp --dump-json`. */
class YtDlpExtractor @Inject constructor(
    private val engine: EngineInitializer,
    private val runtimeProvider: YtDlpRuntimeProvider,
    private val runner: ProcessRunner,
) : MediaExtractor {

    /** Se acorta en pruebas. */
    internal var timeout: Duration = DEFAULT_TIMEOUT

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
        val stderr = StringBuilder()
        var end: ProcessEvent? = null
        runner.run(command, runtimeProvider.runtime().environment, timeout = timeout).collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> stdout.appendLine(event.line)
                is ProcessEvent.Stderr -> stderr.appendLine(event.line)
                is ProcessEvent.Exited, ProcessEvent.TimedOut -> end = event
            }
        }

        return when (val result = end) {
            ProcessEvent.TimedOut -> Outcome.Failure(MediaError.Network(detail = "yt-dlp timed out after $timeout"))
            is ProcessEvent.Exited -> if (result.code == 0) {
                YtDlpInfoParser.parse(stdout.toString())
            } else {
                Outcome.Failure(YtDlpErrorClassifier.classify(stderr.toString()))
            }
            else -> Outcome.Failure(MediaError.Unknown(detail = "yt-dlp ended without exit code"))
        }
    }

    companion object {
        /** YouTube con QuickJS tardó 28 s en el emulador (Etapa 0); se deja margen para redes lentas. */
        val DEFAULT_TIMEOUT = 120.seconds
    }
}

/** Separado de [com.fabapps.mediaforge.media.engine.NativeEngine] para poder probar el extractor en la JVM. */
fun interface YtDlpRuntimeProvider {
    fun runtime(): YtDlpRuntime
}
