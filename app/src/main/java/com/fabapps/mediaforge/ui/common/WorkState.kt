package com.fabapps.mediaforge.ui.common

import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile

/** Estado de una tarea con resultado en la galería (descarga o conversión). */
sealed interface WorkState {
    val isActive: Boolean get() = this is Running || this is Saving

    data object None : WorkState

    /** Estado del motor: pendiente, bajando, uniendo pistas o convirtiendo. */
    data class Running(val job: JobState) : WorkState

    /** Copiando a la galería. */
    data object Saving : WorkState

    data class Saved(val file: OutputFile) : WorkState

    data class Failed(val error: MediaError) : WorkState

    data object Cancelled : WorkState

    /** Android 8–9: el usuario no dio permiso de escritura. */
    data object PermissionDenied : WorkState
}
