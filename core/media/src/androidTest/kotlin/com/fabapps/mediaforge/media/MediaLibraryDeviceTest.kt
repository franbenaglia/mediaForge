package com.fabapps.mediaforge.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.media.storage.MediaStoreLibrary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guardado en la galería en un teléfono real (T4.6). Borra lo que crea. */
@RunWith(AndroidJUnit4::class)
class MediaLibraryDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = MediaStoreLibrary(context, Dispatchers.IO)
    private val created = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        created.forEach { context.contentResolver.delete(it, null, null) }
    }

    private fun privateFile(
        content: String,
        extension: String = "mp4",
        mime: String = "video/mp4",
        name: String = "MediaForge test: video",
    ): OutputFile {
        val dir = File(context.noBackupFilesDir, "mediaforge/jobs/device-test").apply { mkdirs() }
        val file = File(dir, "media.$extension").apply { writeText(content) }
        return OutputFile(file.absolutePath, "$name.$extension", mime, file.length())
    }

    private fun save(file: OutputFile): OutputFile {
        val result = runBlocking { library.save(file) }
        val saved = (result as Outcome.Success).value
        created += Uri.parse(saved.location)
        return saved
    }

    @Test
    fun savesIntoMoviesWithUniqueNamesAndRemovesThePrivateCopy() {
        val first = privateFile("first")
        val saved = save(first)
        assertEquals("MediaForge test video.mp4", saved.displayName)
        assertFalse(File(first.location).exists())
        assertFalse(File(first.location).parentFile!!.exists())

        val second = save(privateFile("second"))
        assertEquals("MediaForge test video (1).mp4", second.displayName)

        val uri = Uri.parse(saved.location)
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING),
            null,
            null,
            null,
        )!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("Movies/MediaForge/", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        val content = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals("first", content)
    }

    /** T5.3: MediaStore acepta cada formato de audio sin cambiar el nombre ni la extensión. */
    @Test
    fun savesEveryAudioFormatIntoMusic() {
        for (format in AudioFormat.entries) {
            val saved = save(privateFile("audio", format.extension, format.mimeType, "MediaForge test audio"))
            assertEquals("MediaForge test audio.${format.extension}", saved.displayName)
            context.contentResolver.query(
                Uri.parse(saved.location),
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null,
            )!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("Music/MediaForge/", it.getString(0))
            }
        }
    }
}
