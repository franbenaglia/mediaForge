package com.fabapps.mediaforge.media.update

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.media.engine.EngineInitializer
import com.fabapps.mediaforge.media.process.ProcessEvent
import com.fabapps.mediaforge.media.process.ProcessRunner
import com.fabapps.mediaforge.media.ytdlp.YtDlpRuntimeProvider
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** Ejecuta Python y yt-dlp del motor para conocer sus versiones. */
internal class AndroidVersionProbe(
    private val engine: EngineInitializer,
    private val runtimeProvider: YtDlpRuntimeProvider,
    private val runner: ProcessRunner,
    private val bundled: File,
) : VersionProbe {

    override suspend fun ytDlpVersion(ytDlp: File): String? {
        val runtime = runtimeProvider.runtime()
        return firstLine(listOf(runtime.python.absolutePath, ytDlp.absolutePath, "--ignore-config", "--version"))
    }

    override suspend fun pythonVersion(): String? {
        val runtime = runtimeProvider.runtime()
        return firstLine(listOf(runtime.python.absolutePath, "--version"))?.removePrefix("Python ")?.trim()
    }

    override fun bundledYtDlp(): File = bundled

    private suspend fun firstLine(command: List<String>): String? {
        if (engine.ensureReady() is Outcome.Failure) return null
        var first: String? = null
        var code: Int? = null
        runner.run(command, runtimeProvider.runtime().environment, timeout = 60.seconds).collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> if (first == null) first = event.line.trim()
                is ProcessEvent.Exited -> code = event.code
                else -> Unit
            }
        }
        return first?.takeIf { code == 0 && it.isNotEmpty() }
    }
}
