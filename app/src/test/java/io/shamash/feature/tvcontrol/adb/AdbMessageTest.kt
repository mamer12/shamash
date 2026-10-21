package io.shamash.feature.tvcontrol.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class AdbMessageTest {

    /** The CNXN every adb host sends, as captured on the wire. */
    private val recordedConnect = hex("434e584e01000001000004000700000032020000bcb1a7b1686f73743a3a00")

    @Test
    fun `TVC_01 CNXN encodes as recorded (little-endian header, checksum, magic)`() {
        val message = AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_DATA, "host::\u0000".toByteArray())
        assertArrayEquals(recordedConnect, message.encode())
    }

    @Test
    fun `TVC_01 a recorded packet decodes back to its fields`() {
        val m = AdbMessage.read(ByteArrayInputStream(recordedConnect))
        assertEquals(AdbMessage.CNXN, m.command)
        assertEquals(AdbMessage.VERSION, m.arg0)
        assertEquals(AdbMessage.MAX_DATA, m.arg1)
        assertEquals("host::\u0000", String(m.data))
    }

    @Test
    fun `TVC_01 checksum is the sum of unsigned bytes`() {
        assertEquals(0x232, AdbMessage.checksum("host::\u0000".toByteArray()))
        assertEquals(255 * 3, AdbMessage.checksum(byteArrayOf(-1, -1, -1)))
    }

    @Test
    fun `TVC_01 encode then read keeps every field`() {
        val sent = AdbMessage(AdbMessage.WRTE, 7, 9, byteArrayOf(1, 2, 3))
        val back = AdbMessage.read(ByteArrayInputStream(sent.encode()))
        assertEquals(listOf(AdbMessage.WRTE, 7, 9), listOf(back.command, back.arg0, back.arg1))
        assertArrayEquals(byteArrayOf(1, 2, 3), back.data)
    }

    @Test
    fun `TVC_01 a bad magic is refused`() {
        val broken = recordedConnect.copyOf().also { it[20] = 0 }
        try {
            AdbMessage.read(ByteArrayInputStream(broken))
            fail("expected a protocol error")
        } catch (e: AdbException) {
            assertEquals(AdbError.PROTOCOL, e.error)
        }
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
