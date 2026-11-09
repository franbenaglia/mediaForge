package com.fabapps.mediaforge.files

import android.content.Context
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.jobs.TempFolders
import com.fabapps.mediaforge.media.engine.noBackupDir
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Restos de trabajos que no terminaron (el proceso murió a mitad), en las carpetas privadas (T9.9). */
interface TempStorage {
    /** Lo que se puede liberar ahora. */
    suspend fun removableBytes(): Long

    /** Borra lo que no usa un trabajo en curso; devuelve los bytes liberados. */
    suspend fun clean(): Long
}

@Singleton
class JobFoldersTempStorage @Inject constructor(
    @ApplicationContext context: Context,
    private val scheduler: JobScheduler,
) : TempStorage {
    /** La misma carpeta que usan el descargador y el conversor (MediaModule). */
    private val root = File(context.noBackupDir(), "mediaforge/jobs")

    override suspend fun removableBytes(): Long = scheduler.withJobsFrozen { running ->
        withContext(Dispatchers.IO) { removable(running).sumOf(::size) }
    }

    override suspend fun clean(): Long = scheduler.withJobsFrozen { running ->
        withContext(Dispatchers.IO) {
            removable(running).sumOf { folder -> size(folder).also { folder.deleteRecursively() } }
        }
    }

    private fun removable(running: Set<String>): List<File> {
        val folders = root.listFiles()?.filter { it.isDirectory }.orEmpty().associateBy { it.name }
        return TempFolders.removable(folders.keys, running).mapNotNull { folders[it] }
    }

    private fun size(folder: File): Long = folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
