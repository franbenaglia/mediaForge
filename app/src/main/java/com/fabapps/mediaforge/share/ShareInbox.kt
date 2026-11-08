package com.fabapps.mediaforge.share

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Lo que otra app compartió con MediaForge, tal cual llegó. */
data class SharedContent(val text: String?, val subject: String?)

/**
 * Guarda el último contenido compartido hasta que Inicio lo toma (ADR-018). Así llega aunque
 * la pantalla todavía no exista. Vive en memoria: si el proceso muere antes, se pierde.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    fun offer(content: SharedContent) {
        _pending.value = content
    }

    /** Devuelve lo pendiente y lo borra: cada contenido se usa una sola vez. */
    fun take(): SharedContent? = _pending.getAndUpdate { null }
}
