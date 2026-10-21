package io.shamash.data.backup

import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 into AES-256-GCM (CMP-14). The header is bound as associated data, so changing the iteration
 * count, salt or version fails authentication. The passphrase is never stored or logged. Pure JVM crypto.
 * SPEC: CMP-14, PLT-02
 */
internal object BackupCrypto {
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun salt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)
    fun nonce(): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val chars = Normalizer.normalize(passphrase, Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    fun encrypt(key: SecretKeySpec, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plain)

    /** Null when the passphrase is wrong or any byte was changed. */
    fun decrypt(key: SecretKeySpec, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray? =
        try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)
        } catch (e: java.security.GeneralSecurityException) {
            null
        }

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
}
