package com.fabapps.mediaforge.data.history

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fabapps.mediaforge.data.jobs.MediaForgeDatabase
import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryKind
import com.fabapps.mediaforge.domain.model.OutputFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La base de la versión 1 (Etapa 7) pasa a la 2 sin perder trabajos y con la tabla `history`. */
@RunWith(AndroidJUnit4::class)
class MigrationDeviceTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MediaForgeDatabase::class.java)

    @Test
    fun version1To2KeepsJobsAndAddsHistory() = runTest {
        helper.createDatabase(name, 1).apply {
            execSQL(
                """
                INSERT INTO jobs (id, requestJson, requestVersion, title, status, attempts, createdAt, updatedAt)
                VALUES ('j1', '{}', 1, 'Viejo', 'COMPLETED', 1, 1, 2)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, MediaForgeDatabase::class.java, name).build()
        try {
            assertEquals("Viejo", database.jobs().get("j1")?.title)
            val store = RoomHistoryStore(database.history())
            val entry = HistoryEntry(
                id = "h1",
                kind = HistoryKind.AUDIO,
                title = "Tema",
                format = "MP3",
                file = OutputFile("content://media/2", "Tema.mp3", "audio/mpeg", 7),
                completedAt = 5,
            )
            store.put(entry)
            store.put(entry.copy(id = "h2", completedAt = 9))
            assertEquals(listOf("h2", "h1"), store.observeAll().first().map { it.id })
            store.delete("h1")
            assertEquals(listOf("h2"), store.observeAll().first().map { it.id })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
