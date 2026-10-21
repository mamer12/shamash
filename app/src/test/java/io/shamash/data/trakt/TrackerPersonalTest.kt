package io.shamash.data.trakt

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.MemorySecrets
import io.shamash.data.ProfileSlot
import io.shamash.data.SecretCredentials
import io.shamash.data.signals.PersonalStore
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.SignalKind
import io.shamash.feature.personalization.Personalization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The tracker as the personalization store sees it: watchlist in My List, watched titles as FINISH. SPEC: PEX-26, PER-02, PER-14 */
class TrackerPersonalTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `PEX_26 an import reaches My List and the signals of an adult profile`() = runBlocking {
        val server = FakeServer { r ->
            when (r.requestUrl!!.encodedPath) {
                "/sync/watchlist" -> FakeServer.json(Fixtures.read("trakt/watchlist.json"))
                "/sync/history" -> FakeServer.json(Fixtures.read("trakt/history.json"))
                else -> MockResponse().setResponseCode(404)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val profile = MutableStateFlow(Profile(id = "p1", addons = setOf(TrackerProvider.TRAKT)))
            val secrets = MemorySecrets()
            val creds = SecretCredentials(secrets, TrackerProvider.TRAKT, TrackerFields.of(true)) { profile.value.id }
            creds.set(TrackerProvider.CLIENT_ID, "cid")
            creds.set(TrackerProvider.CLIENT_SECRET, "synthetic-secret")
            creds.set(TrackerProvider.SESSION, """{"access":"synthetic-access-1"}""")
            val tracker = TrackerProvider(
                TrackerProvider.TRAKT, TraktApi(TrackerHttp(Http(null).client), base = server.url("/").toHttpUrl()), creds,
                ProfileSlot(secrets, "trakt_state", TrackerState.serializer(), TrackerState()) { profile.value.id }, { profile.value },
                say = { "s$it" },
            )
            val store = PersonalStore(folder.root)
            val personal = Personalization(store, profile, { listOf(tracker) }, scope)
            personal.start()
            delay(200)                                     // the collectors are up
            assertTrue(tracker.sync(profile.value, force = true))
            val data = withTimeout(5_000) {
                while (true) {
                    val d = store.read("p1")
                    if (d.myList.size == 3 && d.signals.size == 2) return@withTimeout d
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            assertEquals(setOf("Dune: Part Two", "Severance", "No TMDB"), data.myList.map { it.title }.toSet())
            assertTrue(data.signals.all { it.kind == SignalKind.FINISH && it.providerId == TrackerProvider.TRAKT })
            assertEquals(setOf("trakt:series:tmdb95396", "trakt:movie:tmdb329865"), data.signals.map { it.itemKey }.toSet())

            // A Kids profile records nothing and the tracker doesn't sync for it.
            profile.value = Profile(id = "k1", kind = ProfileKind.KIDS, addons = setOf(TrackerProvider.TRAKT))
            assertFalse(tracker.sync(profile.value, force = true))
            delay(200)
            assertTrue(store.read("k1").signals.isEmpty())
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }
}
