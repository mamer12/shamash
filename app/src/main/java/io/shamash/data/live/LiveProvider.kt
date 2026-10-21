package io.shamash.data.live

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.ProfileSlot
import io.shamash.data.Refresh
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.asSecretStore
import io.shamash.data.softly
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A streamer the profile follows on [service] ("twitch" or "kick"). */
@Serializable
data class LiveFollow(val service: String, val login: String) {
    override fun toString() = "$service:$login"

    companion object {
        private val TWITCH = Regex("[a-z0-9_]{3,25}")
        private val KICK = Regex("[a-z0-9_-]{2,40}")
        private val URL = Regex("""^(?:https?://)?(?:www\.|m\.)?(twitch\.tv|kick\.com)/([A-Za-z0-9_-]+)/?$""")

        /** "twitch:name", "kick:name", or a channel address; null when it isn't one. */
        fun parse(raw: String): LiveFollow? {
            val text = raw.trim()
            URL.find(text)?.let { m ->
                return of(if (m.groupValues[1].startsWith("twitch")) "twitch" else "kick", m.groupValues[2])
            }
            val service = text.substringBefore(':', "").trim().lowercase()
            return of(service, text.substringAfter(':').trim())
        }

        fun of(service: String, login: String): LiveFollow? {
            val l = login.lowercase()
            return when (service) {
                "twitch" -> LiveFollow("twitch", l).takeIf { TWITCH.matches(l) }
                "kick" -> LiveFollow("kick", l).takeIf { KICK.matches(l) }
                else -> null
            }
        }
    }
}

/** The streamers a profile follows, at most [MAX]. SPEC: PEX-25 */
class LiveFollows(store: SecretStore, profileId: suspend () -> String) {
    private val slot = ProfileSlot(store, "live_follows", ListSerializer(LiveFollow.serializer()), emptyList(), profileId)

    suspend fun list(): List<LiveFollow> = slot.get()

    suspend fun save(follows: List<LiveFollow>): List<LiveFollow> = follows.distinct().take(MAX).also { slot.set(it) }

    suspend fun clear() = slot.clear()

    companion object {
        const val MAX = 40
    }
}

/**
 * Twitch and Kick "Live now": the profile's follows (by user name, no account sign-in), offline streamers hidden,
 * refreshed every 5 minutes, opening the stream's app page. Twitch needs the user's own Client ID and secret for an app
 * token; without them only Kick is asked. Streams carry no rating, so FAMILY and KIDS profiles see none. SPEC: PEX-25
 */
class LiveProvider(
    private val credentials: ProviderCredentials,
    private val follows: LiveFollows,
    private val twitch: TwitchApi,
    private val kick: KickApi,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ID, say, clock, ttl = 5 * Refresh.MINUTE) {
    @Volatile private var followSalt = ""

    override fun rowSources() = listOf(RowSource("now", R.string.more_live_row_now))

    override val salt: String get() = followSalt

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) followSalt = follows.list().joinToString(",")
        return super.shelves(profile)
    }

    suspend fun follows(): List<LiveFollow> = follows.list()

    suspend fun saveFollows(list: List<LiveFollow>): List<LiveFollow> = follows.save(list).also { followSalt = it.joinToString(",") }

    /** Everyone followed who is live now, most watched first. */
    suspend fun liveNow(): List<LiveStream> = coroutineScope {
        val all = follows.list()
        val twitchLogins = all.filter { it.service == "twitch" }.map { it.login }
        val tw = async { softly("twitch live", emptyList()) { twitch.live(twitchLogins, credentials.get("twitch-client"), credentials.get("twitch-secret")) } }
        val kk = all.filter { it.service == "kick" }.map { f -> async { softly("kick live", null as LiveStream?) { kick.live(f.login) } } }
        (tw.await() + kk.awaitAll().filterNotNull()).sortedByDescending { it.viewers }
    }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> =
        wanted.mapNotNull { s -> source(s, ShelfStyle.WIDE) { liveNow().take(ROW_MAX).map(::card) } }

    override suspend fun featured(profile: Profile): List<MediaItem> = emptyList()

    override suspend fun test(): TestResult {
        val all = follows.list()
        if (all.isEmpty()) return failed(R.string.more_live_test_none)
        if (all.any { it.service == "twitch" } && (credentials.get("twitch-client") == null || credentials.get("twitch-secret") == null)) {
            return failed(R.string.more_live_test_twitch_client)
        }
        return try {
            twitch.forget()
            val tw = all.filter { it.service == "twitch" }.map { it.login }
            val live = twitch.live(tw, credentials.get("twitch-client"), credentials.get("twitch-secret")).size +
                all.filter { it.service == "kick" }.count { runCatching { kick.live(it.login) }.getOrNull() != null }
            TestResult(true, say(R.string.more_live_test_ok).format(live, all.size))
        } catch (e: Exception) {
            failed(R.string.pex_test_failed)
        }
    }

    companion object {
        const val ID = "live"
        private const val ROW_MAX = 20

        fun target(s: LiveStream): OpenTarget = when (s.service) {
            "twitch" -> OpenTarget.AppPage("twitch://stream/${s.login}", "")
            else -> OpenTarget.AppPage("https://kick.com/${s.login}", "")
        }

        fun card(s: LiveStream) = MediaItem(
            id = "$ID:${s.service}:${s.login}", providerId = ID, title = s.name, kind = Kind.LIVE, open = target(s),
            subtitle = listOf(s.category, s.title).filter { it.isNotEmpty() }.joinToString(" · ").take(120),
            backdropUrl = s.thumbnail,
        )

        val fields = listOf(
            CredentialField("twitch-client", R.string.more_live_twitch_client, CredentialKind.TEXT, required = false, hintRes = R.string.more_live_twitch_client_hint),
            CredentialField("twitch-secret", R.string.more_live_twitch_secret, CredentialKind.SECRET, required = false, hintRes = R.string.more_live_twitch_secret_hint),
        )

        /** The registration (PEX-01). */
        val addOn = AddOn(ID, R.string.more_live_name, R.string.more_live_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            val server = ServerHttp(http)
            LiveProvider(
                deps.credentials, LiveFollows(prefs.asSecretStore()) { prefs.current().id }, TwitchApi(http.client, server), KickApi(server),
                say = { id -> context?.getString(id).orEmpty() },
            )
        }
    }
}
