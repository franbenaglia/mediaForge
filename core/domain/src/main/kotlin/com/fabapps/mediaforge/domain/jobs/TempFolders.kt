package com.fabapps.mediaforge.domain.jobs

/**
 * Carpetas privadas de trabajos (`jobs/<id>`): cada descarga o conversión trabaja en la suya y la
 * borra al terminar. Si el proceso muere a mitad, la carpeta queda.
 */
object TempFolders {
    /** Se pueden borrar las carpetas de trabajos que no están corriendo. */
    fun removable(folders: Collection<String>, running: Set<String>): List<String> =
        folders.filter { it !in running && it.isNotBlank() && it != "." && it != ".." }
}
