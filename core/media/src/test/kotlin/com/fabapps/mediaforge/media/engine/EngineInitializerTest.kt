package com.fabapps.mediaforge.media.engine

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineInitializerTest {

    private class FakeNative(
        var compressed: Long = 50_000_000,
        var free: Long = 10_000_000_000,
        var extracted: Boolean = false,
        var failWith: Exception? = null,
    ) : NativeEngine {
        var initCalls = 0
        override fun compressedPackageBytes() = compressed
        override fun isExtracted() = extracted
        override fun freeBytes() = free
        override fun initialize() {
            initCalls++
            failWith?.let { throw it }
            extracted = true
        }
    }

    @Test
    fun requiredSpaceUsesMeasuredExpansionPlusMargin() {
        assertEquals(51_600_000L * 5 + 50_000_000, EngineSpace.requiredBytes(51_600_000))
    }

    @Test
    fun spaceCheckPassesWhenAlreadyExtracted() {
        assertNull(EngineSpace.check(compressedBytes = 50_000_000, freeBytes = 0, alreadyExtracted = true))
    }

    @Test
    fun spaceCheckReportsRequiredAndAvailable() {
        val error = EngineSpace.check(compressedBytes = 50_000_000, freeBytes = 100_000_000, alreadyExtracted = false)
        assertEquals(300_000_000L, error?.requiredBytes)
        assertEquals(100_000_000L, error?.availableBytes)
    }

    @Test
    fun initializesOnceAndReportsReady() = runTest {
        val native = FakeNative()
        val init = EngineInitializer(native, StandardTestDispatcher(testScheduler))
        assertEquals(EngineState.NotInitialized, init.state.value)

        assertEquals(Outcome.Success(Unit), init.ensureReady())
        assertEquals(Outcome.Success(Unit), init.ensureReady())

        assertEquals(EngineState.Ready, init.state.value)
        assertEquals(1, native.initCalls)
    }

    @Test
    fun concurrentCallsInitializeOnlyOnce() = runTest {
        val native = FakeNative()
        val init = EngineInitializer(native, StandardTestDispatcher(testScheduler))
        val results = (1..5).map { async { init.ensureReady() } }.awaitAll()
        assertTrue(results.all { it == Outcome.Success(Unit) })
        assertEquals(1, native.initCalls)
    }

    @Test
    fun notEnoughSpaceFailsWithoutTouchingTheEngine() = runTest {
        val native = FakeNative(free = 1_000)
        val init = EngineInitializer(native, StandardTestDispatcher(testScheduler))

        val result = init.ensureReady()

        assertTrue((result as Outcome.Failure).error is MediaError.InsufficientStorage)
        assertTrue((init.state.value as EngineState.Failed).error is MediaError.InsufficientStorage)
        assertEquals(0, native.initCalls)
    }

    @Test
    fun engineExceptionBecomesFailureAndCanBeRetried() = runTest {
        val native = FakeNative(failWith = IllegalStateException("unzip failed"))
        val init = EngineInitializer(native, StandardTestDispatcher(testScheduler))

        val first = init.ensureReady() as Outcome.Failure
        assertEquals("unzip failed", first.error.detail)

        native.failWith = null
        assertEquals(Outcome.Success(Unit), init.ensureReady())
        assertEquals(EngineState.Ready, init.state.value)
    }
}
