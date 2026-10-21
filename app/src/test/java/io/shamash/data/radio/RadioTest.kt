package io.shamash.data.radio

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.json
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.RowSources
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class RadioTest {
    @Test fun `PEX_24 Radio Browser stations keep a usable stream once each, broken ones dropped`() {
        val stations = RadioBrowser.parse(json.parseToJsonElement(Fixtures.read("radio/stations.json")))
        assertEquals(listOf("s-1", "s-2"), stations.map { it.id })
        assertEquals("https://stream.example/news.mp3", stations[0].url)        // url_resolved first
        assertEquals("https://q.example/live", stations[1].url)                 // else url
        assertEquals("https://img.example/news.png", stations[0].logo)
        assertNull(stations[1].logo)
        assertTrue(RadioBrowser.parse(json.parseToJsonElement("{}")).isEmpty())
    }

    @Test fun `PEX_24 favorites per profile, toggled, newest first and capped`() = runBlocking {
        val secrets = MemorySecrets()
        val a = RadioFavorites(secrets) { "a" }
        val s1 = Station("1", "One", "https://x/1")
        val s2 = Station("2", "Two", "https://x/2")
        a.toggle(s1)
        assertEquals(listOf("2", "1"), a.toggle(s2).map { it.id })
        assertEquals(listOf("2"), a.toggle(s1).map { it.id })
        assertTrue(RadioFavorites(secrets) { "b" }.list().isEmpty())
        repeat(60) { a.toggle(Station("n$it", "n", "https://x/$it")) }
        assertEquals(RadioFavorites.MAX, a.list().size)
    }

    private fun server() = FakeServer { r ->
        if (r.requestUrl!!.encodedPath == "/json/stations/search") FakeServer.json(Fixtures.read("radio/stations.json")) else null
    }

    @Test fun `PEX_24 rows of favorites and the country's most played, opening in the radio app as audio`() = runBlocking {
        val s = server()
        try {
            val secrets = MemorySecrets()
            val creds = SecretCredentials(secrets, RadioProvider.ID, RadioProvider.fields) { "p1" }
            val p = RadioProvider(creds, RadioBrowser(ServerHttp(Http(null)), s.url("/").toHttpUrl()), RadioFavorites(secrets) { "p1" }, say = { "s$it" })
            val profile = Profile(id = "p1", addons = setOf(RadioProvider.ID), region = "IQ")
            assertEquals(listOf("radio:top"), p.shelves(profile).map { it.id })     // no favorites yet, tag row off by default
            val query = s.seen.last().requestUrl!!
            assertEquals("IQ", query.queryParameter("countrycode"))
            assertEquals("true", query.queryParameter("hidebroken"))
            creds.set("player", "com.radio.app")
            p.toggleFavorite(Station("s-9", "Mine", "https://mine.example/live"))
            val withTag = RowSources.withOn(profile, RadioProvider.ID, p.rowSources()[2], true)
            creds.set("tag", "news")
            val shelves = p.shelves(withTag)
            assertEquals(listOf("radio:fav", "radio:top", "radio:tag"), shelves.map { it.id })
            assertEquals("s${io.shamash.R.string.more_radio_row_tag_named}", shelves[2].title)
            assertEquals("news", s.seen.last { it.requestUrl!!.queryParameter("tag") != null }.requestUrl!!.queryParameter("tag"))
            assertEquals(OpenTarget.AppPage("https://mine.example/live", "com.radio.app", "audio/*"), shelves[0].items.single().open)
            assertTrue(p.test().ok)
        } finally { s.shutdown() }
    }

    @Test fun `PEX_24 a bad country code is not sent and a failing service gives no rows`() = runBlocking {
        val s = FakeServer { null }
        try {
            val secrets = MemorySecrets()
            val creds = SecretCredentials(secrets, RadioProvider.ID, RadioProvider.fields) { "p1" }
            creds.set("country", "Iraq!")
            val p = RadioProvider(creds, RadioBrowser(ServerHttp(Http(null)), s.url("/").toHttpUrl()), RadioFavorites(secrets) { "p1" }, say = { "s$it" })
            assertTrue(p.shelves(Profile(id = "p1", addons = setOf(RadioProvider.ID))).isEmpty())
            assertNull(s.seen.first().requestUrl!!.queryParameter("countrycode"))
            assertFalse(p.test().ok)
        } finally { s.shutdown() }
    }
}
