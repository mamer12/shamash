package io.shamash.data

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class HttpPrivacyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `ARCH_06 API key and bearer requests override cacheable server responses`() = runTest {
        val server = MockWebServer()
        val http = Http(folder.root)
        try {
            server.start()
            val url = server.url("/catalog?api_key=synthetic-test-key").toString()
            repeat(2) { server.enqueue(MockResponse().setHeader("Cache-Control", "public, max-age=3600").setBody("answer-$it")) }
            assertEquals("answer-0", http.text(url))
            assertEquals("answer-1", http.text(url))
            assertEquals(2, server.requestCount)
            http.client.cache?.flush()
            assertFalse(folder.root.walkTopDown().filter { it.isFile }.any { it.readText().contains("synthetic-test-key") })
            assertFalse(http.client.cache?.urls()?.hasNext() ?: false)
            val bearer = HttpPrivacy.request(Request.Builder().url(server.url("/catalog"))
                .header("Authorization", "Bearer synthetic-token").build())
            assertTrue(bearer.cacheControl.noStore)
            assertTrue(bearer.cacheControl.noCache)
        } finally {
            http.client.cache?.close()
            server.shutdown()
        }
    }

    @Test fun `ARCH_04 ordinary requests retain shared cache behavior`() {
        val request = Request.Builder().url("https://example.test/weather").build()
        assertSame(request, HttpPrivacy.request(request))
    }

    @Test fun `ARCH_06 logged exceptions omit URLs credentials and response bodies`() {
        val error = IOException("https://example.test/?api_key=synthetic-test-key private response body")
        assertEquals("TMDB: IOException", Logger.line("TMDB", error))
        assertFalse(Logger.line("TMDB", error).contains("synthetic-test-key"))
    }
}
