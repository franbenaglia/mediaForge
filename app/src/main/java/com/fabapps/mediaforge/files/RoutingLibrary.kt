package com.fabapps.mediaforge.files

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.settings.SettingsStore
import com.fabapps.mediaforge.domain.storage.FolderLibrary
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Aviso de que la carpeta elegida dejó de servir y se guardó en la galería. */
fun interface FolderLostNotice {
    fun show()
}

/**
 * Guarda en la carpeta elegida en Ajustes o, si no hay, en la galería (ADR-019). Si la carpeta ya
 * no se puede usar, vuelve a la galería, borra el ajuste y avisa.
 */
class RoutingLibrary(
    private val gallery: MediaLibrary,
    private val folders: FolderLibrary,
    private val access: DestinationFolders,
    private val settings: SettingsStore,
    private val notice: FolderLostNotice,
    scope: CoroutineScope,
) : MediaLibrary {
    @Volatile private var folder: String? = null

    init {
        scope.launch { settings.settings.collect { folder = it.destinationTree } }
    }

    /** Con una carpeta elegida no hace falta el permiso de escritura de Android 8–9. */
    override fun needsWritePermission(): Boolean {
        val current = folder
        return if (current != null && access.hasAccess(current)) false else gallery.needsWritePermission()
    }

    override suspend fun save(file: OutputFile): Outcome<OutputFile> {
        val current = settings.settings.first().destinationTree ?: return gallery.save(file)
        if (access.hasAccess(current)) {
            folders.save(file, current)?.let { return it }
        }
        settings.update { if (it.destinationTree == current) it.copy(destinationTree = null) else it }
        access.release(current)
        notice.show()
        return gallery.save(file)
    }
}
