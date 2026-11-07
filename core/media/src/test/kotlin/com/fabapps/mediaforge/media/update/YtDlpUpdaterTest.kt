package com.fabapps.mediaforge.media.update

import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.model.MediaError
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class YtDlpUpdaterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val pgp = TestPgp()
    private val base = "https://example.test/releases"
    private val api = "https://api.example.test/latest"

    /** Servidor falso: URL → contenido. Las URLs que no están fallan como error de red. */
    private class FakeHttp : HttpFetcher {
        val responses = mutableMapOf<String, ByteArray>()
        val requested = mutableListOf<String>()
        override suspend fun get(url: String, maxBytes: Long): ByteArray {
            requested += url
            return responses[url] ?: throw IOException("404 $url")
        }
        override suspend fun download(url: String, target: File, maxBytes: Long) {
            target.writeBytes(get(url, maxBytes))
        }
    }

    /** El "binario" es un texto `version:<x>`; si dice `broken`, no arranca. */
    private inner class FakeProbe(var python: String = "3.12.11") : VersionProbe {
        override suspend fun ytDlpVersion(ytDlp: File): String? =
            ytDlp.readText().removePrefix("version:").takeUnless { it == "broken" }
        override suspend fun pythonVersion() = python
        override fun bundledYtDlp() = File(tmp.root, "bundled").apply { writeText("version:2025.11.12") }
    }

    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun set(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private val http = FakeHttp()
    private val probe = FakeProbe()
    private val root by lazy { tmp.newFolder("versions") }
    private val versions by lazy { YtDlpVersions(root, MapStore()) }
    private val updater by lazy { YtDlpUpdater(http, versions, probe, Dispatchers.IO, base, api, pgp.key) }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it)
    }

    /** Publica un release falso firmado con la clave de prueba. */
    private fun publish(
        version: String,
        binary: String = "version:$version",
        latest: Boolean = true,
        spec: String = "",
    ) {
        val bytes = binary.toByteArray()
        val sums = "${"0".repeat(64)}  yt-dlp.exe\n${sha256(bytes)}  yt-dlp\n".toByteArray()
        http.responses["$base/download/$version/SHA2-256SUMS"] = sums
        http.responses["$base/download/$version/SHA2-256SUMS.sig"] = pgp.sign(sums)
        http.responses["$base/download/$version/yt-dlp"] = bytes
        if (latest) {
            http.responses[api] = """{"tag_name": "$version", "name": "yt-dlp $version"}""".toByteArray()
            http.responses["$base/download/$version/_update_spec"] = spec.toByteArray()
        }
    }

    @Test
    fun installsVerifiedVersionAndActivatesIt() = runBlocking {
        publish("2026.10.26")
        assertEquals(Outcome.Success("2026.10.26"), updater.update())
        assertEquals("2026.10.26", versions.active()?.version)
        assertEquals("version:2026.10.26", versions.active()?.file?.readText())
        assertEquals("2026.10.26", updater.installedVersion())
    }

    @Test
    fun alreadyUpToDateDoesNotDownload() = runBlocking {
        publish("2026.10.26")
        updater.update()
        http.requested.clear()
        assertEquals(Outcome.Success("2026.10.26"), updater.update())
        assertTrue(http.requested.none { it.endsWith("/yt-dlp") })
    }

    @Test
    fun checkForUpdateComparesWithBundledVersion() = runBlocking {
        publish("2026.10.26")
        assertEquals(Outcome.Success("2026.10.26"), updater.checkForUpdate())
        updater.update()
        assertEquals(Outcome.Success(null), updater.checkForUpdate())
    }

    @Test
    fun invalidSignatureIsRejectedAndNothingChanges() = runBlocking {
        publish("2026.10.26")
        http.responses["$base/download/2026.10.26/SHA2-256SUMS.sig"] = TestPgp().sign("otra cosa".toByteArray())
        val result = updater.update()
        assertTrue((result as Outcome.Failure).error.detail!!.contains("signature"))
        assertNull(versions.active())
        assertTrue(http.requested.none { it.endsWith("/yt-dlp") })
    }

    @Test
    fun tamperedChecksumListIsRejected() = runBlocking {
        publish("2026.10.26")
        val url = "$base/download/2026.10.26/SHA2-256SUMS"
        http.responses[url] = http.responses.getValue(url) + "\n".toByteArray()
        assertTrue(updater.update() is Outcome.Failure)
        assertNull(versions.active())
    }

    @Test
    fun binaryWithWrongHashIsRejected() = runBlocking {
        publish("2026.10.26")
        http.responses["$base/download/2026.10.26/yt-dlp"] = "version:2026.10.26 + malware".toByteArray()
        val result = updater.update()
        assertTrue((result as Outcome.Failure).error.detail!!.contains("SHA-256"))
        assertNull(versions.active())
        assertEquals(emptyList<String>(), root.list().orEmpty().filter { it != ".staging" })
    }

    @Test
    fun versionThatDoesNotRunIsDiscardedAndPreviousStays() = runBlocking {
        publish("2026.10.26")
        updater.update()
        publish("2026.11.05", binary = "broken")
        val result = updater.update()
        assertTrue((result as Outcome.Failure).error.detail!!.contains("reported"))
        assertEquals("2026.10.26", versions.active()?.version)
        assertTrue(!File(root, "2026.11.05").exists())
    }

    @Test
    fun keepsPreviousVersionForRollback() = runBlocking {
        publish("2026.10.26")
        updater.update()
        publish("2026.11.05")
        updater.update()
        assertEquals("2026.11.05", versions.active()?.version)
        assertEquals("2026.10.26", versions.previous()?.version)

        assertEquals("2026.10.26", versions.rollback()?.version)
        assertEquals("2026.10.26", versions.active()?.version)
        assertTrue(!File(root, "2026.11.05").exists())
    }

    @Test
    fun updateSpecLockLimitsTheTarget() = runBlocking {
        probe.python = "3.10.12"
        publish("2026.10.21", latest = false)
        publish("2026.10.26", spec = "lockV2 yt-dlp/yt-dlp 2026.10.21 zip Python 3\\.10\n")
        assertEquals(Outcome.Success("2026.10.21"), updater.update())
    }

    @Test
    fun networkErrorIsReportedAsNetwork() = runBlocking {
        val result = updater.update()
        assertTrue((result as Outcome.Failure).error is MediaError.Network)
    }

    @Test
    fun maliciousTagIsRejected() = runBlocking {
        http.responses[api] = """{"tag_name": "../../evil"}""".toByteArray()
        assertTrue(updater.update() is Outcome.Failure)
    }

    // Reglas reales de _update_spec (release 2026.10.26).

    private val realSpec = checkNotNull(YtDlpUpdaterTest::class.java.getResource("/update/_update_spec")).readText()

    @Test
    fun realSpecDoesNotLockOurPython() {
        assertEquals("2026.10.26", UpdateSpec.resolveTarget(realSpec, "2026.10.26", "3.12.11"))
    }

    @Test
    fun realSpecLocksOldPythons() {
        assertEquals("2026.10.21", UpdateSpec.resolveTarget(realSpec, "2026.10.26", "3.10.12"))
        assertEquals("2025.10.14", UpdateSpec.resolveTarget(realSpec, "2026.10.26", "3.9.18"))
    }

    @Test
    fun versionComparison() {
        assertTrue(UpdateSpec.compareVersions("2026.10.26", "2026.10.21.202512") > 0)
        assertTrue(UpdateSpec.compareVersions("2026.9.1", "2026.10.1") < 0)
        assertEquals(0, UpdateSpec.compareVersions("2026.10.26", "2026.10.26"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun versionsRejectPathTraversal() {
        versions.install("../evil", File(tmp.root, "x").apply { writeText("x") })
    }
}
