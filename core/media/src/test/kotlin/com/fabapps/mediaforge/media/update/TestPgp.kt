package com.fabapps.mediaforge.media.update

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** Firmante OpenPGP v4 mínimo, solo para pruebas: genera una clave RSA y firmas separadas. */
internal class TestPgp {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = pair.public as RSAPublicKey

    private val keyBody: ByteArray = ByteArrayOutputStream().apply {
        write(4)
        write(byteArrayOf(0x65, 0x00, 0x00, 0x00)) // fecha de creación
        write(1) // RSA
        write(mpi(publicKey.modulus))
        write(mpi(publicKey.publicExponent))
    }.toByteArray()

    val fingerprint: String = java.security.MessageDigest.getInstance("SHA-1")
        .digest(byteArrayOf(0x99.toByte(), (keyBody.size shr 8).toByte(), keyBody.size.toByte()) + keyBody)
        .joinToString("") { "%02X".format(it) }

    val armoredPublicKey: String = buildString {
        appendLine("-----BEGIN PGP PUBLIC KEY BLOCK-----")
        appendLine()
        Base64.getEncoder().encodeToString(packet(6, keyBody)).chunked(64).forEach(::appendLine)
        appendLine("=AAAA")
        appendLine("-----END PGP PUBLIC KEY BLOCK-----")
    }

    val key: OpenPgp.RsaKey get() = OpenPgp.readPublicKey(armoredPublicKey, fingerprint)

    fun sign(data: ByteArray): ByteArray {
        val hashed = byteArrayOf(
            4, 0x00, 1, 10, // v4, documento binario, RSA, SHA-512
            0, 6, // largo de los subpaquetes con hash
            5, 2, 0x65, 0x00, 0x00, 0x01, // subpaquete: fecha de creación de la firma
        )
        val trailer = byteArrayOf(4, 0xFF.toByte(), 0, 0, 0, hashed.size.toByte())
        val signer = Signature.getInstance("SHA512withRSA").apply {
            initSign(pair.private as RSAPrivateKey)
            update(data)
            update(hashed)
            update(trailer)
        }
        val s = BigInteger(1, signer.sign())
        val body = ByteArrayOutputStream().apply {
            write(hashed)
            write(byteArrayOf(0, 0)) // sin subpaquetes sin hash
            write(byteArrayOf(0, 0)) // 16 bits iniciales del hash (no se usan para verificar)
            write(mpi(s))
        }.toByteArray()
        return packet(2, body)
    }

    private fun mpi(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray()
        val bits = value.bitLength()
        return byteArrayOf((bits shr 8).toByte(), bits.toByte()) + bytes
    }

    /** Paquete en formato nuevo con largo de dos bytes (192..8383). */
    private fun packet(tag: Int, body: ByteArray): ByteArray {
        val length = body.size - 192
        return byteArrayOf((0xC0 or tag).toByte(), ((length shr 8) + 192).toByte(), length.toByte()) + body
    }
}
