package io.shamash.data.tmdb

import io.shamash.data.interleave
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget

/** One service's popular movies and shows, as fetched. */
data class ServiceTitles(val service: StreamingService, val movies: List<TmdbTitle>, val shows: List<TmdbTitle>)

/** Pure assembly of TMDB titles into rows, the carousel and search results. */
object TmdbCatalog {
    const val PROVIDER_ID = "tmdb"
    private const val ROW_MAX = 20
    private const val TOP = 10
    private const val FEATURED_PER_SERVICE = 4
    private const val FEATURED_MAX = 8

    /** "Popular on <service>": movies and shows in turns, with wide art, each title once, ≤ 20. SPEC: PRV-02 */
    fun popular(titles: ServiceTitles): List<MediaItem> {
        val wide = listOf(titles.movies, titles.shows).map { list -> list.filter { it.backdropPath != null } }
        return interleave(wide, ROW_MAX) { it.title.lowercase() }.map { it.toItem(titles.service) }
    }

    /** The most popular titles across the user's services, by TMDB popularity, distinct, with a poster. SPEC: PRV-03 */
    fun topTen(all: List<ServiceTitles>): List<MediaItem> =
        all.flatMap { s -> (s.movies + s.shows).map { it to s.service } }
            .filter { (title, _) -> title.posterPath != null }
            .sortedByDescending { (title, _) -> title.popularity }
            .distinctBy { (title, _) -> title.title.lowercase() }
            .take(TOP)
            .map { (title, service) -> title.toItem(service) }

    /** Carousel candidates: a few popular titles per service in turns, Apple TV first. SPEC: HOME-10 */
    fun featured(rows: Map<StreamingService, List<MediaItem>>): List<MediaItem> {
        val ordered = rows.entries.sortedBy { (service, _) -> if (service == StreamingService.APPLE_TV) 0 else 1 }
        val lists = ordered.map { (_, items) -> items.filter { it.backdropUrl != null }.take(FEATURED_PER_SERVICE) }
        return interleave(lists, FEATURED_MAX) { it.id }
    }

    /**
     * Search hits that stream on one of [mine], grouped by service in [mine]'s order, TMDB's relevance within each.
     * SPEC: SRCH-01
     */
    fun bySearch(hits: List<Pair<TmdbTitle, List<StreamingService>>>, mine: List<StreamingService>): List<MediaItem> =
        hits.mapNotNull { (title, services) -> mine.firstOrNull { it in services }?.let { title to it } }
            .filter { (title, _) -> title.posterPath != null || title.backdropPath != null }
            .sortedBy { (_, service) -> mine.indexOf(service) }
            .map { (title, service) -> title.toItem(service) }

    /** Apple TV and Netflix titles open on their own page (looked up at open time); others open the app. SPEC: PRV-05 */
    fun TmdbTitle.toItem(service: StreamingService) = MediaItem(
        id = "$PROVIDER_ID:${kind.name.lowercase()}:$id",
        providerId = PROVIDER_ID,
        title = title,
        kind = kind,
        open = if (service.hasTitleLinks) {
            OpenTarget.Lookup(id, kind, title, service.appPackage)
        } else {
            OpenTarget.App(service.appPackage)
        },
        subtitle = service.displayName,
        overview = overview,
        posterUrl = TmdbImages.poster(posterPath),
        backdropUrl = TmdbImages.wide(backdropPath),
        appPackage = service.appPackage,
    )
}
