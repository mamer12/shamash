package io.shamash.data.security

import io.shamash.data.FakeServer
import io.shamash.data.Http
import io.shamash.data.HttpPrivacy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Redirects never downgrade a guarded call to http and never carry the app's headers to another host. */
class RedirectGuardTest {
    /** One network hop: [original] is the call as the app made it, [hop] what goes on the wire now. */
    private class Hop(private val original: Request, private val hop: Request, private val answer: (Request) -> Response) : Interceptor.Chain {
        val sent = ArrayList<Request>()
        override fun request() = hop
        override fun proceed(request: Request): Response = answer(request).also { sent += request }
        override fun connection(): Connection? = null
        override fun call(): Call = OkHttpClient().newCall(original)
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private fun reply(request: Request, code: Int = 200, location: String? = null) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .apply { if (location != null) header("Location", location) }
        .body("".toResponseBody()).build()

    private fun plex(url: String) = Request.Builder().url(url).header("X-Plex-Token", "SECRET-TOKEN").build()

    @Test fun `PEX_04 a credentialed https call is not followed to http`() {
        val original = plex("https://media.example/library")
        val answered = Hop(original, original) { reply(it, 302, "http://media.example/library") }
        val e = runCatching { RedirectGuard.intercept(answered) }.exceptionOrNull()
        assertEquals(RedirectGuard.DOWNGRADE, e?.message)
        val downgraded = Hop(original, plex("http://media.example/library")) { reply(it) }
        assertTrue(runCatching { RedirectGuard.intercept(downgraded) }.exceptionOrNull() is IOException)
        assertTrue(downgraded.sent.isEmpty())
    }

    @Test fun `PEX_22 a user feed (private tag) is not followed from https to http either`() {
        val original = Request.Builder().url("https://feed.example/rss").header(HttpPrivacy.PRIVATE, "1").build()
        val chain = Hop(original, original) { reply(it, 301, "http://feed.example/rss") }
        assertTrue(runCatching { RedirectGuard.intercept(chain) }.exceptionOrNull() is IOException)
        // https to https and http to https are fine.
        val upgrade = Request.Builder().url("http://feed.example/rss").header(HttpPrivacy.PRIVATE, "1").build()
        assertEquals(301, RedirectGuard.intercept(Hop(upgrade, upgrade) { reply(it, 301, "https://feed.example/rss") }).code)
        assertFalse(RedirectGuard.isDowngrade("http://a.example/".toHttpUrl(), "https://a.example/".toHttpUrl()))
        assertTrue(RedirectGuard.isDowngrade("https://a.example/".toHttpUrl(), "http://a.example/".toHttpUrl()))
    }

    @Test fun `PEX_04 app headers are dropped on a hop to another host, kept on the same host`() {
        val original = Request.Builder().url("https://media.example/a")
            .header("X-Plex-Token", "SECRET-TOKEN").header("X-Feed-Key", "FEED-KEY").header("Range", "bytes=0-").build()
        val elsewhere = original.newBuilder().url("https://cdn.example/a").build()
        val out = RedirectGuard.stripped(original, elsewhere)
        assertNull(out.header("X-Plex-Token"))
        assertNull(out.header("X-Feed-Key"))
        assertEquals("bytes=0-", out.header("Range"))
        val same = original.newBuilder().url("https://media.example/b").build()
        assertEquals("SECRET-TOKEN", RedirectGuard.stripped(original, same).header("X-Plex-Token"))
    }

    @Test fun `PEX_04 through the app's client a cross-host redirect reaches the other host without the token`() {
        val target = FakeServer { MockResponse().setBody("ok") }
        // The same server under another name: "localhost" redirects to "127.0.0.1".
        val port = target.url("/").toHttpUrl().port
        val start = FakeServer { MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.1:$port/next") }
        try {
            val http = Http(null)
            val request = Request.Builder().url(start.url("/first").replace("127.0.0.1", "localhost"))
                .header("X-Emby-Token", "SECRET-TOKEN").header("X-Feed-Key", "FEED-KEY").build()
            http.client.newCall(request).execute().use { assertEquals("ok", it.body?.string()) }
            val seen = target.seen.single()
            assertNull(seen.getHeader("X-Emby-Token"))
            assertNull(seen.getHeader("X-Feed-Key"))
            assertEquals("SECRET-TOKEN", start.seen.single().getHeader("X-Emby-Token"))
        } finally {
            start.shutdown()
            target.shutdown()
        }
    }

    @Test fun `PEX_04 calls without a credential or the private tag keep OkHttp's usual redirects`() {
        val original = Request.Builder().url("https://cdn.example/video").build()
        assertFalse(RedirectGuard.isGuarded(original))
        assertEquals(302, RedirectGuard.intercept(Hop(original, original) { reply(it, 302, "http://cdn.example/video") }).code)
        assertTrue(RedirectGuard.isGuarded(Request.Builder().url("https://m.example/x?X-Plex-Token=t").build()))
    }
}
