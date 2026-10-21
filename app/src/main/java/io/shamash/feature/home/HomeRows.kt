package io.shamash.feature.home

import io.shamash.domain.Carousel
import io.shamash.domain.ContentGate
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.Match
import io.shamash.domain.MediaItem
import io.shamash.domain.Moment
import io.shamash.domain.Moments
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TvApp
import io.shamash.domain.Weather
import io.shamash.ui.Featured
import io.shamash.ui.ShelfKeys
import io.shamash.ui.WeatherNow
import java.util.Locale
import kotlin.math.roundToInt

/** Turns what was loaded into the screen's rows, billboard and apps (pure; runs off the main thread). */
object HomeRows {
    const val YOUTUBE = "com.google.android.youtube.tv"
    private const val APPS_ROW_MAX = 12
    private const val RECENT_MAX = 12
    private const val CONTINUE_MAX = 20

    /** Brand names for apps that may not be installed (the billboard still says where a title opens). */
    private val BRANDS = mapOf(Carousel.MATCH_APP to "TOD", YOUTUBE to "YouTube")

    fun build(input: HomeInput): HomeUi {
        val p = input.profile
        val loaded = gated(input.loaded, p)
        val history = ContentGate.filter(input.history, p)
        val shelves = providerShelves(loaded)
        val matches = Carousel.markMine(loaded.matches, p)
        val moment = Moment.at(input.hour)
        val names = loaded.installed.associate { it.packageName to it.label }
        return HomeUi(
            ready = loaded.started && loaded.pending.isEmpty(),
            greeting = Moments.greeting(moment, p.name),
            moment = moment,
            featured = featured(loaded, matches, input.now, names),
            home = homeRows(p, shelves, matches, history, moment, input.now),
            discover = discoverRows(shelves),
            appsRow = appsRow(p, loaded.installed, loaded.recent),
            recentApps = loaded.recent.take(RECENT_MAX),
            allApps = loaded.installed,
            weather = loaded.weather?.let { weatherNow(it, p) },
            city = p.city,
        )
    }

    /** What the profile's content level lets through: one gate for shelves, billboard and matches. SPEC: PRF-12 */
    fun gated(loaded: Loaded, p: Profile): Loaded {
        val level = p.contentLevel
        if (level == ContentLevel.ALL) return loaded
        return loaded.copy(
            providers = loaded.providers.mapValues { (_, c) ->
                ProviderContent(c.shelves.map { ContentGate.filter(it, level) }, ContentGate.filter(c.featured, level))
            },
            matches = loaded.matches.filter { ContentGate.allows(Carousel.matchItem(it), level) },
        )
    }

    /** Every provider's shelves in the registry's order, each with its items once. */
    fun providerShelves(loaded: Loaded): List<Shelf> {
        val ids = loaded.order + (loaded.providers.keys - loaded.order.toSet())
        return ids.flatMap { loaded.providers[it]?.shelves.orEmpty() }.map { s -> s.copy(items = s.items.distinctBy { it.id }) }
    }

    /** The billboard: the user's match day first, then the providers in turns. SPEC: HOME-10, MC-03 */
    fun featured(loaded: Loaded, matches: List<Match>, now: Long, names: Map<String, String>): List<Featured> {
        val lead = Carousel.matchDay(matches, now)?.let { Carousel.matchItem(it) }
        val byProvider = loaded.providers.mapValues { it.value.featured }
        return Carousel.mix(lead, byProvider).map { Featured(it, appName(it, names)) }
    }

    /**
     * Home rows in order, empty ones left out: Matches, the moment's row, Continue watching, Continue on YouTube,
     * YouTube recommendations, TOD, Cinemana, "Popular on" the user's services, then anything else. SPEC: HOME-04
     */
    fun homeRows(
        p: Profile, shelves: List<Shelf>, matches: List<Match>, history: List<MediaItem>, moment: Moment, now: Long,
    ): List<Shelf> {
        val (videos, titles) = history.partition(::isVideo)
        val ownProgress = shelves.filter { it.titleKey == ShelfKeys.CONTINUE }.flatMap { it.items }
        val watching = (ownProgress + titles).distinctBy { it.title.lowercase() }.take(CONTINUE_MAX)
        val youtube = shelves.filter { it.titleKey == ShelfKeys.YOUTUBE_RECOMMENDED }
        val tod = shelves.filter { it.titleKey.startsWith(ShelfKeys.TOD_PREFIX) }
        val cinemana = shelves.filter { it.titleKey.startsWith(ShelfKeys.CINEMANA_PREFIX) }
        val popular = shelves.filter { it.titleKey == ShelfKeys.POPULAR_ON && subscribed(it, p) }
        val placed = (youtube + tod + cinemana).toSet()
        val others = shelves.filter { it !in placed && it.titleKey !in DISCOVER_ONLY && it.titleKey != ShelfKeys.CONTINUE }
        val momentItems = Moments.row(
            moment,
            matches = Moments.mineSoon(matches, now).map { Carousel.matchItem(it) },
            unfinished = Moments.firstUnfinished(watching),
            personal = personalPick(cinemana, popular, shelves),
            youtube = youtube.firstOrNull()?.items?.firstOrNull(),
        )
        val own = listOf(
            Shelf("matches", ShelfKeys.MATCHES, ShelfStyle.MATCH, Carousel.matchRow(matches).map { Carousel.matchItem(it) }),
            Shelf("moment", ShelfKeys.MOMENT_PREFIX + moment.name.lowercase(Locale.ROOT), ShelfStyle.WIDE, momentItems),
            Shelf("continue", ShelfKeys.CONTINUE, ShelfStyle.WIDE, watching),
            Shelf("continue:youtube", ShelfKeys.CONTINUE_YOUTUBE, ShelfStyle.VIDEO, videos.distinctBy { it.id }),
        )
        return (own + youtube + tod + cinemana + popular + others)
            .map { s -> s.copy(items = s.items.distinctBy { it.id }) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.id }
    }

    /** Discover: Top 10, then "Popular on" every service the TV has. SPEC: HOME-05 */
    fun discoverRows(shelves: List<Shelf>): List<Shelf> =
        (shelves.filter { it.titleKey == ShelfKeys.TOP10 } + shelves.filter { it.titleKey == ShelfKeys.POPULAR_ON })
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.id }

    /** The apps row: subscribed apps first, then the other streaming apps, then recently used. SPEC: HOME-03 */
    fun appsRow(p: Profile, installed: List<TvApp>, recent: List<TvApp>): List<TvApp> {
        val subscribed = installed.filter { it.packageName in p.subscriptions }
        val streaming = installed.filter { it.streaming }
        return (subscribed + streaming + recent).distinctBy { it.packageName }.take(APPS_ROW_MAX)
    }

    fun weatherNow(w: Weather, p: Profile): WeatherNow {
        val degrees = if (p.region.equals("US", ignoreCase = true)) w.temperatureC * 9 / 5 + 32 else w.temperatureC
        return WeatherNow(degrees.roundToInt(), w.code, w.isDay)
    }

    /** The app an item opens in. */
    fun packageOf(item: MediaItem): String? = item.appPackage ?: when (val t = item.open) {
        is OpenTarget.AppPage -> t.appPackage
        is OpenTarget.Lookup -> t.appPackage
        is OpenTarget.App -> t.appPackage
        is OpenTarget.AppScreen -> t.appPackage
        is OpenTarget.Video -> YOUTUBE
        is OpenTarget.Internal -> null
    }

    fun isVideo(item: MediaItem) = item.kind == Kind.VIDEO || item.open is OpenTarget.Video

    private fun appName(item: MediaItem, names: Map<String, String>): String {
        val pkg = packageOf(item) ?: return item.providerId.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return names[pkg] ?: BRANDS[pkg] ?: item.providerId.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun subscribed(shelf: Shelf, p: Profile) = shelf.items.any { packageOf(it) in p.subscriptions }

    /** One pick for the user: Cinemana's, else the first of their services', else the Top 10's. */
    private fun personalPick(cinemana: List<Shelf>, popular: List<Shelf>, all: List<Shelf>): MediaItem? {
        val top = all.filter { it.titleKey == ShelfKeys.TOP10 }
        return (cinemana + popular + top).firstNotNullOfOrNull { it.items.firstOrNull() }
    }

    private val DISCOVER_ONLY = setOf(ShelfKeys.TOP10, ShelfKeys.POPULAR_ON)
}
