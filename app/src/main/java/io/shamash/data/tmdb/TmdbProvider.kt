package io.shamash.data.tmdb

import io.shamash.data.Refresh
import io.shamash.data.TimedCache
import io.shamash.data.softly
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * What's popular on the user's streaming services (subscribed and installed), from TMDB. Hidden without a TMDB key.
 * SPEC: PRV-01, PRV-02, PRV-03, HOME-04, HOME-05
 */
class TmdbProvider(
    private val client: TmdbClient,
    private val isInstalled: (String) -> Boolean,
    clock: () -> Long = System::currentTimeMillis,
) : Provider {
    override val id = TmdbCatalog.PROVIDER_ID

    private data class Rows(val popular: Map<StreamingService, List<MediaItem>>, val top: List<MediaItem>)

    private data class RowsKey(val key: String, val region: String, val services: List<StreamingService>)

    private val ratings = TmdbRatings(client)

    private val cache = TimedCache<RowsKey, Rows>(clock) {
        if (it.popular.isEmpty()) Refresh.RETRY_EMPTY else EVERY
    }

    override fun isEnabled(profile: Profile) = profile.hasTmdb

    override suspend fun shelves(profile: Profile): List<Shelf> {
        val rows = rows(profile)
        val top = Shelf("tmdb_top10", "top10", ShelfStyle.RANKED, ratings.rate(rows.top, profile))
        val popular = rows.popular.map { (service, items) ->
            Shelf("tmdb_popular_${service.name.lowercase()}", "popular_on", ShelfStyle.WIDE, ratings.rate(items, profile), service.displayName)
        }
        return (listOf(top) + popular).filter { it.items.isNotEmpty() }
    }

    override suspend fun featured(profile: Profile): List<MediaItem> =
        ratings.rate(TmdbCatalog.featured(rows(profile).popular), profile)

    override suspend fun search(query: String, profile: Profile): List<MediaItem> {
        val mine = myServices(profile)
        if (!isEnabled(profile) || query.isBlank() || mine.isEmpty()) return emptyList()
        return softly("TMDB search", emptyList()) {
            val hits = client.search(profile.tmdbKey, query.trim()).take(SEARCH_HITS)
            val region = profile.region.uppercase()
            val withServices = coroutineScope {
                hits.map { title -> async { title to services(profile.tmdbKey, title, region) } }.awaitAll()
            }
            ratings.rate(TmdbCatalog.bySearch(withServices, mine), profile)
        }
    }

    private suspend fun services(key: String, title: TmdbTitle, region: String): List<StreamingService> =
        softly("TMDB providers ${title.id}", emptyList()) { client.services(key, title, region) }

    private suspend fun rows(profile: Profile): Rows {
        val mine = myServices(profile)
        if (!isEnabled(profile) || mine.isEmpty()) return Rows(emptyMap(), emptyList())
        val key = RowsKey(profile.tmdbKey, profile.region.uppercase(), mine)
        return cache.get(key) { softly("TMDB rows", Rows(emptyMap(), emptyList())) { fetchRows(key) } }
    }

    private suspend fun fetchRows(key: RowsKey): Rows {
        val ids = client.serviceIds(key.key, key.region).filterKeys { it in key.services }
        val titles = coroutineScope {
            ids.map { (service, providerIds) -> async { fetchService(key, service, providerIds) } }.awaitAll()
        }
        val popular = titles.associate { it.service to TmdbCatalog.popular(it) }.filterValues { it.isNotEmpty() }
        return Rows(popular, TmdbCatalog.topTen(titles))
    }

    private suspend fun fetchService(key: RowsKey, service: StreamingService, providerIds: List<Int>): ServiceTitles {
        suspend fun popular(kind: Kind) = softly("TMDB popular ${service.name} $kind", emptyList()) {
            client.popular(key.key, kind, providerIds, key.region)
        }
        return coroutineScope {
            val movies = async { popular(Kind.MOVIE) }
            val shows = async { popular(Kind.SERIES) }
            ServiceTitles(service, movies.await(), shows.await())
        }
    }

    /** Services the user pays for and has on this TV, in a stable order. SPEC: PRV-01 */
    private fun myServices(profile: Profile): List<StreamingService> =
        StreamingService.entries.filter { it.appPackage in profile.subscriptions && isInstalled(it.appPackage) }

    private companion object {
        const val EVERY = 6 * Refresh.HOUR
        const val SEARCH_HITS = 10
    }
}
