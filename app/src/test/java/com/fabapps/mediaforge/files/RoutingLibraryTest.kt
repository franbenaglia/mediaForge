package com.fabapps.mediaforge.files

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.storage.FolderLibrary
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.testing.FakeSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingLibraryTest {
    private val folder = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    private val file = OutputFile("/private/a.mp3", "a.mp3", "audio/mpeg", 3)

    private class Gallery : MediaLibrary {
        val saved = mutableListOf<OutputFile>()
        var needsPermission = true
        override suspend fun save(file: OutputFile): Outcome<OutputFile> {
            saved += file
            return Outcome.Success(file.copy(location = "content://media/1"))
        }
        override fun needsWritePermission() = needsPermission
    }

    private class Folders : FolderLibrary {
        var usable = true
        val saved = mutableListOf<String>()
        override suspend fun save(file: OutputFile, folder: String): Outcome<OutputFile>? {
            if (!usable) return null
            saved += folder
            return Outcome.Success(file.copy(location = "$folder/document/a.mp3"))
        }
    }

    private class Access : DestinationFolders {
        val granted = mutableSetOf<String>()
        override fun hasAccess(folder: String) = folder in granted
        override fun take(folder: String) = granted.add(folder)
        override fun release(folder: String) {
            granted -= folder
        }
        override suspend fun name(folder: String): String? = null
    }

    private val gallery = Gallery()
    private val folders = Folders()
    private val access = Access()
    private val settings = FakeSettingsStore()
    private var notices = 0

    private fun TestScope.library() = RoutingLibrary(gallery, folders, access, settings, { notices++ }, backgroundScope)
        .also { runCurrent() }

    @Test
    fun withoutAFolderItSavesToTheGallery() = runTest {
        val saved = library().save(file)
        assertEquals("content://media/1", (saved as Outcome.Success).value.location)
        assertTrue(folders.saved.isEmpty())
        assertTrue(library().needsWritePermission())
    }

    @Test
    fun aChosenFolderIsUsedAndNeedsNoStoragePermission() = runTest {
        settings.settings.value = Settings(destinationTree = folder)
        access.granted += folder
        val library = library()
        library.save(file)
        assertEquals(listOf(folder), folders.saved)
        assertTrue(gallery.saved.isEmpty())
        assertFalse(library.needsWritePermission())
        assertEquals(0, notices)
    }

    @Test
    fun lostPermissionFallsBackToTheGalleryAndWarns() = runTest {
        settings.settings.value = Settings(destinationTree = folder)
        library().save(file)
        assertEquals(listOf(file), gallery.saved)
        assertNull(settings.settings.value.destinationTree)
        assertEquals(1, notices)
    }

    @Test
    fun aDeletedFolderFallsBackAndReleasesThePermission() = runTest {
        settings.settings.value = Settings(destinationTree = folder)
        access.granted += folder
        folders.usable = false
        library().save(file)
        assertEquals(listOf(file), gallery.saved)
        assertTrue(access.granted.isEmpty())
        assertNull(settings.settings.value.destinationTree)
        assertEquals(1, notices)
    }
}
