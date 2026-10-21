package io.shamash.data.iptv

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Preferences
import io.shamash.data.Refresh
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.data.TimedCache
import io.shamash.data.softly
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.RowSources
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * IPTV from the user's own M3U playlist (and optional XMLTV guide): Favorites and a row per chosen group. A channel
 * opens in the user's IPTV player app; Shamash has no player for it. The playlist address is a secret: asked with
 * the private flag, never cached, logged or shown again. SPEC: PEX-14, PEX-15, PEX-16
 */
class IptvProvider(
    private val credentials: ProviderCredentials,
    private val server: ServerHttp,
    private val choices: IptvChoices,
    say: Say,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ServerProvider(ID, say, clock, ttl = 5 * Refresh.MINUTE), ProfileListsOwner {
    /** Remove: the active profile's own list goes too. SPEC: PEX-05 */
    override suspend fun clearProfileLists() = choices.clear()

    private val playlists = TimedCache<String, Playlist?>(clock) { if (it == null || it.channels.isEmpty()) Refresh.RETRY_EMPTY else 2 * Refresh.HOUR }
    private var guide: Deferred<Map<String, NowNext>>? = null
    private var guideAt = 0L
    private var guideFor = ""
    @Volatile private var groups: List<String> = emptyList()

    override fun rowSources(): List<RowSource> = listOf(RowSource("fav", R.string.pex_row_favorites)) +
        groups.take(MAX_GROUPS_LISTED).mapIndexed { i, g -> RowSource("g" + Integer.toHexString(g.hashCode()), titleText = g, defaultOn = i < 3) }

    /** The playlist, or null when none is set or it can't be read. */
    suspend fun playlist(): Playlist? {
        val raw = credentials.get("playlist") ?: return null
        val url = ServerUrl.parse(raw, HttpPolicy.ANY) ?: return null
        val pl = playlists.get(raw) {
            softly("iptv playlist", null as Playlist?) {
                server.read(url, timeoutMs = PLAYLIST_MS, maxBytes = MAX_BYTES) { M3u.parse(it) }
            }
        }
        if (pl != null) groups = pl.groups
        return pl
    }

    suspend fun favorites(): List<String> = choices.favorites()

    suspend fun toggleFavorite(channelId: String) = choices.toggle(channelId)

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val pl = playlist() ?: return emptyList()
        val player = credentials.get("player").orEmpty()
        val favorites = choices.favorites().toSet()
        val epg = epgFor(pl.channels.filter { it.id in favorites })
        fun items(list: List<Channel>) = list.take(ROW_MAX).map { card(it, player, epg[it.id]) }
        // The groups are known only now, so the rows the profile has on are worked out again.
        val named = RowSources.capped(rowSources()).filter { RowSources.isOn(profile, ID, it) }
        return named.take(MAX_GROUPS_SHOWN + 1).mapNotNull { s ->
            source(s, ShelfStyle.WIDE) {
                if (s.id == "fav") items(pl.channels.filter { it.id in favorites })
                else items(pl.channels.filter { it.group == s.titleText })
            }
        }
    }

    private fun card(c: Channel, player: String, nn: NowNext?) = MediaItem(
        id = "$ID:${c.id}", providerId = ID, title = c.name, kind = Kind.LIVE,
        open = OpenTarget.AppPage(c.url, player, "video/*"),
        subtitle = nn?.now?.let { n -> say(R.string.pex_now).format(n.title) + (nn.next?.let { " · " + say(R.string.pex_next).format(it.title) } ?: "") }
            ?: c.group,
        posterUrl = c.logo, appPackage = player.ifEmpty { null },
    )

    /** Now/next for [channels] from the guide, refreshed every 6 h in the background; waits up to 8 s for the first. */
    private suspend fun epgFor(channels: List<Channel>): Map<String, NowNext> {
        val raw = credentials.get("epg") ?: return emptyMap()
        val url = ServerUrl.parse(raw, HttpPolicy.ANY) ?: return emptyMap()
        if (channels.isEmpty()) return emptyMap()
        val now = clock()
        val signature = raw + channels.joinToString(",") { it.id }
        val running = guide
        if (running == null || guideFor != signature || now - guideAt > EPG_REFRESH_MS) {
            val wanted = channels.flatMap { c -> listOfNotNull(c.tvgId?.lowercase()?.let { it to c.id }, c.name.lowercase() to c.id) }.toMap()
            guide = scope.async {
                softly("iptv guide", emptyMap()) { server.read(url, timeoutMs = EPG_MS, maxBytes = MAX_BYTES) { Xmltv.nowNext(it, wanted, now) } }
            }
            guideAt = now
            guideFor = signature
        }
        return withTimeoutOrNull(8_000) { guide?.await() } ?: emptyMap()
    }

    override suspend fun featured(profile: Profile): List<MediaItem> = emptyList()   // no wide art (PEX-07)

    override suspend fun test(): TestResult {
        val raw = credentials.get("playlist") ?: return failed(R.string.pex_test_bad_address)
        val url = ServerUrl.parse(raw, HttpPolicy.ANY) ?: return failed(R.string.pex_test_bad_address)
        return try {
            val pl = server.read(url, timeoutMs = PLAYLIST_MS, maxBytes = MAX_BYTES) { M3u.parse(it) }
            groups = pl.groups
            if (pl.channels.isEmpty()) failed(R.string.pex_test_empty)
            else TestResult(true, say(R.string.pex_test_ok_iptv).format(pl.channels.size))
        } catch (e: Exception) {
            failed(R.string.pex_test_failed)
        }
    }

    companion object {
        const val ID = "iptv"
        private const val MAX_GROUPS_LISTED = 11
        private const val MAX_GROUPS_SHOWN = 8
        private const val ROW_MAX = 30
        private const val PLAYLIST_MS = 30_000L
        private const val EPG_MS = 90_000L
        private const val EPG_REFRESH_MS = 6 * Refresh.HOUR
        private const val MAX_BYTES = 200L * 1024 * 1024

        val fields = listOf(
            CredentialField("playlist", R.string.pex_iptv_playlist, CredentialKind.SECRET, hintRes = R.string.pex_iptv_playlist_hint),
            CredentialField("epg", R.string.pex_iptv_epg, CredentialKind.SECRET, required = false, hintRes = R.string.pex_iptv_epg_hint),
            CredentialField("player", R.string.pex_iptv_player, CredentialKind.TEXT, required = false, hintRes = R.string.pex_iptv_player_hint),
        )

        /** The registration (PEX-01). */
        val addOn = AddOn(ID, R.string.pex_iptv_name, R.string.pex_iptv_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            val store = object : SecretStore {
                override suspend fun secret(key: String) = prefs.secret(key)
                override suspend fun setSecret(key: String, value: String?) = prefs.setSecret(key, value)
            }
            IptvProvider(deps.credentials, ServerHttp(http), IptvChoices(store) { prefs.current().id }, say = { id -> context?.getString(id).orEmpty() })
        }
    }
}
