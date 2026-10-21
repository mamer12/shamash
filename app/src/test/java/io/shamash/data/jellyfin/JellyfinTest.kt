package io.shamash.data.jellyfin

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JellyfinTest {
    private val profile = Profile(id = "p1", addons = setOf(JellyfinProvider.ID))

    private fun jf() = FakeServer { r ->
        when (r.requestUrl!!.encodedPath) {
            "/Users/AuthenticateByName" -> FakeServer.json(Fixtures.read("jellyfin/auth.json"))
            "/Users/user-1/Views" -> FakeServer.json(Fixtures.read("jellyfin/views.json"))
            "/Users/user-1/Items/Resume" -> FakeServer.json(Fixtures.read("jellyfin/resume.json"))
            "/Shows/NextUp" -> FakeServer.json("""{"Items":[]}""")
            "/Users/user-1/Items/Latest" -> FakeServer.json(Fixtures.read("jellyfin/latest.json"))
            else -> null
        }
    }

    private fun provider(creds: SecretCredentials, installed: String = "org.jellyfin.androidtv") =
        JellyfinProvider(creds, ServerHttp(Http(null)), { installed }, say = { "s$it" })

    private suspend fun creds(server: FakeServer, store: MemorySecrets = MemorySecrets()): SecretCredentials {
        val c = SecretCredentials(store, JellyfinProvider.ID, JellyfinProvider.fields) { "p1" }
        c.set("url", server.url("/"))
        c.set("username", "ann")
        c.set("password", "PASSWORD-1")
        return c
    }

    @Test fun `PEX_11 the password is exchanged for a token and then forgotten`() = runBlocking {
        val server = jf()
        try {
            val c = creds(server)
            val shelves = provider(c).shelves(profile)
            assertNull(c.get("password"))
            assertEquals("user-1:JF-TOKEN-ABC", c.get("token"))
            val login = server.seen.first { it.requestUrl!!.encodedPath == "/Users/AuthenticateByName" }
            assertTrue(login.body.readUtf8().contains("PASSWORD-1"))
            assertTrue(login.getHeader("X-Emby-Authorization")!!.startsWith("MediaBrowser Client="))
            val later = server.seen.first { it.requestUrl!!.encodedPath.endsWith("/Items/Resume") }
            assertEquals("JF-TOKEN-ABC", later.getHeader("X-Emby-Token"))
            assertNull(later.requestUrl!!.queryParameter("api_key"))
            assertEquals(listOf("jellyfin:resume", "jellyfin:libs-placeholder").first(), shelves.first().id)
        } finally { server.shutdown() }
    }

    @Test fun `PEX_11 resume, next up and the latest of each library`() = runBlocking {
        val server = jf()
        try {
            val c = creds(server)
            val shelves = provider(c).shelves(profile)
            assertEquals(listOf("jellyfin:resume", "jellyfin:liblib1", "jellyfin:liblib2"), shelves.map { it.id }.filter { it != "jellyfin:nextup" })
            val resume = shelves.first { it.id == "jellyfin:resume" }.items
            assertEquals(listOf("Great Show", "A Film"), resume.map { it.title })
            val ep = resume[0]
            assertEquals("S2 E3 · Pilot", ep.subtitle)
            assertEquals(Kind.SERIES, ep.kind)
            assertEquals(0.25f, ep.progress!!, 0.001f)
            assertEquals("TV-PG", ep.rating)
            assertEquals(OpenTarget.App("org.jellyfin.androidtv"), ep.open)
            assertTrue(ep.posterUrl!!.contains("/Items/s1/Images/Primary") && ep.posterUrl!!.contains("maxWidth=360"))
            assertTrue(ep.backdropUrl!!.contains("/Items/s1/Images/Backdrop") && ep.backdropUrl!!.contains("maxWidth=1280"))
            assertTrue(resume[1].posterUrl!!.contains("/Items/m1/"))
            assertFalse(shelves.any { it.id == "jellyfin:nextup" })      // empty rows are left out
        } finally { server.shutdown() }
    }

    @Test fun `PEX_11 Emby app is used when only it is installed`() = runBlocking {
        val server = jf()
        try {
            val shelves = provider(creds(server), installed = "tv.emby.embyatv").shelves(profile)
            assertEquals(OpenTarget.App("tv.emby.embyatv"), shelves.first().items.first().open)
        } finally { server.shutdown() }
    }

    @Test fun `PEX_07 content level applies to ratings`() = runBlocking {
        val server = jf()
        try {
            val c = creds(server)
            assertTrue(provider(c).shelves(profile.copy(contentLevel = ContentLevel.KIDS)).none { s -> s.items.any { it.rating == "R" } })
            val family = provider(c).shelves(profile.copy(contentLevel = ContentLevel.FAMILY))
            assertTrue(family.flatMap { it.items }.none { it.rating == "R" })
            assertTrue(family.flatMap { it.items }.any { it.rating == "TV-PG" })
        } finally { server.shutdown() }
    }

    @Test fun `PEX_05 test connection signs in and says no secret`() = runBlocking {
        val server = jf()
        try {
            val r = provider(creds(server)).test()
            assertTrue(r.ok)
            assertFalse(r.message.contains("PASSWORD-1"))
            val wrong = FakeServer { null }
            val c2 = creds(wrong)
            val r2 = provider(c2).test()
            assertFalse(r2.ok)
            assertFalse(r2.message.contains("PASSWORD-1"))
            wrong.shutdown()
        } finally { server.shutdown() }
    }

    @Test fun `PEX_11 login answers map to ids and a bad one to null`() {
        assertEquals("user-1" to "JF-TOKEN-ABC", JellyfinJson.login(io.shamash.data.json.parseToJsonElement(Fixtures.read("jellyfin/auth.json"))))
        assertNull(JellyfinJson.login(io.shamash.data.json.parseToJsonElement("""{"error":"x"}""")))
    }
}
