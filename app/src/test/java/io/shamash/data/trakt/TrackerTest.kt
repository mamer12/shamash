package io.shamash.data.trakt

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.Logger
import io.shamash.data.MemorySecrets
import io.shamash.data.ProfileSlot
import io.shamash.data.SecretCredentials
import io.shamash.data.json
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.Signal
import io.shamash.domain.SignalKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TrackerTest {
    private var now = 1_767_800_000_000L   // 2026-01-07
    private var profile = Profile(id = "p1", addons = setOf(TrackerProvider.TRAKT, TrackerProvider.SIMKL))

    /** A fake Trakt: device flow (pending once), tokens -1/-2, a refresh that works or says invalid_grant. */
    private class Trakt {
        var polls = 0
        var refreshOk = true
        var refreshes = 0
        var refused = mutableSetOf<String>()
        val pushed = mutableListOf<String>()
        val server = FakeServer { r -> answer(r) }

        private fun authorized(r: RecordedRequest): Boolean {
            val token = r.getHeader("Authorization")?.removePrefix("Bearer ") ?: return false
            return token.startsWith("synthetic-access-") && token !in refused && r.getHeader("trakt-api-key") == "cid" && r.getHeader("trakt-api-version") == "2"
        }

        private fun answer(r: RecordedRequest): MockResponse? = when (r.requestUrl!!.encodedPath) {
            "/oauth/device/code" -> FakeServer.json(Fixtures.read("trakt/device_code.json"))
            "/oauth/device/token" -> if (polls++ == 0) MockResponse().setResponseCode(400) else FakeServer.json(Fixtures.read("trakt/token.json"))
            "/oauth/token" -> if (refreshOk) FakeServer.json(Fixtures.read("trakt/token_refreshed.json").replace("synthetic-access-2", "synthetic-access-${2 + refreshes++}"))
                else FakeServer.json(Fixtures.read("trakt/invalid_grant.json")).setResponseCode(400)
            "/sync/watchlist" -> if (authorized(r)) FakeServer.json(Fixtures.read("trakt/watchlist.json")) else MockResponse().setResponseCode(401)
            "/sync/history" -> when {
                !authorized(r) -> MockResponse().setResponseCode(401)
                r.method == "POST" -> { pushed += r.body.readUtf8(); MockResponse().setResponseCode(201).setBody("{}") }
                else -> FakeServer.json(Fixtures.read("trakt/history.json"))
            }
            else -> null
        }
    }

    private fun traktProvider(t: Trakt, secrets: MemorySecrets = MemorySecrets()): TrackerProvider {
        val api = TraktApi(TrackerHttp(Http(null).client), { now }, t.server.url("/").toHttpUrl())
        return TrackerProvider(
            TrackerProvider.TRAKT, api, SecretCredentials(secrets, TrackerProvider.TRAKT, TrackerFields.of(true)) { profile.id },
            ProfileSlot(secrets, "trakt_state", TrackerState.serializer(), TrackerState()) { profile.id }, { profile },
            say = { "s$it" }, clock = { now },
        )
    }

    private suspend fun signIn(p: TrackerProvider): Boolean {
        val code = p.startSignIn() ?: return false
        while (true) {
            when (p.poll(code)) {
                is PollAnswer.Granted -> return true
                PollAnswer.Failed -> return false
                else -> Unit
            }
        }
    }

    private suspend fun client(p: TrackerProvider, secrets: MemorySecrets) {
        val c = SecretCredentials(secrets, TrackerProvider.TRAKT, TrackerFields.of(true)) { profile.id }
        c.set(TrackerProvider.CLIENT_ID, "cid")
        c.set(TrackerProvider.CLIENT_SECRET, "synthetic-client-secret")
        assertNotNull(p.client())
    }

    @Test fun `PEX_26 device code sign-in stores a redacted per-profile session and imports the watchlist`() = runBlocking {
        val t = Trakt()
        try {
            val secrets = MemorySecrets()
            val p = traktProvider(t, secrets)
            assertNull(p.startSignIn())                               // no client yet
            client(p, secrets)
            val code = p.startSignIn()!!
            assertEquals("5055CC52", code.userCode)
            assertEquals("https://trakt.tv/activate", code.verificationUrl)
            assertFalse(code.toString().contains("synthetic-device-code"))
            assertEquals(PollAnswer.Pending, p.poll(code))
            assertTrue(p.poll(code) is PollAnswer.Granted)
            assertTrue(p.isSignedIn())
            val stored = secrets.map["p1_trakt_session"]!!
            assertTrue(stored.contains("synthetic-access-1"))
            assertFalse(json.decodeFromString(Session.serializer(), stored).toString().contains("synthetic"))
            val list = p.listEntries.first()
            assertEquals(listOf("Dune: Part Two", "Severance", "No TMDB"), list.map { it.title })    // season counts as its show, person skipped
            assertEquals(OpenTarget.Lookup(693134, Kind.MOVIE, "Dune: Part Two", ""), list[0].open)
            assertEquals("PG-13", list[0].rating)
            assertEquals(listOf("Science Fiction", "Adventure"), list[0].genres)
            assertEquals(OpenTarget.Internal("search?q=No+TMDB"), list[2].open)
            assertTrue(list.all { it.providerId == TrackerProvider.TRAKT })
            // Another profile has no sign-in of its own.
            profile = profile.copy(id = "p2")
            assertFalse(p.isSignedIn())
            assertTrue(p.listEntries.first().isEmpty())
        } finally { t.server.shutdown() }
    }

    @Test fun `PEX_26 watched titles become FINISH items once, only while someone records them`() = runBlocking {
        val t = Trakt()
        try {
            val secrets = MemorySecrets()
            val p = traktProvider(t, secrets)
            client(p, secrets)
            assertTrue(signIn(p))                                     // first sync ran with nobody listening
            val got = mutableListOf<MediaItem>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { p.finished.collect { got += it } }
            assertTrue(p.sync(profile, force = true))
            repeat(5) { yield() }
            assertEquals(listOf("Severance", "Arrival"), got.map { it.title })     // newest first, one per title
            assertEquals("trakt:series:tmdb95396", got[0].id)
            assertTrue(p.sync(profile, force = true))
            repeat(5) { yield() }
            assertEquals(2, got.size)                                // nothing new since the last mark
            assertTrue(t.server.seen.last { it.requestUrl!!.encodedPath == "/sync/history" }.requestUrl!!.queryParameter("start_at")!!.startsWith("2026-01-06T20:00"))
            assertFalse(p.sync(profile))                             // not due for 2 h
            job.cancel()
        } finally { t.server.shutdown() }
    }

    @Test fun `PEX_26 an expired token is refreshed, a refused one retried once, invalid_grant signs out`() = runBlocking {
        val t = Trakt()
        try {
            val secrets = MemorySecrets()
            val p = traktProvider(t, secrets)
            client(p, secrets)
            assertTrue(signIn(p))
            // Expired: renewed before the call.
            now += 91L * 86_400_000
            assertTrue(p.sync(profile, force = true))
            assertTrue(secrets.map["p1_trakt_session"]!!.contains("synthetic-access-2"))
            // Refused (401) although not expired: refresh, then retry with the new token.
            t.refused += "synthetic-access-2"
            assertTrue(p.sync(profile, force = true))
            assertTrue(secrets.map["p1_trakt_session"]!!.contains("synthetic-access-3"))
            assertFalse(p.listEntries.first().isEmpty())
            // The token is refused and so is the refresh (invalid_grant): signed out, imported list forgotten.
            t.refused += "synthetic-access-3"
            t.refreshOk = false
            assertFalse(p.sync(profile, force = true))
            assertFalse(p.isSignedIn())
            assertTrue(p.listEntries.first().isEmpty())
        } finally { t.server.shutdown() }
    }

    @Test fun `PEX_26 Kids and Guest never sign in or sync`() = runBlocking {
        val t = Trakt()
        try {
            val secrets = MemorySecrets()
            val p = traktProvider(t, secrets)
            client(p, secrets)
            for (kind in listOf(ProfileKind.KIDS, ProfileKind.GUEST)) {
                profile = profile.copy(kind = kind)
                assertFalse(p.isEnabled(profile))
                assertNull(p.startSignIn())
                assertFalse(p.sync(profile, force = true))
                assertTrue(p.shelves(profile).isEmpty())
                assertFalse(p.test().ok)
                assertEquals(0, p.writeBack(profile.id, listOf(finish("tmdb:movie:1", now))))
            }
            assertTrue(t.server.seen.isEmpty())
        } finally { t.server.shutdown() }
    }

    private fun finish(key: String, at: Long, provider: String = key.substringBefore(':')) =
        Signal("p1", key, provider, SignalKind.FINISH, at, 20)

    @Test fun `PEX_26 write-back is off by default, then pushes TMDB movies finished after it was switched on`() = runBlocking {
        val t = Trakt()
        try {
            val secrets = MemorySecrets()
            val p = traktProvider(t, secrets)
            client(p, secrets)
            assertTrue(signIn(p))
            val before = now - 1000
            val signals = listOf(finish("tmdb:movie:550", before))
            assertFalse(p.writeBackOn())
            assertEquals(0, p.writeBack("p1", signals))
            p.setWriteBack(true)
            assertEquals(0, p.writeBack("p1", signals))                 // finished before write-back was on
            now += 60_000
            val later = signals + finish("tmdb:movie:603", now) + finish("tmdb:series:1399", now) + finish("trakt:movie:tmdb1", now) +
                Signal("p1", "tmdb:movie:604", "tmdb", SignalKind.OPEN, now, 20)
            assertEquals(1, p.writeBack("p1", later))
            assertTrue(t.pushed.single().contains(""""ids":{"tmdb":603}"""))
            assertEquals(0, p.writeBack("p1", later))                   // pushed once
            assertEquals(0, p.writeBack("other-profile", later))
        } finally { t.server.shutdown() }
    }

    @Test fun `PEX_26 removing a tracker title on the TV keeps it out of later imports`() {
        val before = TrackerState(dismissed = listOf("trakt:movie:tmdb693134"), since = 5)
        val lib = Library(TrackerJson.traktWatchlist(Fixtures.read("trakt/watchlist.json")), TrackerJson.traktHistory(Fixtures.read("trakt/history.json")))
        val plan = TrackerSync.plan("trakt", before, lib, listening = false)
        assertEquals(listOf("Severance", "No TMDB"), plan.state.list.map { it.title })
        assertTrue(plan.finished.isEmpty())
        assertEquals(5, plan.state.since)                           // history waits for someone to record it
        val heard = TrackerSync.plan("trakt", before, lib, listening = true)
        assertEquals(2, heard.finished.size)
        assertEquals(TrackerJson.instant("2026-01-06T20:00:00.000Z"), heard.state.since)
    }

    @Test fun `PEX_26 Simkl PIN sign-in, library in one call, a refused token signs out`() = runBlocking {
        var granted = false
        var revoked = false
        val s = FakeServer { r ->
            assertEquals("cid", r.requestUrl!!.queryParameter("client_id"))
            when (r.requestUrl!!.encodedPath) {
                "/oauth/pin" -> FakeServer.json(Fixtures.read("trakt/simkl_pin.json"))
                "/oauth/pin/ABCDE" -> FakeServer.json(Fixtures.read(if (granted) "trakt/simkl_pin_ok.json" else "trakt/simkl_pin_pending.json")).also { granted = true }
                "/sync/all-items/" -> if (!revoked && r.getHeader("Authorization") == "Bearer synthetic-simkl-token" && r.getHeader("simkl-api-key") == "cid")
                    FakeServer.json(Fixtures.read("trakt/simkl_all.json")) else MockResponse().setResponseCode(401)
                else -> null
            }
        }
        try {
            val secrets = MemorySecrets()
            val creds = SecretCredentials(secrets, TrackerProvider.SIMKL, TrackerFields.of(false)) { profile.id }
            val p = TrackerProvider(
                TrackerProvider.SIMKL, SimklApi(TrackerHttp(Http(null).client), { now }, s.url("/").toHttpUrl()), creds,
                ProfileSlot(secrets, "simkl_state", TrackerState.serializer(), TrackerState()) { profile.id }, { profile },
                say = { "s$it" }, clock = { now },
            )
            creds.set(TrackerProvider.CLIENT_ID, "cid")                 // Simkl needs no client secret
            val code = p.startSignIn()!!
            assertEquals("ABCDE", code.userCode)
            assertEquals(PollAnswer.Pending, p.poll(code))
            assertTrue(p.poll(code) is PollAnswer.Granted)
            assertEquals(listOf("Dune: Part Two", "Shogun"), p.listEntries.first().map { it.title })
            assertEquals("simkl:series:id666", p.listEntries.first()[1].itemKey)
            assertTrue(p.test().ok)
            revoked = true
            assertFalse(p.sync(profile, force = true))
            assertFalse(p.isSignedIn())
        } finally { s.shutdown() }
    }

    @Test fun `PEX_26 Simkl history counts completed and watching titles after the mark`() {
        val since = TrackerJson.instant("2025-12-31T00:00:00Z")!!
        val lib = TrackerJson.simklLibrary(Fixtures.read("trakt/simkl_all.json"), since)
        assertEquals(listOf("Severance", "Arrival"), lib.watched.map { it.title })   // "Old One" before the mark, dropped ignored
        assertEquals(Kind.SERIES, lib.watched[0].kind)
        assertEquals(329865, lib.watched[1].tmdbId)
        assertEquals(3, TrackerJson.simklLibrary(Fixtures.read("trakt/simkl_all.json"), 0).watched.size)
        assertTrue(TrackerJson.simklLibrary("not json", 0).watchlist.isEmpty())
    }

    @Test fun `PEX_04 PEX_26 tokens and secrets never reach logs or toString`() {
        val session = Session("synthetic-access-x", "synthetic-refresh-x", 1)
        val client = Client("cid", "synthetic-client-secret")
        for (text in listOf(session.toString(), client.toString(), Logger.line("trakt sync", IOException("Bearer synthetic-access-x")))) {
            assertFalse(text, text.contains("synthetic"))
        }
        assertEquals("""{"movies":[{"watched_at":"2026-01-07T15:33:20Z","ids":{"tmdb":550}}]}""", TrackerJson.historyBody(listOf(550 to 1_767_800_000_000L)))
        assertNull(TrackerJson.session("{}", 0))
        assertNull(TrackerJson.deviceCode("""{"user_code":"x"}""", 0))
    }
}
