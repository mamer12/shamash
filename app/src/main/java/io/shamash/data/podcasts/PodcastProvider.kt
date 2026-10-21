package io.shamash.data.podcasts

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Preferences
import io.shamash.data.ProfileSlot
import io.shamash.data.Reach
import io.shamash.data.Say
import io.shamash.data.security.PublicArt
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
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
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer

/** The shows a profile follows, at most [MAX]. SPEC: PEX-23 */
class PodcastStore(store: SecretStore, profileId: suspend () -> String) {
    private val slot = ProfileSlot(store, "podcasts_subs", ListSerializer(Subscription.serializer()), emptyList(), profileId)

    suspend fun list(): List<Subscription> = slot.get()

    suspend fun save(subs: List<Subscription>): List<Subscription> =
        subs.distinctBy { it.url }.take(MAX).also { slot.set(it) }

    suspend fun add(sub: Subscription) = save(list().filterNot { it.url == sub.url } + sub)

    suspend fun remove(url: String) = save(list().filterNot { it.url == url })

    suspend fun clear() = slot.clear()

    companion object {
        const val MAX = 30
    }
}

/**
 * Podcasts: the profile's subscriptions (typed or imported from OPML) as two rows, "New episodes" across all shows and
 * "Subscriptions" (each show's newest episode). An episode opens in the user's podcast app with ACTION_VIEW audio;
 * Shamash has no audio player in v1. https feeds only, 2 MB and 6 s each. SPEC: PEX-23, PEX-07, PEX-22
 */
class PodcastProvider(
    private val credentials: ProviderCredentials,
    private val store: PodcastStore,
    private val server: ServerHttp,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
    private val policy: HttpPolicy = HttpPolicy.NONE,   // https only (PEX-22); tests use a local http server
) : ServerProvider(ID, say, clock), ProfileListsOwner {
    /** Remove: the active profile's own list goes too. SPEC: PEX-05 */
    override suspend fun clearProfileLists() {
        store.clear()
        known = emptyList()
    }

    @Volatile private var known: List<Subscription> = emptyList()

    override fun rowSources() = listOf(
        RowSource("new", R.string.more_pod_row_new),
        RowSource("subs", R.string.more_pod_row_subs),
    )

    override val salt: String get() = known.joinToString("|") { it.url.hashCode().toString() }

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) known = store.list()
        return super.shelves(profile)
    }

    suspend fun subscriptions(): List<Subscription> = store.list().also { known = it }

    /** Reads the show at [url] and follows it under its own title; throws when it isn't a readable podcast. */
    suspend fun subscribe(url: String): Subscription {
        val show = fetch(Subscription(url))
        val sub = Subscription(url.trim(), show.title)
        known = store.add(sub)
        return sub
    }

    suspend fun unsubscribe(url: String) { known = store.remove(url) }

    /** Follows every show of an OPML file at [url] (a podcast app's export); gives how many are followed now. */
    suspend fun importOpml(url: String): Int {
        val address = ServerUrl.parse(url, policy) ?: error("address")
        val found = server.read(address, timeoutMs = TIMEOUT_MS, maxBytes = MAX_BYTES, reach = Reach.PUBLIC) { Opml.parse(it) }
        known = store.save(store.list() + found)
        return known.size
    }

    suspend fun fetch(sub: Subscription): Podcast {
        val url = ServerUrl.parse(sub.url, policy) ?: error("address")
        return server.read(url, timeoutMs = TIMEOUT_MS, maxBytes = MAX_BYTES, reach = Reach.PUBLIC) { PodcastFeed.parse(it) }   // SPEC: CMP-10
    }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val subs = subscriptions()
        if (subs.isEmpty()) return emptyList()
        val player = credentials.get("player").orEmpty()
        val shows = coroutineScope {
            subs.map { s -> async { softly("podcast feed", null as Podcast?) { fetch(s) }?.let { s to it } } }.awaitAll().filterNotNull()
        }
        return wanted.mapNotNull { s ->
            when (s.id) {
                "new" -> source(s, ShelfStyle.WIDE) { newest(shows).take(ROW_MAX).map { (show, ep) -> card(show, ep, player) } }
                "subs" -> source(s, ShelfStyle.POSTER) {
                    shows.mapNotNull { (sub, show) -> show.episodes.firstOrNull()?.let { card(show.copy(title = show.title.ifEmpty { sub.title }), it, player, poster = true) } }
                }
                else -> null
            }
        }
    }

    override suspend fun test(): TestResult {
        val subs = subscriptions()
        if (subs.isEmpty()) return failed(R.string.more_pod_test_none)
        val ok = subs.count { s -> runCatching { fetch(s).episodes.isNotEmpty() }.getOrDefault(false) }
        return TestResult(ok > 0, say(R.string.more_pod_test_ok).format(ok, subs.size))
    }

    companion object {
        const val ID = "podcasts"
        private const val TIMEOUT_MS = 6_000L
        private const val MAX_BYTES = 2L * 1024 * 1024
        private const val ROW_MAX = 20

        /** Every show's episodes, newest first (undated ones last, in feed order). */
        fun newest(shows: List<Pair<Subscription, Podcast>>): List<Pair<Podcast, Episode>> =
            shows.flatMap { (_, show) -> show.episodes.map { show to it } }.sortedByDescending { it.second.dateMs ?: Long.MIN_VALUE }

        fun card(show: Podcast, ep: Episode, player: String, poster: Boolean = false): MediaItem {
            val art = PublicArt.url(ep.image ?: show.image)   // SPEC: CMP-10: a feed's art never points into the LAN
            return MediaItem(
                id = "$ID:${ep.audioUrl.hashCode().toUInt().toString(16)}", providerId = ID,
                title = if (poster) show.title.ifEmpty { ep.title } else ep.title, kind = Kind.VIDEO,
                open = OpenTarget.AppPage(ep.audioUrl, player, "audio/*"),
                subtitle = if (poster) ep.title else show.title,
                posterUrl = if (poster) art else null, backdropUrl = if (poster) null else art,
                appPackage = player.ifEmpty { null },
            )
        }

        val fields = listOf(
            CredentialField("player", R.string.more_pod_player, CredentialKind.TEXT, required = false, hintRes = R.string.more_pod_player_hint),
        )

        /** The registration (PEX-01). */
        val addOn = AddOn(ID, R.string.more_pod_name, R.string.more_pod_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            PodcastProvider(
                deps.credentials, PodcastStore(prefs.asSecretStore()) { prefs.current().id }, ServerHttp(http),
                say = { id -> context?.getString(id).orEmpty() },
            )
        }
    }
}
