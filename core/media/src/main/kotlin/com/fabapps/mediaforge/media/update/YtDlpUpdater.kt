package com.fabapps.mediaforge.media.update

import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ejecuta un yt-dlp y devuelve su versión, o `null` si no arranca. También informa el Python. */
interface VersionProbe {
    suspend fun ytDlpVersion(ytDlp: File): String?

    /** Por ejemplo `3.12.11`. */
    suspend fun pythonVersion(): String?

    /** El yt-dlp que vino con el APK. */
    fun bundledYtDlp(): File
}

/**
 * Actualiza yt-dlp sin reinstalar el APK, solo con versiones verificadas (FEASIBILITY §8):
 *
 * 1. Última versión publicada, limitada por `_update_spec` según nuestro Python.
 * 2. `SHA2-256SUMS` con firma OpenPGP válida de la clave fijada de yt-dlp.
 * 3. Descarga del zipapp con el SHA-256 de esa lista.
 * 4. La versión nueva tiene que responder `--version` con la versión esperada.
 * 5. Recién entonces se activa; la anterior se conserva.
 *
 * Si algo falla, la versión activa no cambia.
 */
class YtDlpUpdater(
    private val http: HttpFetcher,
    private val versions: YtDlpVersions,
    private val probe: VersionProbe,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://github.com/${UpdateSpec.REPO}/releases",
    private val apiUrl: String = "https://api.github.com/repos/${UpdateSpec.REPO}/releases/latest",
    /** Clave de firma; las pruebas usan una propia. */
    signingKey: OpenPgp.RsaKey? = null,
) : ExtractorEngine {

    private val mutex = Mutex()
    private val key by lazy {
        signingKey ?: OpenPgp.readPublicKey(YtDlpSigningKey.ARMORED, YtDlpSigningKey.FINGERPRINT)
    }
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun installedVersion(): String? = withContext(io) {
        versions.active()?.version ?: probe.ytDlpVersion(probe.bundledYtDlp())
    }

    override suspend fun checkForUpdate(): Outcome<String?> = withContext(io) {
        guarded {
            val target = resolveTarget()
            val installed = installedVersion()
            if (installed != null && UpdateSpec.compareVersions(target, installed) <= 0) null else target
        }
    }

    override suspend fun update(): Outcome<String> = mutex.withLock {
        withContext(io) {
            guarded {
                val target = resolveTarget()
                val installed = installedVersion()
                if (installed != null && UpdateSpec.compareVersions(target, installed) <= 0) return@guarded installed
                install(target)
            }
        }
    }

    private suspend fun resolveTarget(): String {
        val latest = json.parseToJsonElement(http.get(apiUrl, MAX_TEXT_BYTES).decodeToString())
            .jsonObject["tag_name"]?.jsonPrimitive?.content
            ?: throw UpdateException("release without tag_name")
        requireVersion(latest)
        val python = probe.pythonVersion() ?: throw UpdateException("python did not start")
        val spec = http.get("$baseUrl/download/$latest/_update_spec", MAX_TEXT_BYTES).decodeToString()
        return UpdateSpec.resolveTarget(spec, latest, python).also(::requireVersion)
    }

    private suspend fun install(version: String): String {
        val sums = http.get("$baseUrl/download/$version/SHA2-256SUMS", MAX_TEXT_BYTES)
        val signature = http.get("$baseUrl/download/$version/SHA2-256SUMS.sig", MAX_TEXT_BYTES)
        if (!OpenPgp.verifyDetached(sums, signature, key)) throw UpdateException("invalid signature for $version")
        val expected = expectedHash(sums.decodeToString())

        val staged = versions.newStagingFile()
        try {
            http.download("$baseUrl/download/$version/$ASSET", staged, MAX_BINARY_BYTES)
            val actual = sha256(staged)
            if (!actual.equals(expected, ignoreCase = true)) throw UpdateException("SHA-256 mismatch for $version")

            val file = versions.install(version, staged)
            val reported = probe.ytDlpVersion(file)
            if (reported != version) {
                versions.discard(version)
                throw UpdateException("new yt-dlp reported '$reported' instead of $version")
            }
            versions.activate(version)
            return version
        } finally {
            staged.delete()
        }
    }

    /** Línea `<sha256>  yt-dlp` exacta: no se acepta `yt-dlp.exe` ni otras variantes. */
    private fun expectedHash(sums: String): String = sums.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { it.size == 2 && it[1].removePrefix("*") == ASSET }
        ?.first()
        ?.takeIf { it.length == 64 && it.all(Char::isLetterOrDigit) }
        ?: throw UpdateException("no SHA-256 for $ASSET")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireVersion(version: String) {
        if (version.isEmpty() ||
            !version.all { it.isDigit() || it == '.' }
        ) {
            throw UpdateException("invalid version '$version'")
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: UpdateException) {
        Outcome.Failure(MediaError.Unknown(detail = e.message))
    } catch (e: IOException) {
        Outcome.Failure(MediaError.Network(detail = e.message))
    } catch (e: RuntimeException) {
        Outcome.Failure(MediaError.Unknown(detail = e.message))
    }

    private class UpdateException(message: String) : Exception(message)

    companion object {
        const val ASSET = "yt-dlp"
        const val MAX_TEXT_BYTES = 256L * 1024
        const val MAX_BINARY_BYTES = 50L * 1024 * 1024
    }
}
