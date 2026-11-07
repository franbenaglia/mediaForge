package com.fabapps.mediaforge.media.engine

import com.fabapps.mediaforge.domain.model.MediaError

/**
 * Espacio necesario para descomprimir los paquetes nativos en el primer arranque.
 *
 * Medido en la Etapa 0 (FEASIBILITY §7): 51,6 MB comprimidos (Python + FFmpeg) ocupan
 * ~243 MB descomprimidos, una relación de ~4,7. Se usa 5 más un margen.
 */
internal object EngineSpace {
    const val EXPANSION_FACTOR = 5L
    const val MARGIN_BYTES = 50L * 1000 * 1000

    fun requiredBytes(compressedBytes: Long): Long {
        require(compressedBytes >= 0) { "negative size: $compressedBytes" }
        return compressedBytes * EXPANSION_FACTOR + MARGIN_BYTES
    }

    /** `null` si alcanza el espacio (o si ya está descomprimido); el error a mostrar si no. */
    fun check(compressedBytes: Long, freeBytes: Long, alreadyExtracted: Boolean): MediaError.InsufficientStorage? {
        if (alreadyExtracted) return null
        val required = requiredBytes(compressedBytes)
        return if (freeBytes >= required) {
            null
        } else {
            MediaError.InsufficientStorage(requiredBytes = required, availableBytes = freeBytes)
        }
    }
}
