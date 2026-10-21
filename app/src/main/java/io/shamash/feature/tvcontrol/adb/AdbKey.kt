package io.shamash.feature.tvcontrol.adb

import io.shamash.data.Logger
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Shamash's RSA-2048 ADB key: created once, kept in the app's private files, never logged. The TV remembers it after
 * the user accepts "Allow debugging?" once. SPEC: TVC-01
 */
class AdbKey(private val pair: KeyPair) {

    /** Signs adbd's 20-byte token the way adbd checks it: PKCS#1 v1.5 with SHA-1's DigestInfo over the raw token. */
    fun sign(token: ByteArray): ByteArray = try {
        Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, pair.private)
            doFinal(paddedToken(token))
        }
    } catch (e: GeneralSecurityException) {
        throw AdbException(AdbError.KEY_STORAGE, e)
    }

    /** The public key as adbd wants it in AUTH(RSAPUBLICKEY): Android's adbkey.pub line, NUL-terminated. */
    fun publicKeyPayload(): ByteArray {
        val rsa = pair.public as RSAPublicKey
        return (encodePublicKey(rsa.modulus, rsa.publicExponent) + " $KEY_NAME\u0000").toByteArray(Charsets.US_ASCII)
    }

    companion object {
        const val KEY_NAME = "shamash@tv"
        private const val KEY_BITS = 2048
        private const val KEY_BYTES = KEY_BITS / 8
        private const val MODULUS_WORDS = KEY_BYTES / 4
        private const val PUBLIC_KEY_STRUCT = 4 + 4 + KEY_BYTES + KEY_BYTES + 4
        const val TOKEN_SIZE = 20

        /** ASN.1 DigestInfo header for SHA-1 (adbd treats the token as if it were the digest). */
        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        /** Loads the key from [dir], creating it the first time (or again if the files were damaged). */
        @Synchronized
        fun load(dir: File): AdbKey {
            val privateFile = File(dir, "adbkey")
            val publicFile = File(dir, "adbkey.pub")
            readPair(privateFile, publicFile)?.let { return AdbKey(it) }
            return try {
                val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
                writeAtomically(privateFile, pair.private.encoded)
                writeAtomically(publicFile, pair.public.encoded)
                AdbKey(pair)
            } catch (e: GeneralSecurityException) {
                throw AdbException(AdbError.KEY_STORAGE, e)
            } catch (e: java.io.IOException) {
                throw e as? AdbException ?: AdbException(AdbError.KEY_STORAGE, e)
            }
        }

        /** 00 01 FF…FF 00 ‖ DigestInfo(SHA-1) ‖ token: the 256-byte block that is raised to the private exponent. */
        fun paddedToken(token: ByteArray): ByteArray {
            if (token.size != TOKEN_SIZE) throw AdbException(AdbError.PROTOCOL)
            val block = ByteArray(KEY_BYTES)
            block[1] = 0x01
            val separator = KEY_BYTES - SHA1_DIGEST_INFO.size - token.size - 1
            for (i in 2 until separator) block[i] = 0xff.toByte()
            SHA1_DIGEST_INFO.copyInto(block, separator + 1)
            token.copyInto(block, separator + 1 + SHA1_DIGEST_INFO.size)
            return block
        }

        /**
         * Android's RSAPublicKey struct (modulus_size_words, n0inv, modulus LE, rr = 2^4096 mod n LE, exponent), all
         * little-endian, in base64: the format of adbkey.pub.
         */
        fun encodePublicKey(modulus: BigInteger, exponent: BigInteger): String {
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32.subtract(modulus.mod(r32).modInverse(r32))
            val rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(modulus)
            val struct = ByteBuffer.allocate(PUBLIC_KEY_STRUCT).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(MODULUS_WORDS)
                .putInt(n0inv.toInt())
                .put(littleEndian(modulus))
                .put(littleEndian(rr))
                .putInt(exponent.toInt())
                .array()
            return Base64.getEncoder().encodeToString(struct)
        }

        private fun littleEndian(value: BigInteger): ByteArray {
            val big = value.toByteArray()
            return ByteArray(KEY_BYTES) { i -> if (i < big.size) big[big.size - 1 - i] else 0 }
        }

        private fun readPair(privateFile: File, publicFile: File): KeyPair? {
            if (!privateFile.exists() || !publicFile.exists()) return null
            return try {
                val factory = KeyFactory.getInstance("RSA")
                KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
                )
            } catch (e: Exception) {
                Logger.w("ADB key unreadable, making a new one (the TV will ask again)", e)
                null
            }
        }

        /** All or nothing: a power cut mid-write must not leave half a key. */
        private fun writeAtomically(file: File, data: ByteArray) {
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use { out ->
                out.write(data)
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) throw AdbException(AdbError.KEY_STORAGE)
        }
    }
}
