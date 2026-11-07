package com.fabapps.mediaforge.media.process

import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ProcessEvent {
    data class Stdout(val line: String) : ProcessEvent

    data class Stderr(val line: String) : ProcessEvent

    /** Último evento si el proceso terminó solo. */
    data class Exited(val code: Int) : ProcessEvent

    /** Último evento si se superó el tiempo límite; el proceso y sus hijos ya fueron terminados. */
    data object TimedOut : ProcessEvent
}

/**
 * Ejecuta binarios nativos sin pasar por un shell que interprete los argumentos.
 *
 * El comando se lanza como `sh -c 'echo $$; exec "$@"' sh <cmd> <args…>`: el shell imprime su PID
 * (que pasa a ser el del comando tras `exec`) y los argumentos llegan como parámetros posicionales,
 * sin interpretarse. Con el PID real se puede terminar el árbol completo de procesos (por ejemplo,
 * el ffmpeg que lanza yt-dlp), cosa que `Process.destroy()` no hace (FEASIBILITY §9).
 *
 * Cancelar la corrutina que colecta el `Flow` termina el árbol de procesos (ADR-005).
 */
class ProcessRunner(
    private val io: CoroutineDispatcher,
    private val shell: String = "/system/bin/sh",
    private val procDir: File = File("/proc"),
) {
    fun run(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDir: File? = null,
        timeout: Duration? = null,
    ): Flow<ProcessEvent> = channelFlow {
        require(command.isNotEmpty()) { "empty command" }
        val builder = ProcessBuilder(listOf(shell, "-c", WRAPPER, "sh") + command)
        builder.environment().putAll(environment)
        workingDir?.let(builder::directory)

        val process = builder.start()
        process.outputStream.close()
        val stdout = process.inputStream.bufferedReader(Charsets.UTF_8)
        val pid = stdout.readLine()?.trim()?.toIntOrNull()

        var finished = false
        try {
            val readers: List<Job> = listOf(
                launch { forwardLines(stdout.lineSequence(), this@channelFlow) { ProcessEvent.Stdout(it) } },
                launch {
                    val stderr = process.errorStream.bufferedReader(Charsets.UTF_8)
                    forwardLines(stderr.lineSequence(), this@channelFlow) { ProcessEvent.Stderr(it) }
                },
            )
            val code = if (timeout == null) {
                runInterruptible { process.waitFor() }
            } else {
                withTimeoutOrNull(timeout) { runInterruptible { process.waitFor() } }
            }
            if (code == null) {
                killTree(process, pid)
                readers.joinAll()
                send(ProcessEvent.TimedOut)
            } else {
                readers.joinAll()
                send(ProcessEvent.Exited(code))
            }
            finished = true
        } finally {
            if (!finished) withContext(NonCancellable) { killTree(process, pid) }
        }
    }.flowOn(io)

    private suspend fun forwardLines(
        lines: Sequence<String>,
        channel: SendChannel<ProcessEvent>,
        wrap: (String) -> ProcessEvent,
    ) {
        try {
            for (line in lines) channel.send(wrap(line))
        } catch (_: IOException) {
            // El proceso fue terminado y se cerró el pipe.
        }
    }

    /** Termina primero los descendientes (se buscan antes, porque al morir el padre quedan huérfanos). */
    private fun killTree(process: Process, pid: Int?) {
        if (pid != null) {
            val tree = descendants(pid)
            (tree + pid).forEach(::killPid)
        }
        process.destroyForcibly()
    }

    internal fun descendants(root: Int): List<Int> {
        val parents = procDir.listFiles { f -> f.name.all(Char::isDigit) }.orEmpty()
            .mapNotNull { dir -> parentOf(dir)?.let { dir.name.toInt() to it } }
        val result = mutableListOf<Int>()
        var frontier = listOf(root)
        while (frontier.isNotEmpty()) {
            val children = parents.filter { (_, ppid) -> ppid in frontier }.map { it.first }
            result += children
            frontier = children
        }
        return result
    }

    /** `/proc/<pid>/stat`: el nombre del proceso va entre paréntesis y puede tener espacios. */
    private fun parentOf(dir: File): Int? = try {
        val stat = File(dir, "stat").readText()
        stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toIntOrNull()
    } catch (_: IOException) {
        null
    }

    private fun killPid(pid: Int) {
        try {
            ProcessBuilder("kill", "-9", pid.toString()).redirectErrorStream(true).start().waitFor()
        } catch (_: IOException) {
            // Ya terminó.
        }
    }

    private companion object {
        const val WRAPPER = "echo \$\$; exec \"\$@\""
    }
}
