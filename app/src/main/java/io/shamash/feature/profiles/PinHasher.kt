package io.shamash.feature.profiles

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/*
 * A profile PIN (4 digits) as a salted PBKDF2 hash, never the digits. Stored as `pbkdf2$<iterations>$<salt>$<hash>`
 * (hex), so the cost can rise later without breaking old hashes. Pure JVM. SPEC: PRF-05, PRF-20
 */
object PinHasher {
    const val PIN_LENGTH = 4
    const val ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"
    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = ITERATIONS, salt: ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)): String {
        require(isValidPin(pin)) { "a PIN is $PIN_LENGTH digits" }
        return "$PREFIX$$iterations$${hex(salt)}$${hex(derive(pin, salt, iterations))}"
    }

    /** True when [pin] matches [stored]; a missing or malformed hash never matches. Constant-time compare. */
    fun verify(pin: String, stored: String?): Boolean {
        val parts = stored?.split('$') ?: return false
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = unhex(parts[2]) ?: return false
        val expected = unhex(parts[3]) ?: return false
        if (!isValidPin(pin)) return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)).encoded

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String): ByteArray? =
        if (text.isEmpty() || text.length % 2 != 0) null
        else runCatching { ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
}
