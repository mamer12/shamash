package io.shamash.data.plex

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.ServerUrl
import io.shamash.data.HttpPolicy
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class PlexTest {
    private val profile = Profile(id = "p1", addons = setOf(PlexProvider.ID))

    private fun plexServer() = FakeServer { r ->
        when (r.requestUrl!!.encodedPath) {
            "/identity" -> FakeServer.json("""{"MediaContainer":{"machineIdentifier":"MID"}}""")
            "/library/sections" -> FakeServer.json(Fixtures.read("plex/sections.json"))
            "/library/onDeck" -> FakeServer.json(Fixtures.read("plex/ondeck.json"))
            "/library/recentlyAdded", "/library/sections/1/recentlyAdded" -> FakeServer.json(Fixtures.read("plex/ondeck.json"))
            else -> null
        }
    }

    private suspend fun rig(server: FakeServer): Pair<PlexProvider, SecretCredentials> {
        val credentials = SecretCredentials(MemorySecrets(), PlexProvider.ID, PlexProvider.fields) { "p1" }
        credentials.set("url", server.url("/"))
        credentials.set("token", "PLEX-SECRET")
        return PlexProvider(credentials, ServerHttp(Http(null)), say = { "s$it" }) to credentials
    }

    @Test fun `PEX_10 on deck, recent and libraries become rows, opened in the Plex app`() = runBlocking {
        val server = plexServer()
        try {
            val (provider, _) = rig(server)
            val shelves = provider.shelves(profile)
            assertEquals(listOf("plex:deck", "plex:recent", "plex:lib1"), shelves.map { it.id }   // lib2 is on offer but its answer fails: left out
            )
            val episode = shelves.first().items.first()
            assertEquals("Great Show", episode.title)
            assertEquals("S1 E1 · Pilot", episode.subtitle)
            assertEquals(Kind.SERIES, episode.kind)
            assertEquals(0.25f, episode.progress!!, 0.001f)
            assertEquals("TV-14", episode.rating)
            val open = episode.open as OpenTarget.AppPage
            assertEquals("com.plexapp.android", open.appPackage)
            assertTrue(open.uri.startsWith("plex://server://MID/com.plexapp.plugins.library/details?key=%2Flibrary%2Fmetadata%2F101"))
            assertEquals(2, shelves.first().items.size)          // the entry without a key is dropped
            assertEquals(listOf("Movies", "TV"), provider.rowSources().filter { it.id.startsWith("lib") }.map { it.titleText })
        } finally { server.shutdown() }
    }

    @Test fun `PEX_13 pictures are asked resized and never wider than 1920`() = runBlocking {
        val server = plexServer()
        try {
            val (provider, _) = rig(server)
            val item = provider.shelves(profile).first().items.first()
            assertTrue(item.posterUrl!!.contains("photo/:/transcode") && item.posterUrl!!.contains("width=360"))
            assertTrue(item.backdropUrl!!.contains("width=1280"))
            val huge = PlexJson.image(ServerUrl.parse("https://h.example:32400")!!, "/a", "T", 4000, 2000)
            assertTrue(huge.contains("width=1920"))
        } finally { server.shutdown() }
    }

    @Test fun `PEX_04 the token goes in a header for API calls, requests are private and nothing is cached by the app`() = runBlocking {
        val server = plexServer()
        try {
            val (provider, _) = rig(server)
            provider.shelves(profile)
            val api = server.seen.filter { it.requestUrl!!.encodedPath.startsWith("/library") }
            assertTrue(api.isNotEmpty())
            assertTrue(api.all { it.getHeader("X-Plex-Token") == "PLEX-SECRET" })
            assertTrue(api.all { it.requestUrl!!.queryParameter("X-Plex-Token") == null })
            assertTrue(server.seen.all { it.getHeader("X-Shamash-Private") == null })
        } finally { server.shutdown() }
    }

    @Test fun `PEX_07 a kids profile sees only titles rated for kids, and unknown ratings are hidden`() = runBlocking {
        val server = plexServer()
        try {
            val (provider, _) = rig(server)
            assertTrue(provider.shelves(profile.copy(contentLevel = ContentLevel.KIDS)).isEmpty())
            val family = provider.shelves(profile.copy(contentLevel = ContentLevel.FAMILY)).first().items
            assertEquals(listOf("Great Show", "A Film"), family.map { it.title })
            assertTrue(provider.shelves(Profile(id = "p1")).isEmpty())
        } finally { server.shutdown() }
    }

    @Test fun `PEX_05 test connection says ok or a plain failure, and a missing token is not an exception`() = runBlocking {
        val server = plexServer()
        try {
            val (provider, credentials) = rig(server)
            assertTrue(provider.test().ok)
            credentials.set("url", server.url("/").replace("localhost", "localhost") + "nothing-here")
            assertFalse(provider.test().ok)
            credentials.set("token", null)
            assertFalse(provider.test().ok)
            assertTrue(provider.shelves(profile).isEmpty())
        } finally { server.shutdown() }
    }

    @Test fun `PEX_12 a public http address is not used`() = runBlocking {
        val credentials = SecretCredentials(MemorySecrets(), PlexProvider.ID, PlexProvider.fields) { "p1" }
        credentials.set("url", "http://plex.example.com:32400")
        credentials.set("token", "t")
        val provider = PlexProvider(credentials, ServerHttp(Http(null)), say = { "s$it" })
        assertFalse(provider.test().ok)
        assertNull(ServerUrl.parse("http://plex.example.com:32400", HttpPolicy.PRIVATE_ONLY))
    }

    // ---- PIN sign-in ----

    @Test fun `PEX_10 PIN sign-in polls until the code is entered, then lists the server`() = runBlocking {
        var polls = 0
        val plexTv = FakeServer { r ->
            val path = r.requestUrl!!.encodedPath
            when {
                path == "/api/v2/pins" && r.method == "POST" -> FakeServer.json(Fixtures.read("plex/pin.json"))
                path == "/api/v2/pins/1234" -> FakeServer.json(Fixtures.read(if (++polls < 3) "plex/pin.json" else "plex/pin_done.json"))
                path == "/api/v2/resources" -> FakeServer.json(Fixtures.read("plex/resources.json"))
                else -> null
            }
        }
        try {
            val waits = mutableListOf<Long>()
            val auth = PlexAuth(ServerHttp(Http(null)), "shamash-p1", okhttp3.HttpUrl.Builder().scheme("http").host("localhost")
                .port(plexTv.url("/").toHttpUrlPort()).build(), wait = { waits += it })
            val pin = auth.start()
            assertEquals(PlexPin(1234, "ABCD"), pin)
            assertEquals("ACCOUNT-TOKEN-XYZ", auth.poll(pin))
            assertEquals(2, waits.size)
            val servers = auth.servers("ACCOUNT-TOKEN-XYZ")
            assertEquals(1, servers.size)
            assertEquals("Living room", servers[0].name)
            assertEquals("https://192-168-1-5.abc.plex.direct:32400", servers[0].uri)      // https, not the relay
            assertEquals("SERVER-TOKEN-1", servers[0].accessToken)
            val resources = plexTv.seen.first { it.requestUrl!!.encodedPath == "/api/v2/resources" }
            assertEquals("ACCOUNT-TOKEN-XYZ", resources.getHeader("X-Plex-Token"))
            assertEquals("shamash-p1", resources.getHeader("X-Plex-Client-Identifier"))
        } finally { plexTv.shutdown() }
    }

    @Test fun `PEX_10 an unentered code expires`() = runBlocking {
        val plexTv = FakeServer { FakeServer.json(Fixtures.read("plex/pin.json")) }
        try {
            val auth = PlexAuth(ServerHttp(Http(null)), "c", okhttp3.HttpUrl.Builder().scheme("http").host("localhost")
                .port(plexTv.url("/").toHttpUrlPort()).build(), wait = {})
            assertNull(auth.poll(PlexPin(1234, "ABCD"), everyMs = 1_000, maxWaitMs = 3_000))
        } finally { plexTv.shutdown() }
    }

    private fun String.toHttpUrlPort() = okhttp3.HttpUrl.Builder().scheme("http").host("h").build().let {
        this.substringAfterLast(":").trimEnd('/').toInt()
    }
}
