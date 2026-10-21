package io.shamash.data.jellyfin

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.data.child
import io.shamash.data.jStr
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Profile
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.RowSources
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import okhttp3.HttpUrl

/**
 * Jellyfin and Emby: Continue watching, Next up and the latest of each library, from the user's own server. The
 * password is exchanged for a token once and then forgotten; only the token stays (as "userId:token"). Items open the
 * matching app. SPEC: PEX-04, PEX-11, PEX-12, PEX-13
 */
class JellyfinProvider(
    private val credentials: ProviderCredentials,
    private val server: ServerHttp,
    private val appPackage: () -> String,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ID, say, clock) {
    @Volatile private var libraries: List<JellyfinLibrary> = emptyList()

    override fun rowSources(): List<RowSource> = listOf(
        RowSource("resume", R.string.pex_row_continue),
        RowSource("nextup", R.string.pex_row_next_up),
    ) + libraries.take(MAX_LIBRARIES).mapIndexed { i, l ->
        RowSource("lib${l.id}", titleText = say(R.string.pex_row_latest_in).format(l.name), defaultOn = i < 2)
    }

    private class Session(val base: HttpUrl, val userId: String, val token: String) {
        val headers get() = mapOf("Accept" to "application/json", "X-Emby-Token" to token)
    }

    private suspend fun base() = ServerUrl.parse(credentials.get("url").orEmpty(), HttpPolicy.PRIVATE_ONLY)

    /** The stored token, signing in with the typed password first when there is no token yet. */
    private suspend fun session(): Session? {
        val base = base() ?: return null
        val saved = credentials.get("token")
        if (saved == null) {
            val user = credentials.get("username") ?: return null
            val password = credentials.get("password") ?: return null
            val (id, token) = signIn(base, user, password) ?: return null
            credentials.set("token", "$id:$token")
            credentials.set("password", null)      // never kept (PEX-11)
            return Session(base, id, token)
        }
        if (':' in saved) return Session(base, saved.substringBefore(':'), saved.substringAfter(':'))
        val me = server.json(base.child("Users/Me"), mapOf("Accept" to "application/json", "X-Emby-Token" to saved))
        val id = me.jStr("Id") ?: return null
        return Session(base, id, saved)
    }

    private suspend fun signIn(base: HttpUrl, user: String, password: String): Pair<String, String>? {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("Username", kotlinx.serialization.json.JsonPrimitive(user))
            put("Pw", kotlinx.serialization.json.JsonPrimitive(password))
        }.toString()
        val answer = server.json(
            base.child("Users/AuthenticateByName"),
            mapOf("Accept" to "application/json", "X-Emby-Authorization" to AUTH_HEADER), body = body,
        )
        return JellyfinJson.login(answer)
    }

    private suspend fun load(s: Session, path: String, query: Map<String, String>) =
        JellyfinJson.items(server.json(s.base.child(path, query + COMMON), s.headers), s.base, ID, appPackage())

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val s = session() ?: return emptyList()
        runCatching { libraries = JellyfinJson.libraries(server.json(s.base.child("Users/${s.userId}/Views"), s.headers)) }
        val extra = RowSources.capped(rowSources()).filter { it.id.startsWith("lib") && it !in wanted && RowSources.isOn(profile, ID, it) }
        return (wanted + extra).mapNotNull { src ->
            source(src, if (src.id.startsWith("lib")) ShelfStyle.POSTER else ShelfStyle.WIDE) {
                when {
                    src.id == "resume" -> load(s, "Users/${s.userId}/Items/Resume", mapOf("MediaTypes" to "Video"))
                    src.id == "nextup" -> load(s, "Shows/NextUp", mapOf("UserId" to s.userId))
                    else -> load(s, "Users/${s.userId}/Items/Latest", mapOf("ParentId" to src.id.removePrefix("lib")))
                }
            }
        }
    }

    override suspend fun test(): TestResult {
        if (base() == null) return failed(R.string.pex_test_bad_address)
        return try {
            val s = session() ?: return failed(R.string.pex_test_sign_in)
            libraries = JellyfinJson.libraries(server.json(s.base.child("Users/${s.userId}/Views"), s.headers))
            TestResult(true, say(R.string.pex_test_ok_jellyfin))
        } catch (e: Exception) {
            failed(R.string.pex_test_failed)
        }
    }

    companion object {
        const val ID = "jellyfin"
        private const val MAX_LIBRARIES = 6
        private val COMMON = mapOf("Limit" to "20", "Fields" to "Overview,OfficialRating", "ImageTypeLimit" to "1")
        private const val AUTH_HEADER = "MediaBrowser Client=\"Shamash\", Device=\"Android TV\", DeviceId=\"shamash-tv\", Version=\"1\""
        private const val JELLYFIN_APP = "org.jellyfin.androidtv"
        private const val EMBY_APP = "tv.emby.embyatv"

        val fields = listOf(
            CredentialField("url", R.string.pex_jf_url, CredentialKind.URL, hintRes = R.string.pex_jf_url_hint),
            CredentialField("username", R.string.pex_jf_user, CredentialKind.TEXT, required = false),
            CredentialField("password", R.string.pex_jf_password, CredentialKind.SECRET, required = false, hintRes = R.string.pex_jf_password_hint),
            CredentialField("token", R.string.pex_jf_token, CredentialKind.TOKEN, required = false),
        )

        /** The registration (PEX-01). */
        val addOn = AddOn(ID, R.string.pex_jf_name, R.string.pex_jf_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            val app = { if (!deps.isInstalled(JELLYFIN_APP) && deps.isInstalled(EMBY_APP)) EMBY_APP else JELLYFIN_APP }
            JellyfinProvider(deps.credentials, ServerHttp(http), app, say = { id -> context?.getString(id).orEmpty() })
        }
    }
}
