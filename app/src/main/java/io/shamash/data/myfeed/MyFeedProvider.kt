package io.shamash.data.myfeed

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Preferences
import io.shamash.data.Reach
import io.shamash.data.Say
import io.shamash.data.security.PublicArt
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.ContentLevel
import io.shamash.domain.FeedEditor
import io.shamash.domain.FeedEntry
import io.shamash.domain.FeedKind
import io.shamash.domain.FeedStyle
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import io.shamash.domain.RowSources

/**
 * "My feed": RSS, Atom or JSON feeds the user adds by address, one Home row each (at most 10). Cards open in whatever
 * app takes the link (YouTube links in YouTube). https only unless a feed has its own http switch; 1 MB and 6 s per
 * feed; XML read without DTDs. SPEC: PEX-20, PEX-21, PEX-22
 */
class MyFeedProvider(
    private val store: FeedStore,
    private val server: ServerHttp,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ID, say, clock), FeedEditor, ProfileListsOwner {
    @Volatile private var known: List<UserFeed> = emptyList()

    override fun rowSources(): List<RowSource> = known.map { RowSource(it.id, titleText = it.title) }

    override val salt: String get() = known.joinToString("|") { it.hashCode().toString() }

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) feeds()      // the rows on offer are the feeds the profile has now
        return super.shelves(profile)
    }

    suspend fun feeds(): List<UserFeed> = store.list().also { known = it }

    suspend fun save(feeds: List<UserFeed>): List<UserFeed> = store.save(feeds).also { known = it }

    /** Remove: the active profile's feeds go too. SPEC: PEX-05 */
    override suspend fun clearProfileLists() {
        store.clear()
        known = emptyList()
    }

    override val maxFeeds: Int get() = FeedStore.MAX

    override suspend fun feedEntries(): List<FeedEntry> = feeds().map { it.entry() }

    override suspend fun saveEntries(entries: List<FeedEntry>) {
        val old = feeds().associateBy { it.id }
        save(entries.map { e -> old[e.id]?.merged(e) ?: UserFeed.of(e) })
    }

    override suspend fun tryEntry(entry: FeedEntry, limit: Int): List<String> = preview(UserFeed.of(entry), limit).map { it.title }

    /** The first [limit] cards of [feed] (the editor's "Try it"); throws when the feed can't be read. */
    suspend fun preview(feed: UserFeed, limit: Int = 3): List<FeedItem> = fetch(feed).take(limit)

    suspend fun fetch(feed: UserFeed): List<FeedItem> {
        val url = ServerUrl.parse(feed.url, if (feed.allowHttp) HttpPolicy.ANY else HttpPolicy.NONE) ?: error("address")
        val headers = listOfNotNull(feed.header()).toMap()
        return if (feed.kind == FeedKind.JSON) {
            FeedParser.json(server.json(url, headers, TIMEOUT_MS, MAX_BYTES, reach = Reach.PUBLIC), feed.mapping ?: JsonMapping())
        } else {
            server.read(url, headers, TIMEOUT_MS, MAX_BYTES, reach = Reach.PUBLIC) { FeedParser.xml(it) }   // SPEC: CMP-10
        }
    }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val feeds = feeds()
        val byId = feeds.associateBy { it.id }
        return RowSources.capped(rowSources()).filter { RowSources.isOn(profile, ID, it) }.mapNotNull { s ->
            val feed = byId[s.id] ?: return@mapNotNull null
            if (!suits(feed.level, profile.contentLevel)) return@mapNotNull null
            source(s, shelfStyle(feed.style)) { fetch(feed).map { card(feed, it) } }
        }
    }

    override suspend fun test(): TestResult {
        val feeds = feeds()
        if (feeds.isEmpty()) return failed(R.string.pex_test_no_feeds)
        val ok = feeds.count { f -> runCatching { fetch(f).isNotEmpty() }.getOrDefault(false) }
        return TestResult(ok > 0, say(R.string.pex_test_feeds).format(ok, feeds.size))
    }

    companion object {
        const val ID = "myfeed"
        private const val TIMEOUT_MS = 6_000L
        private const val MAX_BYTES = 1024L * 1024

        /** A feed made for [feed] shows to a profile at [profile] level or stricter-than-needed. */
        fun suits(feed: ContentLevel, profile: ContentLevel) = when (profile) {
            ContentLevel.ALL -> true
            ContentLevel.FAMILY -> feed != ContentLevel.ALL
            ContentLevel.KIDS -> feed == ContentLevel.KIDS
        }

        /** The certification a feed's cards carry, so the one ContentGate applies as for every other source. */
        fun rating(level: ContentLevel): String? = when (level) {
            ContentLevel.ALL -> null
            ContentLevel.FAMILY -> "PG"
            ContentLevel.KIDS -> "G"
        }

        fun shelfStyle(style: FeedStyle) = when (style) {
            FeedStyle.WIDE -> ShelfStyle.WIDE
            FeedStyle.VIDEO -> ShelfStyle.VIDEO
            FeedStyle.POSTER -> ShelfStyle.POSTER
        }

        fun card(feed: UserFeed, item: FeedItem): MediaItem {
            val poster = feed.style == FeedStyle.POSTER
            val art = PublicArt.url(item.image)   // SPEC: CMP-10: a feed's art never points into the LAN
            return MediaItem(
                id = "$ID:${feed.id}:${item.link.hashCode().toUInt().toString(16)}", providerId = ID,
                title = item.title, kind = Kind.VIDEO, open = target(item.link), subtitle = item.subtitle,
                posterUrl = if (poster) art else null,
                backdropUrl = if (poster) null else art,
                rating = rating(feed.level),
            )
        }

        /** YouTube links open in YouTube; any other link in whichever app takes it. */
        fun target(link: String): OpenTarget {
            val id = youtubeId(link)
            return if (id != null) OpenTarget.Video(id) else OpenTarget.AppPage(link, "")
        }

        private val YT = Regex("""^https?://(?:www\.|m\.)?(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})""")

        fun youtubeId(link: String): String? = YT.find(link)?.groupValues?.get(1)

        /** The registration (PEX-01). No credential fields: the feeds have their own editor. */
        val addOn = AddOn(ID, R.string.pex_feed_name, R.string.pex_feed_desc) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            val secrets = object : SecretStore {
                override suspend fun secret(key: String) = prefs.secret(key)
                override suspend fun setSecret(key: String, value: String?) = prefs.setSecret(key, value)
            }
            MyFeedProvider(FeedStore(secrets) { prefs.current().id }, ServerHttp(http), say = { id -> context?.getString(id).orEmpty() })
        }
    }
}
