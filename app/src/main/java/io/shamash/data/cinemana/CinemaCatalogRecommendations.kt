package io.shamash.data.cinemana

import io.shamash.data.softly
import io.shamash.domain.Profile
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal data class CinemaRecommendationRow(
    val id: String,
    val titleKey: String = id,
    val titles: List<CinemanaTitle>,
    val argument: String? = null,
    val style: ShelfStyle = ShelfStyle.POSTER,
)

/** Bounded catalog matching; an unavailable recommendation service cannot hide the catalog. SPEC: CIN-09..11 */
internal class CinemaCatalogRecommendations(private val api: CinemanaApi, private val tmdb: CinemanaTmdb) {
    private val matching = Semaphore(4)

    suspend fun rows(profile: Profile, saved: CinemaSaved, candidates: List<CinemanaTitle>): List<CinemaRecommendationRow> =
        coroutineScope {
            val related = async { because(profile, saved) }
            val trend = async { trending(profile, saved) }
            val picks = async { topPicks(profile, saved, candidates) }
            listOf(CinemaRecommendationRow("cinemana_picks", titles = picks.await())) + related.await() + trend.await()
        }

    suspend fun similar(title: CinemanaTitle, profile: Profile, saved: CinemaSaved): List<CinemanaTitle> {
        val fromTmdb = if (profile.hasTmdb && title.imdbId != null) timed {
            match(tmdb.recommendations(profile.tmdbKey, title.imdbId, title.isSeries), title.isSeries, profile)
        } else emptyList()
        val titles = fromTmdb.ifEmpty {
            val genre = title.genres.firstNotNullOfOrNull(CinemanaGenres::idOf) ?: return@ifEmpty emptyList()
            softly("Cinemana similar", emptyList()) { api.genre(profile.contentLevel, genre, title.isSeries) }
        }
        return recommended(titles, profile, saved).filterNot { it.pageId == title.pageId }.take(16)
    }

    private suspend fun topPicks(profile: Profile, saved: CinemaSaved, candidates: List<CinemanaTitle>): List<CinemanaTitle> = coroutineScope {
        val genres = CinemaRecommendations.genres(saved, profile.genres).entries.filter { it.value > 0 }
            .sortedByDescending { it.value }.mapNotNull { CinemanaGenres.idOf(it.key) }.distinct().take(3)
        val genreTitles = async {
            genres.flatMap { id -> listOf(false, true).map { series -> async {
                softly("Cinemana picks", emptyList()) { matching.withPermit { api.genre(profile.contentLevel, id, series) } }
            } } }.awaitAll().flatten()
        }
        val boosted = if (profile.hasTmdb) CinemaRecommendations.boostSeeds(saved).map { seed -> async {
            val imdb = seed.title.imdbId
            val titles = if (imdb == null) emptyList() else timed {
                match(tmdb.recommendations(profile.tmdbKey, imdb, seed.title.isSeries), seed.title.isSeries, profile)
            }
            titles to seed.rating.recommendationWeight
        } }.awaitAll() else emptyList()
        val boosts = mutableMapOf<String, Double>()
        boosted.forEach { (titles, weight) -> titles.distinctBy { it.pageId }.forEach { boosts[it.pageId] = (boosts[it.pageId] ?: 0.0) + weight } }
        CinemaRecommendations.picks(candidates + genreTitles.await() + boosted.flatMap { it.first }, saved, profile, boosts)
    }

    private suspend fun because(profile: Profile, saved: CinemaSaved): List<CinemaRecommendationRow> {
        if (!profile.hasTmdb) return emptyList()
        return coroutineScope {
            CinemaRecommendations.seeds(saved).map { seed -> async {
                val titles = similar(seed.title, profile, saved)
                CinemaRecommendationRow("because_${seed.title.pageId}", "cinemana_because_${seed.reason}", titles, seed.title.title)
            } }.awaitAll()
        }
    }

    private suspend fun trending(profile: Profile, saved: CinemaSaved): List<CinemaRecommendationRow> {
        if (!profile.hasTmdb) return emptyList()
        return coroutineScope {
            listOf(false, true).map { series -> async {
                val titles = timed { match(tmdb.trending(profile.tmdbKey, series), series, profile, limit = 10) }
                CinemaRecommendationRow(if (series) "cinemana_top_series" else "cinemana_top_movies",
                    titles = recommended(titles, profile, saved).take(10), style = ShelfStyle.RANKED)
            } }.awaitAll()
        }
    }

    private suspend fun match(names: List<NameYear>, series: Boolean, profile: Profile, limit: Int = 16): List<CinemanaTitle> =
        coroutineScope {
            names.take(20).map { name -> async {
                matching.withPermit {
                    softly("Cinemana match", null) {
                        api.search(profile.contentLevel, name.name, series)
                            .firstOrNull { CinemaRecommendations.matches(it, name, series) }
                    }
                }
            } }.awaitAll().filterNotNull().distinctBy { it.pageId }.take(limit)
        }

    private fun recommended(titles: List<CinemanaTitle>, profile: Profile, saved: CinemaSaved) = titles
        .filter { CinemaFilter.allows(it, profile.contentLevel) && !CinemaRecommendations.disliked(it, saved) }
        .distinctBy { it.pageId }

    private suspend fun timed(load: suspend () -> List<CinemanaTitle>): List<CinemanaTitle> =
        withTimeoutOrNull(20_000) { softly("Cinemana recommendations", emptyList(), load) }.orEmpty()
}
