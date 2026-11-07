package com.fabapps.mediaforge.domain.storage

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.OutputFile

/** Biblioteca pública del usuario (galería / música). */
interface MediaLibrary {
    /**
     * Copia un archivo privado terminado a la carpeta pública que corresponde a su tipo (video en
     * `Movies/MediaForge`, audio en `Music/MediaForge`) con un nombre que no pisa nada. Si sale bien,
     * borra el archivo privado. Devuelve el archivo guardado, con una URI de contenido en `location`.
     */
    suspend fun save(file: OutputFile): Outcome<OutputFile>

    /** `true` si guardar exige pedir un permiso antes (solo Android 9 o anterior). */
    fun needsWritePermission(): Boolean
}
