package com.fabapps.mediaforge.media.engine

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object NotInitialized : EngineState

    /** Descomprimiendo paquetes. La primera vez puede tardar varios segundos. */
    data object Initializing : EngineState

    data object Ready : EngineState

    data class Failed(val error: MediaError) : EngineState
}

/** Parte nativa del motor, aislada para poder probar [EngineInitializer] en la JVM. */
interface NativeEngine {
    /** Bytes de los paquetes comprimidos que vienen en el APK. */
    fun compressedPackageBytes(): Long

    /** `true` si los paquetes de esta versión del APK ya están descomprimidos. */
    fun isExtracted(): Boolean

    fun freeBytes(): Long

    /** Descomprime si hace falta e inicializa. Bloqueante. */
    fun initialize()
}

/**
 * Prepara el motor una sola vez, fuera del hilo principal, verificando antes el espacio libre.
 * Es seguro llamar a [ensureReady] desde varios lugares a la vez.
 */
@Singleton
class EngineInitializer @Inject constructor(
    private val native: NativeEngine,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<EngineState>(EngineState.NotInitialized)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    suspend fun ensureReady(): Outcome<Unit> = mutex.withLock {
        if (_state.value == EngineState.Ready) return@withLock Outcome.Success(Unit)
        withContext(io) {
            val spaceError = EngineSpace.check(
                compressedBytes = native.compressedPackageBytes(),
                freeBytes = native.freeBytes(),
                alreadyExtracted = native.isExtracted(),
            )
            if (spaceError != null) return@withContext fail(spaceError)

            _state.value = EngineState.Initializing
            try {
                native.initialize()
                _state.value = EngineState.Ready
                Outcome.Success(Unit)
            } catch (e: CancellationException) {
                _state.value = EngineState.NotInitialized
                throw e
            } catch (e: Exception) {
                fail(MediaError.Unknown(detail = e.message))
            }
        }
    }

    private fun fail(error: MediaError): Outcome<Unit> {
        _state.value = EngineState.Failed(error)
        return Outcome.Failure(error)
    }
}
