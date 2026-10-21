package io.shamash.feature.tvcontrol.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** The handshake and shell streams against scripted adbd replies. SPEC: TVC-01 */
class AdbClientTest {

    private class ScriptedLink(replies: List<AdbMessage>) : AdbClient.Link {
        private val sent = ByteArrayOutputStream()
        override val input: InputStream = ByteArrayInputStream(replies.flatMap { it.encode().toList() }.toByteArray())
        override val output: OutputStream = sent
        val timeouts = mutableListOf<Int>()
        override fun setTimeout(millis: Int) {
            timeouts += millis
        }
        override fun close() = Unit

        fun sentMessages(): List<AdbMessage> {
            val stream = ByteArrayInputStream(sent.toByteArray())
            return generateSequence { if (stream.available() > 0) AdbMessage.read(stream) else null }.toList()
        }
    }

    private val token = AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, AdbTestKey.TOKEN)
    private val connected = AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_DATA, "device::\u0000".toByteArray())

    @Test
    fun `TVC_01 a known key signs the token and connects`() {
        val link = ScriptedLink(listOf(token, connected))
        AdbClient.open(link, AdbTestKey.key, waitForApproval = true) { fail("no prompt for a known key") }
        val sent = link.sentMessages()
        assertEquals(listOf(AdbMessage.CNXN, AdbMessage.AUTH), sent.map { it.command })
        assertEquals(AdbMessage.AUTH_SIGNATURE, sent[1].arg0)
        assertArrayEquals(AdbTestKey.bytes("test_key.signature.b64"), sent[1].data)
    }

    @Test
    fun `TVC_01 an unknown key is offered and the TV's OK is awaited 60 s`() {
        val link = ScriptedLink(listOf(token, token, connected))
        var prompted = false
        AdbClient.open(link, AdbTestKey.key, waitForApproval = true) { prompted = true }
        val sent = link.sentMessages()
        assertTrue(prompted)
        assertEquals(AdbMessage.AUTH_RSAPUBLICKEY, sent[2].arg0)
        assertTrue(String(sent[2].data).endsWith(" shamash@tv\u0000"))
        assertEquals(listOf(AdbClient.APPROVAL_MS, AdbClient.TIMEOUT_MS), link.timeouts)
    }

    @Test
    fun `TVC_01 without waiting, an unknown key fails as not approved`() {
        assertFails(AdbError.NOT_APPROVED, listOf(token, token), waitForApproval = false)
    }

    @Test
    fun `TVC_01 a key refused after the prompt is reported`() {
        assertFails(AdbError.KEY_REJECTED, listOf(token, token, token), waitForApproval = true)
    }

    @Test
    fun `TVC_01 TLS-only adbd is reported`() {
        assertFails(AdbError.TLS_REQUIRED, listOf(AdbMessage(AdbMessage.STLS, 1, 0)), waitForApproval = true)
    }

    @Test
    fun `TVC_01 a silent adbd is no response`() {
        assertFails(AdbError.NO_RESPONSE, emptyList(), waitForApproval = true)
    }

    @Test
    fun `TVC_01 shell reads WRTE until CLSE and acknowledges each`() {
        val link = ScriptedLink(
            listOf(
                connected,
                AdbMessage(AdbMessage.OKAY, 42, 1),
                AdbMessage(AdbMessage.WRTE, 42, 1, "Succ".toByteArray()),
                AdbMessage(AdbMessage.WRTE, 42, 1, "ess\n".toByteArray()),
                AdbMessage(AdbMessage.CLSE, 42, 1),
            ),
        )
        val client = AdbClient.open(link, AdbTestKey.key, waitForApproval = false) {}
        assertEquals("Success\n", client.run("cmd package set-home-activity io.shamash/.HomeAlias"))
        val sent = link.sentMessages().drop(1) // after CNXN
        assertEquals(listOf(AdbMessage.OPEN, AdbMessage.OKAY, AdbMessage.OKAY, AdbMessage.CLSE), sent.map { it.command })
        assertEquals("shell:cmd package set-home-activity io.shamash/.HomeAlias\u0000", String(sent[0].data))
        assertTrue(sent.drop(1).all { it.arg0 == 1 && it.arg1 == 42 })
    }

    @Test
    fun `TVC_01 a stream closed before opening is a refused command`() {
        val link = ScriptedLink(listOf(connected, AdbMessage(AdbMessage.CLSE, 0, 1)))
        val client = AdbClient.open(link, AdbTestKey.key, waitForApproval = false) {}
        try {
            client.run("ls")
            fail("expected a refusal")
        } catch (e: AdbException) {
            assertEquals(AdbError.COMMAND_REFUSED, e.error)
        }
    }

    private fun assertFails(expected: AdbError, replies: List<AdbMessage>, waitForApproval: Boolean) {
        try {
            AdbClient.open(ScriptedLink(replies), AdbTestKey.key, waitForApproval) {}
            fail("expected $expected")
        } catch (e: AdbException) {
            assertEquals(expected, e.error)
        }
    }
}
