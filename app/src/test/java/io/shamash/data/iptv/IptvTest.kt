package io.shamash.data.iptv

import io.shamash.R
import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.SafeXml
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.UnsafeXmlException
import io.shamash.domain.ContentLevel
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class IptvTest {
    private fun m3u(text: String, max: Int = M3u.MAX) = M3u.parse(text.byteInputStream(), max)

    @Test fun `PEX_14 reads attributes, BOM, names with commas and skips what it can't use`() {
        val pl = m3u(Fixtures.read("iptv/playlist.m3u"))
        assertEquals(listOf("News One HD", "Sports, Live", "No Attrs", "Cartoons"), pl.channels.map { it.name })
        val news = pl.channels[0]
        assertEquals("news.one", news.id)
        assertEquals("News", news.group)
        assertEquals("https://img.example/news1.png", news.logo)
        assertEquals("http://stream.example:8080/live/u/p/1.ts", news.url)
        assertEquals("No Attrs", pl.channels[2].id)
        assertEquals("", pl.channels[2].group)
        assertNull(pl.channels[3].logo)           // not an http(s) picture
        assertEquals(listOf("News", "Sports", "Kids"), pl.groups)
    }

    @Test fun `PEX_14 keeps at most 5000 channels of a 10k-line playlist and stops reading`() {
        val text = buildString {
            append("#EXTM3U\n")
            repeat(5_000) { append("#EXTINF:-1 group-title=\"G${it % 40}\",Ch $it\nhttp://s.example/$it.ts\n") }
        }
        assertTrue(text.lines().size > 10_000)
        val pl = m3u(text)
        assertEquals(M3u.MAX, pl.channels.size)
        val more = text + text.replace("Ch ", "More ")
        assertEquals(M3u.MAX, m3u(more).channels.size)
        assertEquals(3, m3u(more, max = 3).channels.size)
    }

    @Test fun `PEX_14 junk and an empty playlist give no channels`() {
        assertTrue(m3u("").channels.isEmpty())
        assertTrue(m3u("<html>not a playlist</html>").channels.isEmpty())
        assertTrue(m3u("#EXTM3U\nhttp://orphan.example/a.ts\n").channels.isEmpty())
    }

    private val at = Xmltv.time("20260101111500 +0000")!!
    private val wanted = mapOf("news.one" to "news.one", "cartoons" to "cartoons")

    @Test fun `PEX_15 now and next for wanted channels only`() {
        val r = Xmltv.nowNext(Fixtures.read("iptv/guide.xml").byteInputStream(), wanted, at)
        assertEquals(setOf("news.one", "cartoons"), r.keys)
        assertEquals("Weather", r.getValue("news.one").now?.title)
        assertEquals("Late show", r.getValue("news.one").next?.title)
        assertEquals("Cartoon Hour", r.getValue("cartoons").now?.title)   // matched by display name, +0100 offset applied
        assertNull(r.getValue("cartoons").next)
    }

    @Test fun `PEX_15 times with and without offset`() {
        assertEquals(Xmltv.time("20260101120000 +0000"), Xmltv.time("20260101130000 +0100"))
        assertEquals(Xmltv.time("20260101120000 +0000"), Xmltv.time("20260101120000"))
        assertEquals(Xmltv.time("20260101120000 +0000"), Xmltv.time("202601011200 +0000"))
        assertNull(Xmltv.time("yesterday"))
        assertNull(Xmltv.time(null))
    }

    @Test fun `PEX_15 bad or truncated XML is skipped silently`() {
        assertTrue(Xmltv.nowNext("<tv><channel".byteInputStream(), wanted, at).isEmpty())
        assertTrue(Xmltv.nowNext("".byteInputStream(), wanted, at).isEmpty())
        assertTrue(Xmltv.nowNext("not xml at all".byteInputStream(), wanted, at).isEmpty())
    }

    @Test fun `PEX_22 a DTD with an external entity is refused`() {
        assertThrows(UnsafeXmlException::class.java) {
            SafeXml.parse(Fixtures.read("iptv/xxe.xml"), org.xml.sax.helpers.DefaultHandler())
        }
        assertTrue(Xmltv.nowNext(Fixtures.read("iptv/xxe.xml").byteInputStream(), wanted, at).isEmpty())
        assertThrows(UnsafeXmlException::class.java) {
            SafeXml.parse("<?xml version=\"1.0\"?><!doctype x [<!entity a \"b\">]><x/>", org.xml.sax.helpers.DefaultHandler())
        }
    }

    // ---- the provider, against a fake server ----

    private class Rig(clock: Long) {
        val secrets = MemorySecrets()
        val gz = Buffer().also { b -> GzipSink(b).buffer().use { it.writeUtf8(Fixtures.read("iptv/guide.xml")) } }
        val server = FakeServer { r ->
            when (r.requestUrl!!.encodedPath) {
                "/list.m3u" -> MockResponse().setBody(Fixtures.read("iptv/playlist.m3u"))
                "/guide.xml.gz" -> MockResponse().setBody(gz.clone())
                else -> null
            }
        }
        val credentials = SecretCredentials(secrets, IptvProvider.ID, IptvProvider.fields) { "p1" }
        val provider = IptvProvider(
            credentials, ServerHttp(Http(null)), IptvChoices(secrets) { "p1" },
            say = { if (it == R.string.pex_now) "Now: %s" else if (it == R.string.pex_next) "Next: %s" else "s$it" },
            clock = { clock },
        )
    }

    private val profile = Profile(id = "p1", addons = setOf(IptvProvider.ID))

    @Test fun `PEX_14 PEX_15 favorites and group rows, opening in the chosen player with the guide's now and next`() = runBlocking {
        val rig = Rig(at)
        try {
            rig.credentials.set("playlist", rig.server.url("/list.m3u"))
            rig.credentials.set("epg", rig.server.url("/guide.xml.gz"))
            rig.credentials.set("player", "org.videolan.vlc")
            rig.provider.toggleFavorite("news.one")
            val shelves = rig.provider.shelves(profile)
            val fav = shelves.first { it.id == "iptv:fav" }
            val card = fav.items.single()
            assertEquals("News One HD", card.title)
            assertEquals("Now: Weather · Next: Late show", card.subtitle)
            assertEquals(OpenTarget.AppPage("http://stream.example:8080/live/u/p/1.ts", "org.videolan.vlc", "video/*"), card.open)
            assertEquals("iptv", card.providerId)
            val groups = shelves.filter { it.id != "iptv:fav" }.map { it.title }
            assertEquals(listOf("News", "Sports", "Kids"), groups)
            assertTrue(rig.provider.featured(profile).isEmpty())
            assertTrue(rig.server.seen.all { it.getHeader("X-Shamash-Private") == null })
        } finally { rig.server.shutdown() }
    }

    @Test fun `PEX_07 channels have no rating so a family or kids profile sees none`() = runBlocking {
        val rig = Rig(at)
        try {
            rig.credentials.set("playlist", rig.server.url("/list.m3u"))
            assertTrue(rig.provider.shelves(profile.copy(contentLevel = ContentLevel.KIDS)).isEmpty())
            assertTrue(rig.provider.shelves(profile).isNotEmpty())
            assertTrue(rig.provider.shelves(Profile(id = "p1")).isEmpty())            // add-on off
        } finally { rig.server.shutdown() }
    }

    @Test fun `PEX_16 test connection reports the count and never echoes the address`() = runBlocking {
        val rig = Rig(at)
        try {
            rig.credentials.set("playlist", rig.server.url("/list.m3u?u=bob&p=hunter2"))
            val r = rig.provider.test()
            assertTrue(r.ok)
            assertFalse(r.message.contains("hunter2"))
            rig.credentials.set("playlist", rig.server.url("/missing.m3u?p=hunter2"))
            val bad = rig.provider.test()
            assertFalse(bad.ok)
            assertFalse(bad.message.contains("hunter2"))
            rig.credentials.set("playlist", "not a url")
            assertFalse(rig.provider.test().ok)
        } finally { rig.server.shutdown() }
    }

    @Test fun `PEX_14 favorites are kept per profile`() = runBlocking {
        val secrets = MemorySecrets()
        val a = IptvChoices(secrets) { "a" }
        val b = IptvChoices(secrets) { "b" }
        assertEquals(listOf("x"), a.toggle("x"))
        assertTrue(b.favorites().isEmpty())
        assertEquals(emptyList<String>(), a.toggle("x"))
        assertTrue(secrets.map.isEmpty())
    }
}
