package com.fabapps.mediaforge.media.update

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Verificación mínima de firmas OpenPGP separadas (RFC 4880): clave RSA v4 y firma v4 binaria,
 * que es lo que publica yt-dlp (`SHA2-256SUMS.sig`). Usa solo `java.security`; evita sumar
 * Bouncy Castle (~8 MB) para una sola verificación.
 *
 * Lo que no entiende (otros algoritmos, versiones o formatos) lo rechaza: nunca acepta por defecto.
 */
object OpenPgp {

    class RsaKey internal constructor(val fingerprint: String, internal val key: PublicKey)

    /** Lee una clave pública armada y exige que su huella sea [expectedFingerprint] (hex, sin espacios). */
    fun readPublicKey(armored: String, expectedFingerprint: String): RsaKey {
        val packet = packets(dearmor(armored)).firstOrNull { it.tag == TAG_PUBLIC_KEY }
            ?: throw IllegalArgumentException("no public key packet")
        val body = packet.body
        require(body[0].toInt() == 4) { "only v4 keys are supported" }
        require(body[5].toInt() == ALGO_RSA) { "only RSA keys are supported" }
        var offset = 6
        val (n, afterN) = mpi(body, offset)
        offset = afterN
        val (e, _) = mpi(body, offset)

        val fingerprint = sha1Hex(byteArrayOf(0x99.toByte(), (body.size shr 8).toByte(), body.size.toByte()) + body)
        require(fingerprint.equals(expectedFingerprint.replace(" ", ""), ignoreCase = true)) {
            "unexpected key fingerprint $fingerprint"
        }
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
        return RsaKey(fingerprint.uppercase(), key)
    }

    /** `true` solo si [signature] (binaria, no armada) es una firma válida de [data] hecha con [key]. */
    fun verifyDetached(data: ByteArray, signature: ByteArray, key: RsaKey): Boolean = try {
        val packet = packets(signature).firstOrNull { it.tag == TAG_SIGNATURE } ?: return false
        val body = packet.body
        if (body[0].toInt() != 4) return false
        if (body[1].toInt() != SIG_BINARY_DOCUMENT) return false
        if (body[2].toInt() != ALGO_RSA) return false
        val algorithm = HASH_ALGORITHMS[body[3].toInt() and 0xFF] ?: return false

        val hashedLength = u16(body, 4)
        val hashedEnd = 6 + hashedLength
        val unhashedLength = u16(body, hashedEnd)
        val afterUnhashed = hashedEnd + 2 + unhashedLength
        val (s, _) = mpi(body, afterUnhashed + 2) // +2: los 16 bits iniciales del hash

        val trailer = byteArrayOf(0x04, 0xFF.toByte()) + int32(hashedEnd)
        val modulusBytes = ((key.key as java.security.interfaces.RSAPublicKey).modulus.bitLength() + 7) / 8

        Signature.getInstance(algorithm).run {
            initVerify(key.key)
            update(data)
            update(body, 0, hashedEnd)
            update(trailer)
            verify(leftPad(s.toByteArray().dropWhileZero(), modulusBytes))
        }
    } catch (_: RuntimeException) {
        false
    } catch (_: java.security.GeneralSecurityException) {
        false
    }

    // Formato

    private class Packet(val tag: Int, val body: ByteArray)

    private fun packets(data: ByteArray): List<Packet> {
        val result = mutableListOf<Packet>()
        var i = 0
        while (i < data.size) {
            val header = data[i++].toInt() and 0xFF
            require(header and 0x80 != 0) { "invalid packet header" }
            val tag: Int
            val length: Int
            if (header and 0x40 != 0) {
                tag = header and 0x3F
                val first = data[i++].toInt() and 0xFF
                length = when {
                    first < 192 -> first
                    first < 224 -> ((first - 192) shl 8) + (data[i++].toInt() and 0xFF) + 192
                    first == 255 -> int32At(data, i).also { i += 4 }
                    else -> throw IllegalArgumentException("partial lengths are not supported")
                }
            } else {
                tag = (header shr 2) and 0x0F
                length = when (header and 0x03) {
                    0 -> data[i++].toInt() and 0xFF
                    1 -> u16(data, i).also { i += 2 }
                    2 -> int32At(data, i).also { i += 4 }
                    else -> data.size - i
                }
            }
            require(length >= 0 && i + length <= data.size) { "truncated packet" }
            result += Packet(tag, data.copyOfRange(i, i + length))
            i += length
        }
        return result
    }

    private fun dearmor(armored: String): ByteArray {
        val lines = armored.lines().map { it.trim() }
        val begin = lines.indexOfFirst { it.startsWith("-----BEGIN PGP") }
        require(begin >= 0) { "not an armored block" }
        val headersEnd = lines.subList(begin + 1, lines.size).indexOfFirst { it.isEmpty() } + begin + 1
        val base64 = lines.drop(headersEnd + 1)
            .takeWhile { !it.startsWith("=") && !it.startsWith("-----END") }
            .joinToString("")
        return Base64.getDecoder().decode(base64)
    }

    private fun mpi(data: ByteArray, offset: Int): Pair<BigInteger, Int> {
        val bits = u16(data, offset)
        val bytes = (bits + 7) / 8
        val start = offset + 2
        require(start + bytes <= data.size) { "truncated MPI" }
        return BigInteger(1, data.copyOfRange(start, start + bytes)) to start + bytes
    }

    private fun u16(data: ByteArray, offset: Int) =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun int32At(data: ByteArray, offset: Int) = ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun int32(value: Int) =
        byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun ByteArray.dropWhileZero(): ByteArray = dropWhile { it.toInt() == 0 }.toByteArray()

    private fun leftPad(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size >= size) return bytes
        val out = ByteArrayOutputStream(size)
        repeat(size - bytes.size) { out.write(0) }
        out.write(bytes)
        return out.toByteArray()
    }

    private fun sha1Hex(data: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02X".format(it) }

    private const val TAG_SIGNATURE = 2
    private const val TAG_PUBLIC_KEY = 6
    private const val ALGO_RSA = 1
    private const val SIG_BINARY_DOCUMENT = 0x00
    private val HASH_ALGORITHMS = mapOf(8 to "SHA256withRSA", 9 to "SHA384withRSA", 10 to "SHA512withRSA")
}
