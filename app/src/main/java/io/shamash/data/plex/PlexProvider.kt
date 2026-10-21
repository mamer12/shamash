package io.shamash.data.plex

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.data.child
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.Profile
import io.shamash.domain.RowSource
import io.shamash.domain.RowSources
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import okhttp3.HttpUrl

/**
 * Plex: Continue watching (on deck), Recently added and up to six libraries from the user's own server. Items open in
 * the Plex app; Shamash has no player for them. Requests are private (never cached) and carry the token in a header.
 * SPEC: PEX-04, PEX-10, PEX-12, PEX-13
 */
class PlexProvider(
    private val credentials: ProviderCredentials,
    private val server: ServerHttp,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ID, say, clock) {
    @Volatile private var libraries: List<PlexLibrary> = emptyList()
    @Volatile private var machineId: String? = null

    override fun rowSources(): List<RowSource> = listOf(
        RowSource("deck", R.string.pex_row_continue),
        RowSource("recent", R.string.pex_row_recent),
    ) + libraries.take(MAX_LIBRARIES).mapIndexed { i, l -> RowSource("lib${l.key}", titleText = l.title, defaultOn = i < 2) }

    private data class Conn(val base: HttpUrl, val token: String)

    private suspend fun conn(): Conn? {
        val base = ServerUrl.parse(credentials.get("url").orEmpty(), HttpPolicy.PRIVATE_ONLY) ?: return null
        return Conn(base, credentials.get("token") ?: return null)
    }

    private suspend fun fetch(c: Conn, path: String, query: Map<String, String> = emptyMap()) =
        server.json(c.base.child(path, query), mapOf("Accept" to "application/json", "X-Plex-Token" to c.token))

    private suspend fun load(c: Conn, path: String, query: Map<String, String> = emptyMap()) =
        PlexJson.items(fetch(c, path, query + ("X-Plex-Container-Size" to "20")), c.base, c.token, machineId, ID)

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val c = conn() ?: return emptyList()
        discover(c)
        val shelves = wanted.mapNotNull { s ->
            source(s, if (s.id == "deck") ShelfStyle.WIDE else ShelfStyle.POSTER) {
                when {
                    s.id == "deck" -> load(c, "library/onDeck")
                    s.id == "recent" -> load(c, "library/recentlyAdded")
                    else -> load(c, "library/sections/${s.id.removePrefix("lib")}/recentlyAdded")
                }
            }
        }
        // Libraries are only known after the first look: show the ones switched on now.
        val late = RowSources.capped(rowSources()).filter { it.id.startsWith("lib") && it !in wanted && RowSources.isOn(profile, ID, it) }
        return shelves + late.mapNotNull { s -> source(s, ShelfStyle.POSTER) { load(c, "library/sections/${s.id.removePrefix("lib")}/recentlyAdded") } }
    }

    private suspend fun discover(c: Conn) {
        runCatching { machineId = machineId ?: PlexJson.machineId(fetch(c, "identity")) }
        runCatching { libraries = PlexJson.libraries(fetch(c, "library/sections")) }
    }

    override suspend fun test(): TestResult {
        val c = conn() ?: return failed(R.string.pex_test_bad_address)
        return try {
            val id = PlexJson.machineId(fetch(c, "identity"))      // asked afresh: the test must not pass on old answers
            if (id == null) failed(R.string.pex_test_failed)
            else {
                machineId = id
                discover(c)
                TestResult(true, say(R.string.pex_test_ok_plex))
            }
        } catch (e: Exception) {
            failed(R.string.pex_test_failed)
        }
    }

    companion object {
        const val ID = "plex"
        private const val MAX_LIBRARIES = 6

        val fields = listOf(
            CredentialField("url", R.string.pex_plex_url, CredentialKind.URL, hintRes = R.string.pex_plex_url_hint),
            CredentialField("token", R.string.pex_plex_token, CredentialKind.TOKEN, hintRes = R.string.pex_plex_token_hint),
        )

        /** The registration (PEX-01): one line in `Sources.addOns`. */
        val addOn = AddOn(ID, R.string.pex_plex_name, R.string.pex_plex_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            PlexProvider(deps.credentials, ServerHttp(http), say = { id -> context?.getString(id).orEmpty() })
        }
    }
}
