package io.shamash.data.cinemana

import io.shamash.data.Http
import io.shamash.data.TimedCache
import io.shamash.data.softly
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Preview
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

// SPEC: CIN-01..11
data class CinemaBrowseData(val featured: List<MediaItem> = emptyList(), val shelves: List<Shelf> = emptyList())
data class CinemaPlayback(
    val details: CinemanaDetails,
    val files: List<VideoFile>,
    val subtitles: SubtitleFiles,
    val episodes: List<CinemanaTitle>,
)

/** The add-on's boundary: screens ask for catalog operations, never construct API requests. */
class CinemaLibrary(
    private val api: CinemanaApi,
    private val tmdb: CinemanaTmdb,
    val http: Http,
    val store: CinemaStore,
) {
    private val recommendations = CinemaCatalogRecommendations(api, tmdb)
    private data class HomeKey(val profile: Profile, val saved: CinemaSaved)
    private val homeCache = TimedCache<HomeKey, CinemaBrowseData>(System::currentTimeMillis) {
        if (it.shelves.none { row -> row.items.isNotEmpty() }) 15_000L else 300_000L
    }

    suspend fun browse(profile: Profile, tab: String = "home", query: String = ""): CinemaBrowseData =
        withContext(Dispatchers.IO) {
            val saved = store.snapshot()
            when (tab) {
                "search" -> CinemaBrowseData(shelves = listOf(shelf("cinemana_results", search(query, profile))))
                "list", "my_list" -> CinemaBrowseData(shelves = listOf(shelf("cinemana_my_list", saved.myList.filter {
                    CinemaFilter.allows(it, profile.contentLevel)
                }.map { item(it) })))
                "movies", "series" -> category(profile, tab == "series")
                else -> homeCache.get(HomeKey(profile, saved)) { home(profile, saved) }
            }
        }

    private suspend fun home(profile: Profile, saved: CinemaSaved): CinemaBrowseData = coroutineScope {
        val banners = async { safeTitles("Cinemana banner") { api.banner(profile.contentLevel) } }
        val fresh = async { safeTitles("Cinemana new") { api.newly(profile.contentLevel, 24) } }
        val groups = async { softly("Cinemana groups", emptyList()) {
            api.groups(profile.contentLevel, Locale.getDefault().language == "ar")
        } }
        val wide = withWide(banners.await(), profile)
        val newTitles = fresh.await()
        val curated = groups.await()
        val recs = recommendations.rows(profile, saved, newTitles + curated.flatMap { it.titles })
        val rows = listOf(
            Shelf("continue_watching", "continue_watching", ShelfStyle.POSTER,
                CinemaProgressRules.continuing(saved.history).filter { CinemaFilter.allows(it.title, profile.contentLevel) }
                    .map { item(it.title, it.fraction, play = true) }),
            shelf("cinemana_my_list", saved.myList.filter { CinemaFilter.allows(it, profile.contentLevel) }.map { item(it) }),
            shelf("cinemana_new", visible(newTitles, profile).map { item(it) }),
        ) + recs.map { row -> Shelf(row.id, row.titleKey, row.style, row.titles.map { item(it) }, row.argument) } +
            curated.map { group -> Shelf("cinemana_group_${group.id}", "cinemana_group", ShelfStyle.POSTER,
                visible(group.titles, profile).map { item(it) }, group.title) }
        CinemaBrowseData(visible(wide, profile).map { item(it) }, CinemaRecommendations.order(rows))
    }

    private suspend fun category(profile: Profile, series: Boolean): CinemaBrowseData = coroutineScope {
        val recent = async { safeTitles("Cinemana latest") { api.latest(profile.contentLevel, series) } }
        val genres = profile.genres.mapNotNull(CinemanaGenres::idOf).take(4)
        val rows = genres.map { id -> async {
            val titles = safeTitles("Cinemana genre") { api.genre(profile.contentLevel, id, series) }
            val name = CinemanaGenres.IDS.entries.find { it.value == id }?.key.orEmpty()
            Shelf("cinemana_genre_$id", "cinema_genre_${name.replace('-', '_')}", ShelfStyle.POSTER, visible(titles, profile).map { item(it) })
        } }.awaitAll()
        val titles = withWide(visible(recent.await(), profile), profile)
        CinemaBrowseData(titles.take(8).map { item(it) }, listOf(shelf("cinemana_new", titles.map { item(it) })) + rows)
    }

    suspend fun search(query: String, profile: Profile): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        coroutineScope {
            listOf(false, true).map { series -> async {
                safeTitles("Cinemana search") { api.search(profile.contentLevel, query.take(200), series) }
            } }.awaitAll().flatten().let { visible(it, profile) }.distinctBy { it.pageId }.map { item(it) }
        }
    }

    /** Explicit IDs pass the same filter, including restored progress and incoming links. SPEC: CIN-08 */
    suspend fun detail(id: String, profile: Profile): CinemanaDetails? = withContext(Dispatchers.IO) {
        softly("Cinemana details", null) {
            api.details(id, profile.contentLevel)?.takeIf { CinemaFilter.allows(it.title, profile.contentLevel) }
                ?.let { it.copy(title = withWide(listOf(it.title), profile).first()) }
        }
    }

    suspend fun episodes(id: String, profile: Profile): List<CinemanaTitle> = withContext(Dispatchers.IO) {
        visible(safeTitles("Cinemana episodes") { api.episodes(id, profile.contentLevel) }, profile)
    }

    suspend fun similar(title: CinemanaTitle, profile: Profile): List<MediaItem> = withContext(Dispatchers.IO) {
        recommendations.similar(title, profile, store.snapshot()).map { item(it) }
    }

    suspend fun playback(id: String, profile: Profile): CinemaPlayback? = withContext(Dispatchers.IO) {
        val details = detail(id, profile) ?: return@withContext null
        val allEpisodes = if (details.title.isSeries) episodes(details.title.pageId, profile) else emptyList()
        val selected = if (details.title.isSeries && details.title.rootId.isEmpty() && details.title.episode == 0) {
            CinemaProgressRules.episode(allEpisodes, store.snapshot().history)?.let { detail(it.id, profile) }
                ?: return@withContext null
        } else details
        coroutineScope {
            val files = async { softly("Cinemana files", emptyList()) { api.files(selected.title.id, profile.contentLevel) } }
            val subs = async { softly("Cinemana subtitles", SubtitleFiles()) { api.subtitles(selected.title.id, profile.contentLevel) } }
            CinemaPlayback(selected, files.await(), subs.await(), allEpisodes)
        }
    }

    suspend fun preview(item: MediaItem, profile: Profile): Preview? = withContext(Dispatchers.IO) {
        if (!profile.cinemanaAddon) return@withContext null
        val id = item.id.substringAfter(':')
        val title = detail(id, profile)?.title ?: return@withContext null
        val playable = if (title.isSeries && title.rootId.isEmpty()) episodes(title.id, profile).firstOrNull() ?: title else title
        softly("Cinemana preview", null) {
            val files = api.files(playable.id, profile.contentLevel)
            (files.lastOrNull { it.height <= 720 } ?: files.firstOrNull())?.let { Preview(it.url) }
        }
    }

    fun item(title: CinemanaTitle, progress: Float? = null, play: Boolean = false): MediaItem = MediaItem(
        id = "cinemana:${if (play) title.id else title.pageId}", providerId = "cinemana", title = title.title,
        kind = title.kind, open = OpenTarget.AppPage(
            "shamash://cinemana/${if (play) "play/${title.id}" else "title/${title.pageId}"}", "io.shamash"),
        subtitle = listOf(title.year, title.rating, title.stars).filter { it.isNotBlank() }.joinToString(" · "),
        overview = title.overview, posterUrl = title.poster, backdropUrl = title.wide, appPackage = "io.shamash", progress = progress,
        rating = title.rating.ifBlank { null }, genres = title.genres,
    )

    private suspend fun withWide(titles: List<CinemanaTitle>, profile: Profile): List<CinemanaTitle> = coroutineScope {
        titles.take(24).mapIndexed { index, title -> async {
            if (title.wide != null || !profile.hasTmdb || title.imdbId == null || index >= 8) title
            else title.copy(backdrop = softly("Cinemana art", null) { tmdb.backdrop(profile.tmdbKey, title.imdbId) })
        } }.awaitAll()
    }

    private fun visible(titles: List<CinemanaTitle>, profile: Profile) =
        titles.filter { CinemaFilter.allows(it, profile.contentLevel) }.distinctBy { it.id }

    private fun shelf(key: String, items: List<MediaItem>) = Shelf(key, key, ShelfStyle.POSTER, items)
    private suspend fun safeTitles(label: String, load: suspend () -> List<CinemanaTitle>) = softly(label, emptyList(), load)
}
