package io.shamash.data.podcasts

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.UnsafeXmlException
import io.shamash.domain.ContentLevel
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.RowSources
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class PodcastsTest {
    @Test fun `PEX_23 a podcast feed gives its title, art and the audio episodes only`() {
        val show = PodcastFeed.parse(Fixtures.read("podcasts/show.xml").byteInputStream())
        assertEquals("The Sample Show", show.title)
        assertEquals("https://img.example/show.jpg", show.image)
        assertEquals(listOf("Episode 3: Newest", "Episode 2"), show.episodes.map { it.title })   // video, untitled and file: dropped
        assertEquals("https://cdn.example/ep3.mp3", show.episodes[0].audioUrl)
        assertEquals("audio/mpeg", show.episodes[0].mime)
        assertEquals("https://img.example/ep3.jpg", show.episodes[0].image)
        assertNull(show.episodes[1].image)
        assertNotNull(show.episodes[0].dateMs)
        assertTrue(show.episodes[0].dateMs!! > show.episodes[1].dateMs!!)
    }

    @Test fun `PEX_23 PEX_22 a feed or OPML with a DTD is refused`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>"""
        assertThrows(UnsafeXmlException::class.java) { PodcastFeed.parse(xxe.byteInputStream()) }
        assertThrows(UnsafeXmlException::class.java) { Opml.parse(xxe.replace("rss", "opml").byteInputStream()) }
    }

    @Test fun `PEX_23 OPML import keeps each http feed once`() {
        val subs = Opml.parse(Fixtures.read("podcasts/subs.opml").byteInputStream())
        assertEquals(listOf("https://a.example/feed.xml", "https://b.example/rss"), subs.map { it.url })
        assertEquals(listOf("Show A", "Show B"), subs.map { it.title })
        assertFalse(subs.first().toString().contains("feed.xml"))       // logs show the host only
    }

    @Test fun `PEX_23 subscriptions are per profile and capped`() = runBlocking {
        val secrets = MemorySecrets()
        val a = PodcastStore(secrets) { "a" }
        assertEquals(PodcastStore.MAX, a.save((1..40).map { Subscription("https://e.com/$it") }).size)
        assertTrue(PodcastStore(secrets) { "b" }.list().isEmpty())
        a.remove("https://e.com/1")
        assertEquals(PodcastStore.MAX - 1, a.list().size)
        a.save(emptyList())
        assertTrue(secrets.map.isEmpty())
    }

    private fun server() = FakeServer { r ->
        when (r.requestUrl!!.encodedPath) {
            "/show.xml" -> MockResponse().setBody(Fixtures.read("podcasts/show.xml"))
            "/other.xml" -> MockResponse().setBody(Fixtures.read("podcasts/other.xml"))
            "/subs.opml" -> MockResponse().setBody(Fixtures.read("podcasts/subs.opml")
                .replace("https://a.example/feed.xml", r.requestUrl!!.resolve("/show.xml").toString())
                .replace("https://b.example/rss", r.requestUrl!!.resolve("/other.xml").toString()))
            else -> null
        }
    }

    private fun provider(secrets: MemorySecrets = MemorySecrets(), policy: HttpPolicy = HttpPolicy.ANY) = PodcastProvider(
        SecretCredentials(secrets, PodcastProvider.ID, PodcastProvider.fields) { "p1" },
        PodcastStore(secrets) { "p1" }, ServerHttp(Http(null)), say = { "s$it" }, policy = policy,
    )

    private val profile = Profile(id = "p1", addons = setOf(PodcastProvider.ID))

    @Test fun `PEX_23 rows of new episodes across shows and one card per show, opening as audio`() = runBlocking {
        val s = server()
        try {
            val p = provider()
            p.subscribe(s.url("/show.xml"))
            p.subscribe(s.url("/other.xml"))
            val shelves = p.shelves(profile)
            assertEquals(listOf("podcasts:new", "podcasts:subs"), shelves.map { it.id })
            val new = shelves[0]
            assertEquals(ShelfStyle.WIDE, new.style)
            assertEquals(listOf("Other 1", "Episode 3: Newest", "Episode 2"), new.items.map { it.title })   // newest first
            assertEquals(OpenTarget.AppPage("https://cdn.example/o1.mp3", "", "audio/*"), new.items[0].open)
            val subs = shelves[1]
            assertEquals(listOf("The Sample Show", "Other Show"), subs.items.map { it.title })
            assertEquals("https://img.example/ep3.jpg", subs.items[0].posterUrl)
            assertTrue(p.shelves(profile.copy(contentLevel = ContentLevel.KIDS)).isEmpty())   // unrated: hidden for kids
            assertTrue(p.shelves(profile.copy(addons = emptySet())).isEmpty())
            val off = RowSources.withOn(profile, PodcastProvider.ID, p.rowSources()[0], false)
            assertEquals(listOf("podcasts:subs"), p.shelves(off).map { it.id })
        } finally { s.shutdown() }
    }

    @Test fun `PEX_23 the chosen podcast app gets the episode`() = runBlocking {
        val s = server()
        try {
            val secrets = MemorySecrets()
            val p = provider(secrets)
            SecretCredentials(secrets, PodcastProvider.ID, PodcastProvider.fields) { "p1" }.set("player", "com.podcast.app")
            p.subscribe(s.url("/show.xml"))
            val item = p.shelves(profile).first().items.first()
            assertEquals(OpenTarget.AppPage("https://cdn.example/ep3.mp3", "com.podcast.app", "audio/*"), item.open)
            assertEquals("com.podcast.app", item.appPackage)
        } finally { s.shutdown() }
    }

    @Test fun `PEX_23 OPML import by address, plain http refused, test counts working shows`() = runBlocking<Unit> {
        val s = server()
        try {
            val p = provider()
            assertFalse(p.test().ok)
            assertEquals(2, p.importOpml(s.url("/subs.opml")))
            val r = p.test()
            assertTrue(r.ok)
            assertEquals(listOf("The Sample Show", "Other Show"), p.shelves(profile).last().items.map { it.title })
            val strict = provider(policy = HttpPolicy.NONE)
            assertThrows(Exception::class.java) { runBlocking { strict.subscribe(s.url("/show.xml")) } }
        } finally { s.shutdown() }
    }
}
