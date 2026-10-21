package io.shamash.data.cinemana

import io.shamash.data.Refresh
import io.shamash.data.TimedCache
import io.shamash.data.interleave
import io.shamash.data.softly
import io.shamash.domain.ContentGate
import io.shamash.domain.ContentLevel
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.PreviewSource
import io.shamash.domain.RefreshableProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Cinemana's catalog on Home: new titles, featured titles (with TMDB's wide art when there's a key) and search.
 * Titles open in Watchme+ when it's installed, else in the Cinemana app. SPEC: PRV-13, PRV-14
 */
class CinemanaProvider(
    private val api: CinemanaApi,
    private val tmdb: CinemanaTmdb,
    private val installed: (String) -> Boolean,
    private val library: CinemaLibrary? = null,
    private val profile: suspend () -> Profile = { Profile() },
) : Provider, PreviewSource, RefreshableProvider {
    override val id = ID
    override val changes: Flow<Unit> = library?.store?.state?.map { Unit } ?: emptyFlow()

    override suspend fun preview(item: MediaItem) = library?.preview(item, profile())

    private data class Lists(val fresh: List<MediaItem>, val featured: List<MediaItem>)
    private data class Key(val level: ContentLevel, val tmdbKey: String)

    private val cache = TimedCache<Key, Lists>(System::currentTimeMillis) { if (it.fresh.isEmpty()) Refresh.RETRY_EMPTY else 2 * Refresh.HOUR }

    override fun isEnabled(profile: Profile) = profile.cinemanaAddon || installed(WATCHME)

    override suspend fun shelves(profile: Profile): List<Shelf> =
        rawShelves(profile).map { ContentGate.filter(it, profile.contentLevel) }.filter { it.items.isNotEmpty() }

    override suspend fun featured(profile: Profile): List<MediaItem> =
        ContentGate.filter(rawFeatured(profile), profile)

    override suspend fun search(query: String, profile: Profile): List<MediaItem> =
        ContentGate.filter(rawSearch(query, profile), profile)   // SPEC: CIN-08, PRF-12

    private suspend fun rawShelves(profile: Profile): List<Shelf> {
        if (profile.cinemanaAddon && library != null) return library.browse(profile).shelves
        val lists = lists(profile)
        return listOf(
            Shelf("cinemana_new", "cinemana_new", ShelfStyle.POSTER, lists.fresh),
            Shelf("cinemana_featured", "cinemana_featured", ShelfStyle.POSTER, lists.featured),
        ).filter { it.items.isNotEmpty() }
    }

    private suspend fun rawFeatured(profile: Profile): List<MediaItem> =
        if (profile.cinemanaAddon && library != null) library.browse(profile).featured.take(FEATURED_MAX)
        else lists(profile).featured.filter { it.backdropUrl != null }.take(FEATURED_MAX)

    private suspend fun rawSearch(query: String, profile: Profile): List<MediaItem> {
        if (profile.cinemanaAddon && library != null) return library.search(query, profile)
        return softly("Cinemana search", emptyList()) {
        coroutineScope {
            val movies = async { api.search(profile.contentLevel, query, series = false) }
            val series = async { api.search(profile.contentLevel, query, series = true) }
            interleave(listOf(movies.await(), series.await()), ROW_MAX) { it.id }.map(::item)
        }
    }

    }

    private suspend fun lists(profile: Profile): Lists = softly("Cinemana", Lists(emptyList(), emptyList())) {
        cache.get(Key(profile.contentLevel, profile.tmdbKey)) { load(profile) }
    }

    private suspend fun load(profile: Profile): Lists = coroutineScope {
        val fresh = async { softly("Cinemana new", emptyList()) { api.newly(profile.contentLevel, ROW_MAX) } }
        val banner = async { softly("Cinemana banner", emptyList()) { api.banner(profile.contentLevel) } }
        val featured = withWideArt(banner.await().take(ROW_MAX), profile.tmdbKey)
        Lists(fresh.await().map(::item), featured.map(::item))
    }

    /** Cinemana's own cover, else TMDB's backdrop looked up by IMDb id for the first few. */
    private suspend fun withWideArt(titles: List<CinemanaTitle>, tmdbKey: String): List<CinemanaTitle> = coroutineScope {
        titles.mapIndexed { i, t ->
            async {
                val imdb = t.imdbId
                if (t.wide != null || tmdbKey.isBlank() || imdb == null || i >= LOOKUPS_MAX) t
                else t.copy(backdrop = softly("TMDB art", null) { tmdb.backdrop(tmdbKey, imdb) })
            }
        }.awaitAll()
    }

    private fun item(t: CinemanaTitle) = MediaItem(
        id = "$ID:${t.pageId}",
        providerId = ID,
        title = t.title,
        kind = t.kind,
        open = open(t),
        subtitle = listOf(t.year, t.rating).filter { it.isNotBlank() }.joinToString(" · "),
        overview = t.overview,
        posterUrl = t.poster,
        backdropUrl = t.wide,
        appPackage = if (installed(WATCHME)) WATCHME else CINEMANA_APP,
        rating = t.rating.ifBlank { null },
    )

    /** Watchme+ opens the title's page; the Cinemana app only opens. */
    internal fun open(t: CinemanaTitle): OpenTarget =
        if (installed(WATCHME)) OpenTarget.AppScreen(
            WATCHME, WATCHME_SCREEN, mapOf(WATCHME_ID to t.pageId, WATCHME_QUERY to t.title), resumeExtraKey = WATCHME_PLAY_AT,
        )
        else OpenTarget.App(CINEMANA_APP)

    companion object {
        const val ID = "cinemana"
        const val WATCHME = "io.watchmeplus"
        const val WATCHME_SCREEN = "io.freemytv.CinemaActivity"
        const val WATCHME_ID = "io.freemytv.cinemana.ID"
        const val WATCHME_QUERY = "io.freemytv.cinemana.QUERY"
        const val WATCHME_PLAY_AT = "io.freemytv.cinemana.PLAY_AT"   // long ms; Home sets it through resumeExtraKey
        const val CINEMANA_APP = "com.shabakaty.cbox.cinemana"
        private const val ROW_MAX = 20
        private const val FEATURED_MAX = 8
        private const val LOOKUPS_MAX = 8
    }
}
