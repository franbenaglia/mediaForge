package com.fabapps.mediaforge.ui.home

import com.fabapps.mediaforge.domain.url.UrlCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    private val vm = HomeViewModel()
    private val state get() = vm.state.value

    @Test
    fun startsEmptyAndCannotAnalyze() {
        assertEquals(InputStatus.EMPTY, state.status)
        assertFalse(state.canAnalyze)
    }

    @Test
    fun typingPartialUrlDoesNotShowError() {
        vm.onInputChange("https://")
        assertEquals(InputStatus.TYPING, state.status)
        assertNull(state.invalidReason)
        assertFalse(state.canAnalyze)
    }

    @Test
    fun validUrlEnablesAnalyze() {
        vm.onInputChange("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertEquals(InputStatus.READY, state.status)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun clearingTextGoesBackToEmpty() {
        vm.onInputChange("https://example.com/v")
        vm.onInputChange("   ")
        assertEquals(InputStatus.EMPTY, state.status)
    }

    @Test
    fun analyzeWithInvalidInputShowsReasonAndDoesNotStart() {
        vm.onInputChange("file:///etc/passwd")
        vm.onAnalyze()
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.UNSUPPORTED_SCHEME, state.invalidReason)
        assertNull(state.pendingAnalysis)
    }

    @Test
    fun editingAfterErrorHidesIt() {
        vm.onInputChange("nope")
        vm.onAnalyze()
        vm.onInputChange("nope2")
        assertEquals(InputStatus.TYPING, state.status)
        assertNull(state.invalidReason)
    }

    @Test
    fun analyzeWithValidInputPublishesNormalizedUrlOnce() {
        vm.onInputChange("WWW.YouTube.com/watch?v=x")
        vm.onAnalyze()
        assertEquals("https://www.youtube.com/watch?v=x", state.pendingAnalysis)
        assertEquals("https://www.youtube.com/watch?v=x", state.input)
        vm.onAnalysisHandled()
        assertNull(state.pendingAnalysis)
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
        assertEquals("solo texto", state.input)
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

    @Test
    fun clearResetsEverything() {
        vm.onPaste("nada")
        vm.onClear()
        assertEquals(HomeUiState(), state)
    }
}
