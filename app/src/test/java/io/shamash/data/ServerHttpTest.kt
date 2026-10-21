package io.shamash.data

import io.shamash.domain.Profile
import io.shamash.domain.CredentialField
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ServerHttpTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `PEX_12 plain http only where the policy allows it`() {
        assertNotNull(ServerUrl.parse("https://example.com/x"))
        assertNull(ServerUrl.parse("http://example.com/x"))
        assertNull(ServerUrl.parse("http://example.com/x", HttpPolicy.PRIVATE_ONLY))
        assertNotNull(ServerUrl.parse("http://example.com/x", HttpPolicy.ANY))
        listOf("192.168.1.5", "10.0.0.2", "172.16.0.1", "172.31.255.1", "127.0.0.1", "169.254.1.1", "nas.local", "localhost", "nas")
            .forEach { assertNotNull(it, ServerUrl.parse("http://$it:8096", HttpPolicy.PRIVATE_ONLY)) }
        listOf("172.32.0.1", "8.8.8.8", "192.169.1.1", "example.com", "1.2.3.4.5").forEach {
            assertNull(it, ServerUrl.parse("http://$it:8096", HttpPolicy.PRIVATE_ONLY))
        }
        assertNull(ServerUrl.parse("file:///etc/passwd", HttpPolicy.ANY))
        assertNull(ServerUrl.parse("not a url", HttpPolicy.ANY))
        assertNull(ServerUrl.parse("  ", HttpPolicy.ANY))
    }

    @Test fun `PEX_04 private requests are never cached and errors name the host only`() = runBlocking {
        val server = FakeServer { r ->
            if (r.requestUrl!!.encodedPath == "/ok") MockResponse().setHeader("Cache-Control", "public, max-age=3600").setBody("answer")
            else MockResponse().setResponseCode(500)
        }
        val http = Http(folder.root)
        try {
            val s = ServerHttp(http)
            val ok = ServerUrl.parse(server.url("/ok"), HttpPolicy.ANY)!!
            val headers = mapOf("X-Plex-Token" to "SECRET-TOKEN-1")
            assertEquals("answer", s.text(ok, headers))
            assertEquals("answer", s.text(ok, headers))
            assertEquals(2, server.seen.size)
            http.client.cache?.flush()
            assertFalse(http.client.cache?.urls()?.hasNext() ?: false)
            assertFalse(folder.root.walkTopDown().filter { it.isFile }.any { it.readText().contains("SECRET-TOKEN-1") })
            assertTrue(server.seen.all { it.getHeader(HttpPrivacy.PRIVATE) == null })
            val bad = ServerUrl.parse(server.url("/boom?user=bob&password=hunter2"), HttpPolicy.ANY)!!
            val e = runCatching { s.text(bad) }.exceptionOrNull() as IOException
            assertFalse(e.message!!.contains("hunter2"))
            assertFalse(Logger.line("playlist", e).contains("hunter2"))
        } finally {
            http.client.cache?.close()
            server.shutdown()
        }
    }

    @Test fun `PEX_04 credentials and feeds redact themselves`() {
        val c = SecretCredentials(MemorySecrets(), "plex", listOf(CredentialField("token", 1)), { "p1" })
        assertFalse(c.toString().contains("p1_plex"))
        val f = io.shamash.data.myfeed.UserFeed("a", "Mine", "https://h.example/f?key=SECRET", authHeader = "Authorization: Bearer SECRET")
        assertFalse(f.toString().contains("SECRET"))
        assertTrue(f.toString().contains("h.example"))
    }

    @Test fun `gzip answers are read as plain text and size is bounded`() = runBlocking {
        val gz = Buffer().also { b -> GzipSink(b).buffer().use { it.writeUtf8("hello gz") } }
        val server = FakeServer { r ->
            if (r.requestUrl!!.encodedPath == "/g.gz") MockResponse().setBody(gz.clone()) else MockResponse().setBody("x".repeat(5000))
        }
        try {
            val s = ServerHttp(Http(null))
            assertEquals("hello gz", s.text(ServerUrl.parse(server.url("/g.gz"), HttpPolicy.ANY)!!))
            assertThrows(IOException::class.java) {
                runBlocking { s.text(ServerUrl.parse(server.url("/big"), HttpPolicy.ANY)!!, maxBytes = 1000) }
            }
            Unit
        } finally { server.shutdown() }
    }
}
