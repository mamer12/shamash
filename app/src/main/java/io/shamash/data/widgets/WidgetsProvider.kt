package io.shamash.data.widgets

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.Preferences
import io.shamash.data.ProfileSlot
import io.shamash.data.Reach
import io.shamash.data.Refresh
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.data.asSecretStore
import io.shamash.data.interleave
import io.shamash.data.softly
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The tile types of the "Today" row; each can be taken out. */
enum class TileType(val key: String) { CRYPTO("crypto"), FX("fx"), NEWS("news") }

/** What a profile's "Today" row shows: price symbols, news feed addresses, and the tile types taken out. SPEC: PEX-27 */
@Serializable
data class WidgetConfig(
    val symbols: List<String> = listOf("BTC", "ETH", "USD/EUR"),
    val feeds: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun shows(type: TileType) = type.key !in hidden

    override fun toString() = "WidgetConfig(symbols=$symbols, feeds=${feeds.size}, hidden=$hidden)"

    companion object {
        const val MAX_FEEDS = 3
    }
}

class WidgetStore(store: SecretStore, profileId: suspend () -> String) {
    private val slot = ProfileSlot(store, "widgets_config", WidgetConfig.serializer(), WidgetConfig(), profileId)

    suspend fun get(): WidgetConfig = slot.get()

    suspend fun save(c: WidgetConfig): WidgetConfig =
        c.copy(symbols = c.symbols.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(TILES_MAX), feeds = c.feeds.distinct().take(WidgetConfig.MAX_FEEDS))
            .also { slot.set(it) }

    suspend fun clear() = slot.clear()
}

/** How old a value may be: normal for 2 h, then shown as stale, gone after a day. SPEC: PEX-27 */
object Freshness {
    const val FRESH_MS = 2 * Refresh.HOUR
    const val KEEP_MS = 24 * Refresh.HOUR

    fun isStale(at: Long, now: Long) = now - at > FRESH_MS

    fun isGone(at: Long, now: Long) = now - at > KEEP_MS
}

/** The last good answer per key, so a failed refresh keeps showing it (stale after 2 h, dropped after a day). */
class LastGood<T> {
    private val values = ConcurrentHashMap<String, Pair<T, Long>>()

    fun put(key: String, value: T, at: Long) { values[key] = value to at }

    fun get(key: String, now: Long): Pair<T, Long>? = values[key]?.takeUnless { Freshness.isGone(it.second, now) }
}

const val TILES_MAX = 6

/**
 * The "Today" row: up to 6 tiles of prices (coins from CoinGecko, currency rates from a public endpoint) and news
 * headlines, refreshed every 15 minutes. A failed refresh keeps the last good value; after 2 h it says how old it is.
 * No sign-in, no key. Price tiles carry a G rating (numbers suit everyone); headlines carry none, so FAMILY and KIDS
 * profiles see prices only. SPEC: PEX-27
 */
class WidgetsProvider(
    private val store: WidgetStore,
    private val prices: PriceApi,
    private val server: ServerHttp,
    say: Say,
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: HttpPolicy = HttpPolicy.NONE,   // https news feeds only (PEX-22); tests use a local http server
) : ServerProvider(ID, say, clock, ttl = 15 * Refresh.MINUTE), ProfileListsOwner {
    /** Remove: the active profile's own list goes too. SPEC: PEX-05 */
    override suspend fun clearProfileLists() = store.clear()

    private val quotes = LastGood<Quote>()
    private val news = LastGood<List<Headline>>()
    @Volatile private var configSalt = ""

    override fun rowSources() = listOf(RowSource("today", R.string.more_w_row_today))

    override val salt: String get() = configSalt

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) configSalt = store.get().hashCode().toString()
        return super.shelves(profile)
    }

    suspend fun config(): WidgetConfig = store.get()

    suspend fun save(c: WidgetConfig): WidgetConfig = store.save(c).also { configSalt = it.hashCode().toString() }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> =
        wanted.mapNotNull { s -> source(s, ShelfStyle.WIDE) { tiles(store.get()) } }

    /** Prices and headlines in turn, at most 6. */
    suspend fun tiles(c: WidgetConfig): List<MediaItem> {
        val now = clock()
        val symbols = Symbol.list(c.symbols.joinToString(","), TILES_MAX)
        val coins = if (c.shows(TileType.CRYPTO)) symbols.filterIsInstance<Symbol.Coin>() else emptyList()
        val pairs = if (c.shows(TileType.FX)) symbols.filterIsInstance<Symbol.Fx>() else emptyList()
        softly("widget coins", emptyList()) { prices.coins(coins) }.forEach { quotes.put(it.symbol.key, it, it.at) }
        softly("widget rates", emptyList()) { prices.pairs(pairs) }.forEach { quotes.put(it.symbol.key, it, it.at) }
        val priceTiles = (coins + pairs).mapNotNull { quotes.get(it.key, now)?.let { (q, at) -> priceTile(q, at, now) } }
        val newsTiles = if (!c.shows(TileType.NEWS)) emptyList() else c.feeds.take(WidgetConfig.MAX_FEEDS).map { url ->
            headlines(url, now).map { (h, at) -> newsTile(h, at, now) }
        }
        return interleave(listOf(priceTiles) + newsTiles, TILES_MAX) { it.id }
    }

    private suspend fun headlines(url: String, now: Long): List<Pair<Headline, Long>> {
        val fresh = softly("widget news", null as List<Headline>?) {
            val address = ServerUrl.parse(url, policy) ?: return@softly null
            server.read(address, timeoutMs = 6_000, maxBytes = 1024L * 1024, reach = Reach.PUBLIC) { Headlines.parse(it, 2) }
        }
        if (!fresh.isNullOrEmpty()) news.put(url, fresh, now)
        val (list, at) = news.get(url, now) ?: return emptyList()
        return list.map { it to at }
    }

    private fun subtitle(lead: String, at: Long, now: Long): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
        return if (Freshness.isStale(at, now)) say(R.string.more_w_stale).format(time)
        else listOf(lead, time).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    private fun priceTile(q: Quote, at: Long, now: Long): MediaItem {
        val (title, link) = when (val s = q.symbol) {
            is Symbol.Coin -> "${s.label}  $" + format(q.value) to "https://www.coingecko.com/en/coins/${s.id}"
            is Symbol.Fx -> "${s.base}/${s.quote}  " + format(q.value) to "https://www.xe.com/currencyconverter/convert/?Amount=1&From=${s.base}&To=${s.quote}"
        }
        return MediaItem(
            id = "$ID:${q.symbol.key}", providerId = ID, title = title, kind = Kind.VIDEO, open = OpenTarget.AppPage(link, ""),
            subtitle = subtitle(change(q.change), at, now), rating = "G",
        )
    }

    private fun newsTile(h: Headline, at: Long, now: Long) = MediaItem(
        id = "$ID:news:${h.link.hashCode().toUInt().toString(16)}", providerId = ID, title = h.title, kind = Kind.VIDEO,
        open = OpenTarget.AppPage(h.link, ""), subtitle = subtitle(h.source, at, now),
    )

    override suspend fun test(): TestResult {
        val c = store.get()
        val shown = tiles(c)
        return if (shown.isEmpty()) failed(R.string.more_w_test_none) else TestResult(true, say(R.string.more_w_test_ok).format(shown.size))
    }

    companion object {
        const val ID = "widgets"

        fun format(value: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = when {
                value >= 1000 -> 0
                value >= 1 -> 2
                else -> 4
            }
            minimumFractionDigits = if (value >= 1000) 0 else 2
        }.format(value)

        fun change(pct: Double?): String = pct?.let { (if (it >= 0) "+" else "") + String.format(Locale.US, "%.1f%%", it) }.orEmpty()

        /** The registration (PEX-01): nothing to sign in to. */
        val addOn = AddOn(ID, R.string.more_w_name, R.string.more_w_desc) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            val server = ServerHttp(http)
            WidgetsProvider(
                WidgetStore(prefs.asSecretStore()) { prefs.current().id }, PriceApi(server), server,
                say = { id -> context?.getString(id).orEmpty() },
            )
        }
    }
}
