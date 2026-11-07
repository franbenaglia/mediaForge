package com.fabapps.mediaforge.ui.home

import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.SourceSite
import com.fabapps.mediaforge.domain.url.UrlCheck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeExtractor : MediaExtractor {
        val calls = mutableListOf<String>()
        var pending = CompletableDeferred<Outcome<MediaInfo>>()
        var cancelled = 0

        override suspend fun extract(url: String): Outcome<MediaInfo> {
            calls += url
            return try {
                pending.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private val extractor = FakeExtractor()
    private lateinit var vm: HomeViewModel
    private val state get() = vm.state.value

    private val info = MediaInfo(
        id = "aqz-KE-bpKQ",
        sourceUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        title = "Big Buck Bunny",
        site = SourceSite("Youtube", "YouTube"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = HomeViewModel(extractor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    // Entrada

    @Test
    fun startsEmptyAndCannotAnalyze() {
        assertEquals(InputStatus.EMPTY, state.status)
        assertFalse(state.canAnalyze)
    }

    @Test
    fun typingPartialUrlDoesNotShowError() {
        vm.onInputChange("https://")
        assertEquals(InputStatus.TYPING, state.status)
        assertEquals(null, state.invalidReason)
    }

    @Test
    fun validUrlEnablesAnalyze() {
        vm.onInputChange("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertEquals(InputStatus.READY, state.status)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun analyzeWithInvalidInputShowsReasonAndDoesNotCallEngine() = test {
        vm.onInputChange("file:///etc/passwd")
        vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.UNSUPPORTED_SCHEME, state.invalidReason)
        assertTrue(extractor.calls.isEmpty())
    }

    @Test
    fun editingAfterErrorHidesIt() {
        vm.onInputChange("nope")
        vm.onAnalyze()
        vm.onInputChange("nope2")
        assertEquals(InputStatus.TYPING, state.status)
        assertEquals(null, state.invalidReason)
    }

    @Test
    fun pasteExtractsUrlFromSharedText() {
        vm.onPaste("Mirá esto https://www.dailymotion.com/video/x8abc ¡genial!")
        assertEquals("https://www.dailymotion.com/video/x8abc", state.input)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun pasteWithoutUrlShowsError() {
        vm.onPaste("solo texto")
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.NOT_A_URL, state.invalidReason)
    }

    @Test
    fun pasteEmptyClipboardChangesNothing() {
        vm.onInputChange("https://example.com/v")
        vm.onPaste(null)
        vm.onPaste("  ")
        assertEquals("https://example.com/v", state.input)
    }

    // Análisis

    @Test
    fun analyzeShowsLoadingThenResultWithNormalizedUrl() = test {
        vm.onInputChange("WWW.YouTube.com/watch?v=aqz-KE-bpKQ")
        vm.onAnalyze()
        assertEquals(AnalysisState.Loading, state.analysis)
        assertFalse(state.canAnalyze)

        advanceUntilIdle()
        assertEquals(listOf("https://www.youtube.com/watch?v=aqz-KE-bpKQ"), extractor.calls)

        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
        assertEquals(AnalysisState.Loaded(info), state.analysis)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun engineErrorIsShown() = test {
        vm.onInputChange("https://vimeo.com/1")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.LoginRequired()))
        advanceUntilIdle()
        assertEquals(AnalysisState.Failed(MediaError.LoginRequired()), state.analysis)
    }

    @Test
    fun secondTapWhileLoadingIsIgnored() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(1, extractor.calls.size)
    }

    @Test
    fun changingTheLinkCancelsTheRunningAnalysis() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onInputChange("https://example.com/other")
        advanceUntilIdle()
        assertEquals(1, extractor.cancelled)
        assertEquals(AnalysisState.Idle, state.analysis)
    }

    @Test
    fun clearCancelsAndResets() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onClear()
        advanceUntilIdle()
        assertEquals(1, extractor.cancelled)
        assertEquals(HomeUiState(), state)
    }

    @Test
    fun retryAfterErrorRunsAgain() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.Network()))
        advanceUntilIdle()

        extractor.pending = CompletableDeferred()
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
        assertEquals(2, extractor.calls.size)
        assertEquals(AnalysisState.Loaded(info), state.analysis)
    }
}
