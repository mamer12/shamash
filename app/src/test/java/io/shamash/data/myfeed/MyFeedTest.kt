package io.shamash.data.myfeed

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.ServerHttp
import io.shamash.data.UnsafeXmlException
import io.shamash.data.json
import io.shamash.domain.ContentLevel
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class MyFeedTest {
    @Test fun `PEX_21 RSS gives plain titles, art from thumbnail, enclosure or first img, and drops bad entries`() {
        val items = FeedParser.xml(Fixtures.read("myfeed/rss.xml").byteInputStream())
        assertEquals(listOf("First story", "Second", "Third"), items.map { it.title })       // tags removed, bad link and empty title dropped
        assertEquals("https://img.example/1.jpg", items[0].image)
        assertEquals("https://img.example/2.png", items[1].image)
        assertEquals("https://img.example/3.jpg", items[2].image)
        assertEquals("https://example.com/1", items[0].link)
        assertNotNull(items[0].dateMs)
        assertEquals("", items[1].subtitle)
    }

    @Test fun `PEX_21 Atom uses the alternate link and the entry date`() {
        val items = FeedParser.xml(Fixtures.read("myfeed/atom.xml").byteInputStream())
        assertEquals(listOf("Atom one", "Atom two"), items.map { it.title })
        assertEquals("https://example.com/a1", items[0].link)
        assertEquals("https://img.example/a1.jpg", items[0].image)
        assertNull(items[1].image)
        assertNotNull(items[0].dateMs)
        assertNotNull(items[1].dateMs)
    }

    @Test fun `PEX_21 JSON mapping by pointers`() {
        val root = json.parseToJsonElement(Fixtures.read("myfeed/items.json"))
        val m = JsonMapping(items = "/data/posts", title = "/headline", link = "/meta/url", image = "/meta/thumb", subtitle = "/by", date = "/ts")
        val items = FeedParser.json(root, m)
        assertEquals(listOf("Post A", "Post B"), items.map { it.title })            // no link and file: links are dropped
        assertEquals("https://img.example/pa.jpg", items[0].image)
        assertEquals("Ann", items[0].subtitle)
        assertEquals(1_767_268_800_000L, items[0].dateMs)
        assertNull(items[1].image)
        assertTrue(FeedParser.json(root, m.copy(items = "/data/none")).isEmpty())
        assertTrue(FeedParser.json(root, m.copy(items = "/data")).isEmpty())         // not a list
        assertEquals(root, FeedParser.pointer(root, ""))
        assertEquals("Post A", (FeedParser.pointer(root, "/data/posts/0/headline") as kotlinx.serialization.json.JsonPrimitive).content)
        assertNull(FeedParser.pointer(root, "/data/posts/9/x"))
    }

    @Test fun `PEX_22 a feed with a DTD or entities is refused and HTML is never kept`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title><link>https://e.com/1</link></item></channel></rss>"""
        assertThrows(UnsafeXmlException::class.java) { FeedParser.xml(xxe.byteInputStream()) }
        val billion = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;">]><rss><channel/></rss>"""
        assertThrows(UnsafeXmlException::class.java) { FeedParser.xml(billion.byteInputStream()) }
        assertEquals("Hi there", FeedParser.plain("<p>Hi <b>there</b></p>"))
        assertEquals("", FeedParser.plain(null))
    }

    @Test fun `PEX_21 YouTube links open in YouTube and others in whichever app takes them`() {
        assertEquals(OpenTarget.Video("abcdefghijk"), MyFeedProvider.target("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals(OpenTarget.Video("abcdefghijk"), MyFeedProvider.target("https://youtu.be/abcdefghijk?t=5"))
        assertEquals(OpenTarget.Video("abcdefghijk"), MyFeedProvider.target("https://m.youtube.com/shorts/abcdefghijk"))
        assertEquals(OpenTarget.AppPage("https://example.com/a", ""), MyFeedProvider.target("https://example.com/a"))
    }

    @Test fun `PEX_21 which feeds suit which profile`() {
        val suits = MyFeedProvider::suits
        assertTrue(suits(ContentLevel.ALL, ContentLevel.ALL))
        assertFalse(suits(ContentLevel.ALL, ContentLevel.FAMILY))
        assertFalse(suits(ContentLevel.ALL, ContentLevel.KIDS))
        assertTrue(suits(ContentLevel.FAMILY, ContentLevel.FAMILY))
        assertFalse(suits(ContentLevel.FAMILY, ContentLevel.KIDS))
        assertTrue(suits(ContentLevel.KIDS, ContentLevel.KIDS))
    }

    @Test fun `PEX_20 at most 10 feeds per profile, kept apart`() = runBlocking {
        val secrets = MemorySecrets()
        val a = FeedStore(secrets) { "a" }
        val b = FeedStore(secrets) { "b" }
        val saved = a.save((1..12).map { UserFeed("f$it", "Feed $it", "https://e.com/$it") })
        assertEquals(10, saved.size)
        assertEquals(10, a.list().size)
        assertTrue(b.list().isEmpty())
        assertEquals(UserFeed("f1", "Feed 1", "https://e.com/1"), a.list().first())
        a.save(emptyList())
        assertTrue(secrets.map.isEmpty())
    }

    // ---- the provider against a fake server ----

    private fun rss() = FakeServer { r ->
        when (r.requestUrl!!.encodedPath) {
            "/rss.xml" -> MockResponse().setBody(Fixtures.read("myfeed/rss.xml"))
            "/items.json" -> FakeServer.json(Fixtures.read("myfeed/items.json"))
            "/big.xml" -> MockResponse().setBody("<rss>" + "x".repeat(2_000_000) + "</rss>")
            "/auth.xml" -> if (r.getHeader("X-Api-Key") == "KEY-1") MockResponse().setBody(Fixtures.read("myfeed/rss.xml")) else MockResponse().setResponseCode(401)
            else -> null
        }
    }

    private suspend fun provider(vararg feeds: UserFeed): MyFeedProvider {
        val p = MyFeedProvider(FeedStore(MemorySecrets()) { "p1" }, ServerHttp(Http(null)), say = { "s$it" })
        p.save(feeds.toList())
        return p
    }

    private val profile = Profile(id = "p1", addons = setOf(MyFeedProvider.ID))

    @Test fun `PEX_20 PEX_21 a feed becomes a row of cards with its style and art`() = runBlocking {
        val server = rss()
        try {
            val feed = UserFeed("f1", "Daily news", server.url("/rss.xml"), style = FeedStyle.WIDE, allowHttp = true)
            val shelves = provider(feed).shelves(profile)
            val shelf = shelves.single()
            assertEquals("myfeed:f1", shelf.id)
            assertEquals("Daily news", shelf.title)
            assertEquals(io.shamash.domain.ShelfStyle.WIDE, shelf.style)
            assertEquals(3, shelf.items.size)
            assertEquals("https://img.example/1.jpg", shelf.items[0].backdropUrl)
            assertNull(shelf.items[0].posterUrl)
            assertEquals(OpenTarget.Video("abcdefghijk"), shelf.items[1].open)
            val poster = provider(feed.copy(style = FeedStyle.POSTER)).shelves(profile).single().items[0]
            assertEquals("https://img.example/1.jpg", poster.posterUrl)
            assertNull(poster.backdropUrl)
        } finally { server.shutdown() }
    }

    @Test fun `PEX_21 JSON feeds use their mapping`() = runBlocking {
        val server = rss()
        try {
            val m = JsonMapping("/data/posts", "/headline", "/meta/url", "/meta/thumb", "/by")
            val feed = UserFeed("j", "Posts", server.url("/items.json"), FeedKind.JSON, m, allowHttp = true)
            assertEquals(listOf("Post A", "Post B"), provider(feed).shelves(profile).single().items.map { it.title })
        } finally { server.shutdown() }
    }

    @Test fun `PEX_22 http needs the feed's own switch, big feeds and failing feeds give nothing`() = runBlocking {
        val server = rss()
        try {
            assertTrue(provider(UserFeed("f1", "n", server.url("/rss.xml"), allowHttp = false)).shelves(profile).isEmpty())
            assertTrue(provider(UserFeed("f2", "n", server.url("/big.xml"), allowHttp = true)).shelves(profile).isEmpty())
            assertTrue(provider(UserFeed("f3", "n", server.url("/missing.xml"), allowHttp = true)).shelves(profile).isEmpty())
            val both = provider(UserFeed("f3", "n", server.url("/missing.xml"), allowHttp = true), UserFeed("f1", "ok", server.url("/rss.xml"), allowHttp = true))
            assertEquals(listOf("myfeed:f1"), both.shelves(profile).map { it.id })       // one failing feed doesn't take the others down
        } finally { server.shutdown() }
    }

    @Test fun `PEX_22 an auth header is sent when set and the request is private`() = runBlocking {
        val server = rss()
        try {
            val feed = UserFeed("a", "Private", server.url("/auth.xml"), allowHttp = true, authHeader = "X-Api-Key: KEY-1")
            assertEquals(3, provider(feed).shelves(profile).single().items.size)
            assertTrue(server.seen.all { it.getHeader("X-Shamash-Private") == null })
            assertTrue(provider(feed.copy(authHeader = "")).shelves(profile).isEmpty())
            assertTrue(provider(feed.copy(authHeader = "bad header: x")).shelves(profile).isEmpty())
        } finally { server.shutdown() }
    }

    @Test fun `PEX_21 kids see only feeds marked for kids, and their cards carry a rating the gate accepts`() = runBlocking {
        val server = rss()
        try {
            val all = UserFeed("a", "Adult", server.url("/rss.xml"), allowHttp = true)
            val kids = UserFeed("k", "Kids", server.url("/rss.xml"), allowHttp = true, level = ContentLevel.KIDS)
            val p = provider(all, kids)
            assertEquals(listOf("myfeed:a", "myfeed:k"), p.shelves(profile).map { it.id })
            assertEquals(listOf("myfeed:k"), p.shelves(profile.copy(contentLevel = ContentLevel.KIDS)).map { it.id })
            assertEquals(listOf("myfeed:k"), p.shelves(profile.copy(contentLevel = ContentLevel.FAMILY)).map { it.id })
        } finally { server.shutdown() }
    }

    @Test fun `PEX_05 test connection counts working feeds and the editor's Try it shows three`() = runBlocking {
        val server = rss()
        try {
            val p = provider(UserFeed("f1", "ok", server.url("/rss.xml"), allowHttp = true), UserFeed("f2", "bad", server.url("/missing"), allowHttp = true))
            val r = p.test()
            assertTrue(r.ok)
            assertEquals(3, p.preview(UserFeed("t", "t", server.url("/rss.xml"), allowHttp = true), 3).size)
            assertThrows(Exception::class.java) { runBlocking { p.preview(UserFeed("t", "t", "http://example.com/x")) } }
            assertFalse(provider().test().ok)
        } finally { server.shutdown() }
    }
}
