package io.shamash.data.live

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.json
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class LiveTest {
    @Test fun `PEX_25 follows are parsed from names and channel addresses`() {
        assertEquals(LiveFollow("twitch", "streamer_one"), LiveFollow.parse("twitch:Streamer_One"))
        assertEquals(LiveFollow("kick", "kick-star"), LiveFollow.parse(" kick: kick-star "))
        assertEquals(LiveFollow("twitch", "abc"), LiveFollow.parse("https://www.twitch.tv/abc"))
        assertEquals(LiveFollow("kick", "xyz"), LiveFollow.parse("kick.com/xyz/"))
        assertNull(LiveFollow.parse("youtube:someone"))
        assertNull(LiveFollow.parse("twitch:a"))                 // too short for Twitch
        assertNull(LiveFollow.parse("twitch:bad name"))
        assertNull(LiveFollow.parse("twitch:../../x"))
    }

    @Test fun `PEX_25 Helix and Kick answers give live streams only`() {
        val tw = LiveJson.twitch(json.parseToJsonElement(Fixtures.read("live/twitch_streams.json")))
        assertEquals(listOf("streamer_one"), tw.map { it.login })        // a non-live (rerun) entry is dropped
        assertEquals("https://static-cdn.jtvnw.net/previews-ttv/live_user_streamer_one-1280x720.jpg", tw[0].thumbnail)
        assertEquals("Chess", tw[0].category)
        val kick = LiveJson.kick("kickstar", json.parseToJsonElement(Fixtures.read("live/kick_live.json")))!!
        assertEquals("KickStar", kick.name)
        assertEquals(2400L, kick.viewers)
        assertNull(LiveJson.kick("sleepy", json.parseToJsonElement(Fixtures.read("live/kick_offline.json"))))
        assertEquals("synthetic-app-token" to 5011271L, LiveJson.appToken(Fixtures.read("live/twitch_token.json")))
        assertNull(LiveJson.appToken("{}"))
    }

    private fun server(tokenCalls: MutableList<String> = mutableListOf()) = FakeServer { r ->
        when (r.requestUrl!!.encodedPath) {
            "/oauth2/token" -> { tokenCalls += r.body.readUtf8(); FakeServer.json(Fixtures.read("live/twitch_token.json")) }
            "/helix/streams" -> if (r.getHeader("Authorization") == "Bearer synthetic-app-token" && r.getHeader("Client-Id") == "cid")
                FakeServer.json(Fixtures.read("live/twitch_streams.json")) else MockResponse().setResponseCode(401)
            "/api/v2/channels/kickstar" -> FakeServer.json(Fixtures.read("live/kick_live.json"))
            "/api/v2/channels/sleepy" -> FakeServer.json(Fixtures.read("live/kick_offline.json"))
            else -> null
        }
    }

    private fun provider(s: FakeServer, secrets: MemorySecrets): LiveProvider {
        val http = Http(null)
        val server = ServerHttp(http)
        val base = s.url("/").toHttpUrl()
        return LiveProvider(
            SecretCredentials(secrets, LiveProvider.ID, LiveProvider.fields) { "p1" }, LiveFollows(secrets) { "p1" },
            TwitchApi(http.client, server, api = base, auth = base), KickApi(server, base), say = { "s$it" },
        )
    }

    private val profile = Profile(id = "p1", addons = setOf(LiveProvider.ID))

    @Test fun `PEX_25 Live now lists followed streamers who are live, most watched first, offline hidden`() = runBlocking {
        val calls = mutableListOf<String>()
        val s = server(calls)
        try {
            val secrets = MemorySecrets()
            val p = provider(s, secrets)
            p.saveFollows(listOfNotNull(LiveFollow.parse("twitch:streamer_one"), LiveFollow.parse("twitch:rerun_guy"), LiveFollow.parse("kick:kickstar"), LiveFollow.parse("kick:sleepy")))
            // Without a Twitch client only Kick is asked.
            assertEquals(listOf("live:kick:kickstar"), p.shelves(profile).single().items.map { it.id })
            val creds = SecretCredentials(secrets, LiveProvider.ID, LiveProvider.fields) { "p1" }
            creds.set("twitch-client", "cid")
            creds.set("twitch-secret", "synthetic-secret")
            p.saveFollows(p.follows() + LiveFollow("twitch", "nobody_here"))   // new follows: rows are fetched again
            val items = p.shelves(profile).single().items
            assertEquals(listOf("live:kick:kickstar", "live:twitch:streamer_one"), items.map { it.id })
            assertEquals(Kind.LIVE, items[0].kind)
            assertEquals(OpenTarget.AppPage("https://kick.com/kickstar", ""), items[0].open)
            assertEquals(OpenTarget.AppPage("twitch://stream/streamer_one", ""), items[1].open)
            assertEquals("Chess · Blitz all day", items[1].subtitle)
            assertTrue(p.featured(profile).isEmpty())
            assertTrue(p.shelves(profile.copy(contentLevel = ContentLevel.FAMILY)).isEmpty())   // streams are unrated
            assertEquals(1, calls.size)                                         // the app token is reused
            assertTrue(s.seen.filter { it.requestUrl!!.encodedPath == "/helix/streams" }.all { it.requestUrl!!.queryParameterValues("user_login").size == 3 })
            assertTrue(p.test().ok)
        } finally { s.shutdown() }
    }

    @Test fun `PEX_25 test asks for the Twitch client only when someone on Twitch is followed`() = runBlocking {
        val s = server()
        try {
            val p = provider(s, MemorySecrets())
            assertFalse(p.test().ok)
            p.saveFollows(listOf(LiveFollow("twitch", "streamer_one")))
            assertEquals("s${io.shamash.R.string.more_live_test_twitch_client}", p.test().message)
            p.saveFollows(listOf(LiveFollow("kick", "kickstar")))
            assertTrue(p.test().ok)
        } finally { s.shutdown() }
    }
}
