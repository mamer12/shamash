package io.shamash.feature.companion

import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class CompanionSecurityTest {
    @Test fun `CMP_03 PIN has six digits including leading zeroes`() {
        repeat(100) { assertTrue(Pins.isWellFormed(Pins.fresh(Random(it.toLong())))) }
        assertTrue(Pins.isWellFormed("000001"))
        listOf("1234", "1234567", "12a456", "１２３４５６").forEach { assertFalse(Pins.isWellFormed(it)) }
    }

    @Test fun `CMP_03 failures globally lock then double the wait and rotate on third lock`() {
        var now = 0L
        val guard = PinGuard { now }
        repeat(PinGuard.ROTATE_AFTER) { lock ->
            repeat(PinGuard.LIMIT - 1) { assertEquals(PinGuard.Verdict.WRONG, guard.failed()) }
            assertEquals(if (lock == 2) PinGuard.Verdict.ROTATE else PinGuard.Verdict.LOCKED, guard.failed())
            val duration = PinGuard.FIRST_LOCK_MS * (1 shl lock)
            assertEquals(duration, guard.lockedForMs())
            now += duration - 1
            assertEquals(1L, guard.lockedForMs())
            now++
            assertEquals(0L, guard.lockedForMs())
        }
    }

    @Test fun `CMP_03 successful authentication resets failures`() {
        val guard = PinGuard { 0L }
        repeat(4) { guard.failed() }
        guard.succeeded()
        assertEquals(PinGuard.Verdict.WRONG, guard.failed())
        assertEquals(0L, guard.lockedForMs())
    }

    @Test fun `CMP_03 cross-site forms and rebound host names are refused`() {
        assertFalse(CompanionGate.isPageSend(HttpRequest("POST", "/send", "")))
        assertFalse(CompanionGate.isPageSend(HttpRequest("POST", "/send", "", mapOf("x-shamash" to "true"))))
        assertTrue(CompanionGate.isPageSend(HttpRequest("POST", "/send", "", mapOf("x-shamash" to "1"))))
        assertTrue(CompanionGate.hostAllowed("192.168.1.20:8765", "192.168.1.20", 8765))
        listOf(null, "evil.example:8765", "192.168.1.20", "192.168.1.20:80").forEach {
            assertFalse(CompanionGate.hostAllowed(it, "192.168.1.20", 8765))
        }
    }

    @Test fun `CMP_05 unknown routes and CORS preflights stop before headers`() {
        listOf("OPTIONS /send", "POST /", "GET /send", "DELETE /").forEach { first ->
            val input = "$first HTTP/1.1\r\nsecret header bytes".byteInputStream()
            assertNull(MiniHttp.read(input, accept = CompanionGate::accepts))
            assertEquals("secret header bytes", input.readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test fun `CMP_05 deadline stops trickled headers`() {
        var now = 0L
        val data = "GET / HTTP/1.1\r\nHost: tv\r\n\r\n".byteInputStream()
        val slow = object : InputStream() {
            override fun read(): Int { now += 100_000_000; return data.read() }
        }
        assertThrows(SocketTimeoutException::class.java) { MiniHttp.read(slow, Deadline.after(500) { now }) }
    }

    @Test fun `CMP_05 final body read must finish before the deadline`() {
        var now = 0L
        val data = "POST /send HTTP/1.1\r\nContent-Length: 1\r\n\r\nx".byteInputStream()
        val slow = object : InputStream() {
            override fun read() = data.read()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                now = 6_000_000_000
                return data.read(bytes, offset, length)
            }
        }
        assertThrows(SocketTimeoutException::class.java) { MiniHttp.read(slow, Deadline.after(5_000) { now }) }
    }

    @Test fun `CMP_02 page only permits its nonce script and never advertises CORS`() {
        val reply = HttpReply.html("page", "test-nonce").bytes().toString(Charsets.UTF_8)
        assertTrue(reply.contains("script-src 'nonce-test-nonce'"))
        assertTrue(reply.contains("default-src 'none'"))
        assertTrue(reply.contains("connect-src 'self'"))
        assertTrue(reply.contains("frame-ancestors 'none'"))
        assertFalse(reply.contains("Access-Control-Allow-Origin"))
        val page = CompanionPage.fill(emptyMap(), "test-nonce")
        assertTrue(page.contains("nonce=\"test-nonce\""))
        assertTrue(page.contains("X-Shamash"))
    }
}
