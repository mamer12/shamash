package io.shamash.feature.tvcontrol.adb

import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * A throwaway RSA-2048 key made only for these tests (not any TV's key), with vectors computed independently in
 * Python: the adbkey.pub line per AOSP's android_pubkey_encode, and the token signature with
 * `cryptography`'s PKCS1v15 + Prehashed(SHA1).
 */
object AdbTestKey {
    val TOKEN = ByteArray(20) { (it + 1).toByte() }

    fun text(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("tvcontrol/adb/$name")) { name }.readText().trim()

    fun bytes(name: String): ByteArray = Base64.getDecoder().decode(text(name))

    val pair: KeyPair by lazy {
        val factory = KeyFactory.getInstance("RSA")
        KeyPair(
            factory.generatePublic(X509EncodedKeySpec(bytes("test_key.x509.b64"))),
            factory.generatePrivate(PKCS8EncodedKeySpec(bytes("test_key.pk8.b64"))),
        )
    }

    val key: AdbKey by lazy { AdbKey(pair) }
}
