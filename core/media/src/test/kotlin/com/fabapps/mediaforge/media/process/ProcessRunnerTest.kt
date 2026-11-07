package com.fabapps.mediaforge.media.process

import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corre procesos reales en la JVM (Linux), con /bin/sh en lugar de /system/bin/sh. */
class ProcessRunnerTest {
    private val runner = ProcessRunner(Dispatchers.IO, shell = "/bin/sh")

    private fun runAll(vararg command: String, env: Map<String, String> = emptyMap()) = runBlocking {
        withTimeout(10.seconds) { runner.run(command.toList(), env).toList() }
    }

    private fun alive(pid: Int) = File("/proc/$pid").exists() &&
        !File("/proc/$pid/stat").readText().substringAfterLast(')').trim().startsWith("Z")

    @Test
    fun forwardsStdoutStderrAndExitCode() {
        val events = runAll("sh", "-c", "echo out1; echo err1 >&2; echo out2; exit 3")
        assertEquals(
            listOf(ProcessEvent.Stdout("out1"), ProcessEvent.Stdout("out2")),
            events.filterIsInstance<ProcessEvent.Stdout>(),
        )
        assertEquals(listOf(ProcessEvent.Stderr("err1")), events.filterIsInstance<ProcessEvent.Stderr>())
        assertEquals(ProcessEvent.Exited(3), events.last())
    }

    @Test
    fun argumentsAreNeverInterpretedByTheShell() {
        val tricky = listOf("a b; echo INJECTED", "\$(echo INJECTED)", "`echo INJECTED`", "\"quoted\"", "--", "-x")
        val events = runAll("printf", "%s\\n", *tricky.toTypedArray())
        assertEquals(tricky, events.filterIsInstance<ProcessEvent.Stdout>().map { it.line })
        assertEquals(ProcessEvent.Exited(0), events.last())
    }

    @Test
    fun utf8OutputIsPreserved() {
        val events = runAll("printf", "%s\\n", "ñandú 😀")
        assertEquals("ñandú 😀", (events.first() as ProcessEvent.Stdout).line)
    }

    @Test
    fun environmentIsPassed() {
        val events = runAll("sh", "-c", "echo \$MF_TEST", env = mapOf("MF_TEST" to "valor"))
        assertEquals(ProcessEvent.Stdout("valor"), events.first())
    }

    @Test
    fun missingBinaryEndsWithExitCode127() {
        val events = runAll("/nonexistent/binary")
        assertEquals(ProcessEvent.Exited(127), events.last())
    }

    @Test
    fun cancellingKillsTheProcessAndItsChildren() = runBlocking {
        var childPid = -1
        val job = launch {
            runner.run(listOf("sh", "-c", "sleep 30 & echo \$!; wait")).collect { event ->
                if (event is ProcessEvent.Stdout) childPid = event.line.trim().toInt()
            }
        }
        withTimeout(5.seconds) { while (childPid == -1) delay(20) }
        assertTrue(alive(childPid))

        job.cancel()
        job.join()

        withTimeout(5.seconds) { while (alive(childPid)) delay(20) }
        assertFalse(alive(childPid))
    }

    @Test
    fun timeoutKillsAndReportsTimedOut() = runBlocking {
        val start = System.nanoTime()
        val events = withTimeout(10.seconds) {
            runner.run(listOf("sleep", "30"), timeout = 300.milliseconds).toList()
        }
        assertEquals(ProcessEvent.TimedOut, events.last())
        assertTrue((System.nanoTime() - start) < 5_000_000_000L)
    }

    @Test
    fun firstEventArrivesWhileProcessStillRuns() = runBlocking {
        val first = withTimeout(5.seconds) {
            runner.run(listOf("sh", "-c", "echo ready; sleep 30")).first()
        }
        // `first()` cancela el flow: el sleep tiene que terminar con él.
        assertEquals(ProcessEvent.Stdout("ready"), first)
    }

    @Test
    fun descendantsFindsWholeTree() = runBlocking {
        var pids: List<Int> = emptyList()
        val job = launch {
            runner.run(listOf("sh", "-c", "sh -c 'sleep 30 & echo \$!; wait' & wait")).collect { event ->
                if (event is ProcessEvent.Stdout) pids = pids + event.line.trim().toInt()
            }
        }
        withTimeout(5.seconds) { while (pids.isEmpty()) delay(20) }
        val grandchild = pids.single()
        assertTrue(alive(grandchild))
        job.cancel()
        job.join()
        withTimeout(5.seconds) { while (alive(grandchild)) delay(20) }
    }
}
