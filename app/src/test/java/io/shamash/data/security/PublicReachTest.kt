package io.shamash.data.security

import io.shamash.data.FakeServer
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.MemorySecrets
import io.shamash.data.Reach
import io.shamash.data.ServerHttp
import io.shamash.data.ServerUrl
import io.shamash.data.myfeed.FeedStore
import io.shamash.data.myfeed.MyFeedProvider
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

/** User-supplied URLs are checked against private ranges when they are fetched, not only when added. */
class PublicReachTest {
    private fun ok() = FakeServer { MockResponse().setBody("<rss><channel><item><title>One</title><link>https://x.example/1</link></item></channel></rss>") }

    @Test fun `CMP_10 private and reserved addresses are local, public ones are not`() {
        listOf("127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.20", "169.254.169.254", "100.64.0.1", "0.0.0.0",
            "198.18.0.1", "224.0.0.1", "::1", "fd00::1", "fe80::1", "::ffff:192.168.1.1", "::ffff:127.0.0.1")
            .forEach { assertTrue(it, PrivateNet.isLocalAddress(InetAddress.getByName(it))) }
        listOf("93.184.216.34", "1.1.1.1", "2606:4700:4700::1111")
            .forEach { assertFalse(it, PrivateNet.isLocalAddress(InetAddress.getByName(it))) }
        listOf("localhost", "nas.local", "router.lan", "0x7f.1", "2130706433", "printer")
            .forEach { assertTrue(it, PrivateNet.isLocalName(it)) }
    }

    @Test fun `CMP_10 a public-reach fetch to a private address is refused before any request`() = runBlocking {
        val server = ok()
        try {
            val url = ServerUrl.parse(server.url("/feed.xml"), HttpPolicy.ANY)!!
            val http = ServerHttp(Http(null))
            val e = runCatching { http.text(url, reach = Reach.PUBLIC) }.exceptionOrNull()
            assertTrue(e is IOException)
            assertFalse(e!!.message.orEmpty().contains("feed.xml"))
            assertEquals(0, server.seen.size)
            // Media servers (PEX-12) keep their private reach.
            assertTrue(http.text(url).contains("One"))
            assertEquals(1, server.seen.size)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `CMP_10 a name that resolved public when added and private now (DNS rebinding) is refused`() {
        val server = ok()
        try {
            val port = server.url("/").toHttpUrl().port
            // Added: the phone's check resolves it to a public address.
            assertFalse(PrivateNet.isLocal("feed.example") { listOf(InetAddress.getByName("93.184.216.34")) })
            // Fetched: DNS now answers loopback.
            val rebinding = object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }
            val client = PublicSockets.applyTo(OkHttpClient.Builder().dns(rebinding), PublicSockets()).build()
            val e = runCatching { client.newCall(Request.Builder().url("http://feed.example:$port/feed.xml").build()).execute().close() }
                .exceptionOrNull()
            assertTrue(e is IOException)
            assertEquals(0, server.seen.size)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `CMP_10 a redirect into the local network is refused on the hop`() = runBlocking {
        val inside = ok()
        val outside = FakeServer { MockResponse().setResponseCode(302).setHeader("Location", inside.url("/feed.xml")) }
        try {
            var connections = 0
            // The first connection stands for the public feed host; any later one is the redirect's target.
            val http = ServerHttp(Http(null), refused = { connections++ > 0 })
            val url = ServerUrl.parse(outside.url("/feed.xml"), HttpPolicy.ANY)!!
            val e = runCatching { http.text(url, reach = Reach.PUBLIC) }.exceptionOrNull()
            assertTrue(e is IOException)
            assertEquals(1, outside.seen.size)
            assertEquals(0, inside.seen.size)
        } finally {
            inside.shutdown()
            outside.shutdown()
        }
    }

    @Test fun `PEX_22 My feed fetches with public reach, so a feed pointing into the LAN shows nothing`() = runBlocking {
        val server = ok()
        try {
            val feed = UserFeed("f1", "Inside", server.url("/feed.xml"), allowHttp = true)
            val provider = MyFeedProvider(FeedStore(MemorySecrets()) { "p1" }, ServerHttp(Http(null)), say = { "s$it" })
            provider.save(listOf(feed))
            assertTrue(runCatching { provider.fetch(feed) }.isFailure)
            assertTrue(provider.shelves(Profile(id = "p1", addons = setOf(MyFeedProvider.ID))).isEmpty())
            assertEquals(0, server.seen.size)
        } finally {
            server.shutdown()
        }
    }
}
