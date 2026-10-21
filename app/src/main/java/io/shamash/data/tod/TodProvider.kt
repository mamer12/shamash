package io.shamash.data.tod

import io.shamash.data.Http
import io.shamash.data.Refresh
import io.shamash.data.TimedCache
import io.shamash.data.interleave
import io.shamash.data.softly
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Upcoming matches and new titles on TOD, from its website's featured lists (no account). SPEC: PRV-09, PRV-10 */
class TodProvider(
    private val http: Http,
    private val isInstalled: (String) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : Provider {
    override val id = TodCatalog.PROVIDER_ID

    private data class Rows(val matches: List<MediaItem>, val titles: List<MediaItem>) {
        val isEmpty get() = matches.isEmpty() && titles.isEmpty()
    }

    private val cache = TimedCache<Unit, Rows>(clock) { if (it.isEmpty) Refresh.RETRY_EMPTY else Refresh.HOUR }

    override fun isEnabled(profile: Profile) = isInstalled(TodCatalog.APP)

    override suspend fun shelves(profile: Profile): List<Shelf> {
        val rows = rows(profile)
        return listOf(
            Shelf("tod_matches", "tod_upcoming", ShelfStyle.WIDE, rows.matches),
            Shelf("tod_new", "tod_new", ShelfStyle.WIDE, rows.titles),
        ).filter { it.items.isNotEmpty() }
    }

    override suspend fun featured(profile: Profile): List<MediaItem> = rows(profile).matches.take(FEATURED_MAX)

    /** TOD's site has no search Shamash can use; its titles show in the rows. */
    override suspend fun search(query: String, profile: Profile): List<MediaItem> = emptyList()

    private suspend fun rows(profile: Profile): Rows {
        if (!isEnabled(profile)) return Rows(emptyList(), emptyList())
        return cache.get(Unit) { fetchRows() }
    }

    private suspend fun fetchRows(): Rows = coroutineScope {
        val sports = async { page("sports") }
        val movies = async { page("movies") }
        val series = async { page("series") }
        Rows(
            matches = TodCatalog.matches(sports.await(), clock()),
            titles = TodCatalog.titles(movies.await(), series.await()),
        )
    }

    private suspend fun page(name: String): List<TodTile> = softly("TOD $name", emptyList()) {
        TodJson.slider(http.text("$SITE$name", mapOf("RSC" to "1", "User-Agent" to BROWSER)))
    }

    private companion object {
        const val SITE = "https://www.tod.tv/en/"
        const val FEATURED_MAX = 6

        /** TOD's site answers only browsers ("Request Rejected" otherwise). */
        const val BROWSER = "Mozilla/5.0 (Linux; Android 11; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"
    }
}

/** Pure assembly of TOD tiles into cards. */
object TodCatalog {
    const val PROVIDER_ID = "tod"
    const val APP = "com.todtv.tod"
    private const val ROW_MAX = 20
    private const val STALE_AFTER_MS = 2 * Refresh.HOUR

    /** Games that haven't started more than 2 h ago, soonest first; live once kicked off. SPEC: PRV-09 */
    fun matches(tiles: List<TodTile>, now: Long): List<MediaItem> =
        tiles.mapNotNull { tile ->
            tile.startsAt?.takeIf { tile.contentType == "GAME" && it >= now - STALE_AFTER_MS }?.let { tile to it }
        }
            .sortedBy { (_, startsAt) -> startsAt }
            .take(ROW_MAX)
            .map { (tile, startsAt) -> tile.toItem(if (startsAt <= now) Kind.LIVE else Kind.MATCH, tile.competition) }

    /** New movies and series in turns. */
    fun titles(movies: List<TodTile>, series: List<TodTile>): List<MediaItem> =
        interleave(listOf(movies.map { it.toItem(Kind.MOVIE) }, series.map { it.toItem(Kind.SERIES) }), ROW_MAX) { it.id }

    /** TOD's TV app takes no links: its titles open the app. SPEC: PRV-10 */
    private fun TodTile.toItem(kind: Kind, subtitle: String = "") = MediaItem(
        id = "$PROVIDER_ID:$slug",
        providerId = PROVIDER_ID,
        title = title,
        kind = kind,
        open = OpenTarget.App(APP),
        subtitle = subtitle,
        posterUrl = image.url(CARD_W, CARD_H),
        backdropUrl = image.url(WIDE_W, WIDE_H),
        appPackage = APP,
        startsAt = startsAt,
        rating = rating ?: if (kind == Kind.LIVE) SPORT_RATING else null,
    )

    /** Live sport carries no age label: it shows for FAMILY like a match does, not for KIDS. SPEC: PRF-12 */
    private const val SPORT_RATING = "TV-PG"
    private const val CARD_W = 640
    private const val CARD_H = 360
    private const val WIDE_W = 1280
    private const val WIDE_H = 720
}
