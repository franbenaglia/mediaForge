package com.fabapps.mediaforge.ui.history

import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryFilter
import com.fabapps.mediaforge.domain.history.HistoryKind
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.files.DeleteConsent
import com.fabapps.mediaforge.files.DeleteResult
import com.fabapps.mediaforge.files.SavedFiles
import com.fabapps.mediaforge.share.ShareInbox
import com.fabapps.mediaforge.share.SharedContent
import com.fabapps.mediaforge.testing.InMemoryHistoryStore
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeFiles : SavedFiles {
        val present = mutableSetOf<String>()
        val deleted = mutableListOf<String>()
        var results = ArrayDeque<DeleteResult>()

        override suspend fun exists(location: String) = location in present

        override suspend fun delete(location: String): DeleteResult {
            val result = results.removeFirstOrNull() ?: DeleteResult.Deleted
            if (result == DeleteResult.Deleted) {
                deleted += location
                present -= location
            }
            return result
        }
    }

    private val consent = object : DeleteConsent {}
    private val store = InMemoryHistoryStore()
    private val files = FakeFiles()
    private val inbox = ShareInbox()
    private lateinit var vm: HistoryViewModel
    private val state get() = vm.state.value

    private fun entry(
        id: String,
        kind: HistoryKind,
        title: String,
        at: Long,
        url: String? = "https://example.com/$id",
    ) = HistoryEntry(
        id = id,
        kind = kind,
        title = title,
        sourceName = if (kind == HistoryKind.CONVERSION) null else "YouTube",
        sourceUrl = if (kind == HistoryKind.CONVERSION) null else url,
        format = "MP4",
        file = OutputFile("content://media/$id", "$title.mp4", "video/mp4", 10),
        completedAt = at,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store.entries.value = listOf(
            entry("v", HistoryKind.VIDEO, "Canción", at = 1),
            entry("a", HistoryKind.AUDIO, "Podcast", at = 2),
            entry("c", HistoryKind.CONVERSION, "Vacaciones", at = 3),
        ).associateBy { it.id }
        files.present += listOf("content://media/v", "content://media/a", "content://media/c")
        vm = HistoryViewModel(store, files, inbox)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        advanceUntilIdle()
        block()
    }

    private val ids get() = state.items.map { it.id }

    @Test
    fun listsNewestFirst() = test {
        assertFalse(state.loading)
        assertFalse(state.isEmpty)
        assertEquals(listOf("c", "a", "v"), ids)
    }

    @Test
    fun emptyHistoryIsNotTheSameAsNoResults() = test {
        vm.onQueryChange("nada")
        advanceUntilIdle()
        assertTrue(state.items.isEmpty())
        assertFalse(state.isEmpty)

        store.entries.value = emptyMap()
        advanceUntilIdle()
        assertTrue(state.isEmpty)
    }

    @Test
    fun searchAndFilter() = test {
        vm.onFilterChange(HistoryFilter.AUDIO)
        advanceUntilIdle()
        assertEquals(listOf("a"), ids)
        vm.onFilterChange(HistoryFilter.ALL)
        vm.onQueryChange("cancion")
        advanceUntilIdle()
        assertEquals(listOf("v"), ids)
    }

    @Test
    fun missingFilesAreMarkedAndRecheckedOnResume() = test {
        assertTrue(state.items.none { it.missing })
        files.present -= "content://media/a"
        vm.onResume()
        advanceUntilIdle()
        assertEquals(listOf("a"), state.items.filter { it.missing }.map { it.id })
    }

    @Test
    fun redownloadSendsTheLinkToHome() = test {
        assertTrue(vm.onRedownload("v"))
        assertEquals(SharedContent("https://example.com/v", null), inbox.pending.value)
        assertFalse(vm.onRedownload("c"))
    }

    @Test
    fun removingKeepsTheFile() = test {
        vm.onRemove("v")
        advanceUntilIdle()
        assertEquals(listOf("c", "a"), ids)
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun deletingAsksFirstThenDeletesFileAndEntry() = test {
        vm.onDeleteFile("a")
        advanceUntilIdle()
        assertEquals("a", state.confirmDelete?.id)

        vm.onDismissDelete()
        advanceUntilIdle()
        assertNull(state.confirmDelete)
        assertTrue(files.deleted.isEmpty())

        vm.onDeleteFile("a")
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertEquals(listOf("content://media/a"), files.deleted)
        assertEquals(listOf("c", "v"), ids)
        assertEquals(HistoryMessage.FILE_DELETED, state.message)
        vm.onMessageShown()
        advanceUntilIdle()
        assertNull(state.message)
    }

    @Test
    fun systemConsentThenRetry() = test {
        files.results.addAll(listOf(DeleteResult.NeedsConsent(consent), DeleteResult.Deleted))
        vm.onDeleteFile("v")
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertEquals(PendingConsent("v", consent), state.consent)

        vm.onConsentShown()
        advanceUntilIdle()
        assertTrue(state.consent!!.shown)

        vm.onConsentResult(granted = true)
        advanceUntilIdle()
        assertNull(state.consent)
        assertEquals(listOf("c", "a"), ids)
    }

    @Test
    fun deniedConsentKeepsEverything() = test {
        files.results.add(DeleteResult.NeedsConsent(consent))
        vm.onDeleteFile("v")
        vm.onConfirmDelete()
        advanceUntilIdle()
        vm.onConsentResult(granted = false)
        advanceUntilIdle()
        assertNull(state.consent)
        assertEquals(listOf("c", "a", "v"), ids)
        assertNull(state.message)
    }

    @Test
    fun failedDeleteIsReported() = test {
        files.results.add(DeleteResult.Failed)
        vm.onDeleteFile("v")
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertEquals(HistoryMessage.DELETE_FAILED, state.message)
        assertEquals(3, state.items.size)
    }
}
