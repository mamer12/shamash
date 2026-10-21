package io.shamash.feature.tvcontrol.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class AdbKeyTest {

    @Test
    fun `TVC_01 token padding is 00 01 FF then the SHA-1 DigestInfo and the raw token`() {
        val block = AdbKey.paddedToken(AdbTestKey.TOKEN)
        assertEquals(256, block.size)
        assertEquals(0, block[0].toInt())
        assertEquals(1, block[1].toInt())
        val separator = 256 - 15 - 20 - 1
        assertTrue((2 until separator).all { block[it] == 0xff.toByte() })
        assertEquals(0, block[separator].toInt())
        assertEquals(0x30, block[separator + 1].toInt())
        assertEquals(0x14, block[separator + 15].toInt())
        assertArrayEquals(AdbTestKey.TOKEN, block.copyOfRange(256 - 20, 256))
    }

    @Test(expected = AdbException::class)
    fun `TVC_01 a token that isn't 20 bytes is refused`() {
        AdbKey.paddedToken(ByteArray(19))
    }

    @Test
    fun `TVC_01 signature matches PKCS1 v1_5 over the prehashed token (independent vector)`() {
        assertArrayEquals(AdbTestKey.bytes("test_key.signature.b64"), AdbTestKey.key.sign(AdbTestKey.TOKEN))
    }

    @Test
    fun `TVC_01 the public key opens the signature back to the padded token`() {
        val opened = Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, AdbTestKey.pair.public)
            doFinal(AdbTestKey.key.sign(AdbTestKey.TOKEN))
        }
        assertArrayEquals(AdbKey.paddedToken(AdbTestKey.TOKEN), opened)
    }

    @Test
    fun `TVC_01 public key encodes like Android's adbkey_pub (independent vector)`() {
        val rsa = AdbTestKey.pair.public as RSAPublicKey
        assertEquals(AdbTestKey.text("test_key.adbkey.pub"), AdbKey.encodePublicKey(rsa.modulus, rsa.publicExponent))
    }

    @Test
    fun `TVC_01 the AUTH payload is the key line, a name and a NUL`() {
        val payload = String(AdbTestKey.key.publicKeyPayload(), Charsets.US_ASCII)
        assertEquals(AdbTestKey.text("test_key.adbkey.pub") + " shamash@tv\u0000", payload)
    }

    @Test
    fun `TVC_01 the key is made once and read back the same`() {
        val dir = Files.createTempDirectory("adbkey").toFile()
        try {
            val first = AdbKey.load(dir).publicKeyPayload()
            val again = AdbKey.load(dir).publicKeyPayload()
            assertArrayEquals(first, again)
            assertTrue(dir.resolve("adbkey").exists() && dir.resolve("adbkey.pub").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
