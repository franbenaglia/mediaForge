package com.fabapps.mediaforge.media.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Con los archivos reales del release 2026.10.26 de yt-dlp, verificados antes con gpg. */
class OpenPgpTest {
    private fun resource(name: String) = checkNotNull(OpenPgpTest::class.java.getResource("/update/$name")).readBytes()

    private val sums = resource("SHA2-256SUMS")
    private val signature = resource("SHA2-256SUMS.sig")
    private val key = OpenPgp.readPublicKey(YtDlpSigningKey.ARMORED, YtDlpSigningKey.FINGERPRINT)

    @Test
    fun readsPinnedKey() {
        assertEquals(YtDlpSigningKey.FINGERPRINT, key.fingerprint)
    }

    @Test
    fun acceptsRealSignature() {
        assertTrue(OpenPgp.verifyDetached(sums, signature, key))
    }

    @Test
    fun rejectsModifiedChecksumFile() {
        val tampered = sums.copyOf().also {
            it[0] =
                if (it[0] == 'a'.code.toByte()) 'b'.code.toByte() else 'a'.code.toByte()
        }
        assertFalse(OpenPgp.verifyDetached(tampered, signature, key))
    }

    @Test
    fun rejectsAppendedLine() {
        val tampered = sums + "0000000000000000000000000000000000000000000000000000000000000000  evil\n".toByteArray()
        assertFalse(OpenPgp.verifyDetached(tampered, signature, key))
    }

    @Test
    fun rejectsModifiedSignature() {
        val tampered = signature.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 0x01).toByte() }
        assertFalse(OpenPgp.verifyDetached(sums, tampered, key))
    }

    @Test
    fun rejectsGarbageSignatureWithoutCrashing() {
        assertFalse(OpenPgp.verifyDetached(sums, ByteArray(0), key))
        assertFalse(OpenPgp.verifyDetached(sums, "not a signature".toByteArray(), key))
        assertFalse(OpenPgp.verifyDetached(sums, byteArrayOf(0x89.toByte(), 0x02, 0x33), key))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsKeyWithOtherFingerprint() {
        OpenPgp.readPublicKey(YtDlpSigningKey.ARMORED, "0000000000000000000000000000000000000000")
    }
}
