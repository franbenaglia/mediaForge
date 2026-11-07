package com.fabapps.mediaforge.media.update

import java.io.File

/** Clave-valor persistente. En Android es SharedPreferences; en pruebas, un mapa. */
interface KeyValueStore {
    operator fun get(key: String): String?

    operator fun set(key: String, value: String?)
}

/**
 * Versiones de yt-dlp descargadas por la app, cada una en su carpeta: `<root>/<versión>/yt-dlp`.
 * Se conservan la activa y la anterior (para volver atrás); la del APK nunca se toca.
 */
class YtDlpVersions(private val root: File, private val store: KeyValueStore) {

    data class Installed(val version: String, val file: File)

    fun active(): Installed? = installed(store[KEY_ACTIVE])

    fun previous(): Installed? = installed(store[KEY_PREVIOUS])

    /** Archivo temporal para una descarga en curso. Se borra lo que haya quedado de un intento anterior. */
    fun newStagingFile(): File {
        val staging = File(root, STAGING).apply {
            deleteRecursively()
            mkdirs()
        }
        return File(staging, BINARY)
    }

    /** Mueve el archivo verificado a su carpeta definitiva. Todavía no lo activa. */
    fun install(version: String, staged: File): File {
        requireSafe(version)
        val dir = File(root, version).apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, BINARY)
        check(staged.renameTo(target)) { "could not move $staged to $target" }
        return target
    }

    /** La nueva pasa a ser la activa y la activa, la anterior. Se borran las demás. */
    fun activate(version: String) {
        requireSafe(version)
        check(File(File(root, version), BINARY).isFile) { "version $version is not installed" }
        val current = store[KEY_ACTIVE]
        if (current != version) store[KEY_PREVIOUS] = current
        store[KEY_ACTIVE] = version
        prune()
    }

    /** Descarta una versión instalada que no pasó la prueba. */
    fun discard(version: String) {
        requireSafe(version)
        if (version != store[KEY_ACTIVE] && version != store[KEY_PREVIOUS]) {
            File(root, version).deleteRecursively()
        }
    }

    /** Vuelve a la anterior (por ejemplo, si la nueva rompe un sitio). */
    fun rollback(): Installed? {
        val previous = previous() ?: return null
        store[KEY_PREVIOUS] = null
        store[KEY_ACTIVE] = previous.version
        prune()
        return previous
    }

    private fun installed(version: String?): Installed? {
        if (version == null || !isSafe(version)) return null
        val file = File(File(root, version), BINARY)
        return if (file.isFile) Installed(version, file) else null
    }

    private fun prune() {
        val keep = setOfNotNull(store[KEY_ACTIVE], store[KEY_PREVIOUS])
        root.listFiles().orEmpty().filter { it.isDirectory && it.name !in keep }.forEach { it.deleteRecursively() }
    }

    /** El nombre de la versión forma una ruta: solo dígitos y puntos (evita `../`). */
    private fun isSafe(version: String) = version.isNotEmpty() &&
        version.all { it.isDigit() || it == '.' } &&
        !version.startsWith('.')

    private fun requireSafe(version: String) = require(isSafe(version)) { "invalid version: $version" }

    private companion object {
        const val KEY_ACTIVE = "ytdlp_active"
        const val KEY_PREVIOUS = "ytdlp_previous"
        const val STAGING = ".staging"
        const val BINARY = "yt-dlp"
    }
}
