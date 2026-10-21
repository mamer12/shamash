package io.shamash.data.cinemana

import io.shamash.domain.ContentGate
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import java.util.Locale
import kotlin.math.abs

/** Ranking is deterministic and independent of networking. SPEC: CIN-09..11 */
object CinemaRecommendations {
    data class Seed(val title: CinemanaTitle, val reason: String)

    fun genres(saved: CinemaSaved, selected: Set<String>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
        fun add(genres: List<String>, weight: Double) {
            genres.forEach { genre ->
                val key = genre.trim().lowercase(Locale.ROOT)
                weights[key] = weights.getOrDefault(key, 0.0) + weight
            }
        }
        add(selected.toList(), 2.0)
        saved.myList.forEach { add(it.genres, 1.0) }
        saved.history.filter { it.started }.distinctBy { it.title.pageId }
            .forEach { add(it.title.genres, if (it.finished) 2.0 else 1.0) }
        saved.ratings.forEach { add(it.title.genres, it.rating.genreWeight) }
        return weights
    }

    fun seeds(saved: CinemaSaved): List<Seed> = (
        saved.ratings.filter { it.rating == CinemaRating.LOVE }.map { Seed(it.title, "loved") } +
            saved.history.filter { it.started }.sortedByDescending { it.updatedAt }.map { Seed(it.title, "watched") } +
            saved.ratings.filter { it.rating == CinemaRating.LIKE }.map { Seed(it.title, "liked") }
        ).filter { it.title.imdbId != null && !disliked(it.title, saved) }.distinctBy { it.title.pageId }.take(3)

    fun boostSeeds(saved: CinemaSaved): List<RatedTitle> = saved.ratings
        .filter { it.rating != CinemaRating.DISLIKE }
        .sortedByDescending { it.rating.recommendationWeight }.distinctBy { it.title.pageId }.take(3)

    fun picks(titles: List<CinemanaTitle>, saved: CinemaSaved, profile: Profile, boosts: Map<String, Double>): List<CinemanaTitle> {
        val weights = genres(saved, profile.genres)
        val seen = saved.history.filter { it.started }.map { it.title.pageId }.toSet()
        return titles.distinctBy { it.pageId }
            .filter { !disliked(it, saved) && it.pageId !in seen && CinemaFilter.allows(it, profile.contentLevel) }
            .sortedByDescending { t -> t.genres.sumOf { weights[it.lowercase(Locale.ROOT)] ?: 0.0 } + (boosts[t.pageId] ?: 0.0) }
            .take(24)
    }

    fun disliked(title: CinemanaTitle, saved: CinemaSaved): Boolean =
        saved.ratings.any { it.title.pageId == title.pageId && it.rating == CinemaRating.DISLIKE }

    fun matches(title: CinemanaTitle, wanted: NameYear, series: Boolean): Boolean {
        val year = wanted.year.toIntOrNull()
        val actual = title.year.toIntOrNull()
        return title.isSeries == series &&
            (normalize(title.title) == normalize(wanted.name) || normalize(title.otherTitle) == normalize(wanted.name)) &&
            (year == null || actual == null || abs(year - actual) <= 1)
    }

    fun order(rows: List<Shelf>): List<Shelf> {
        val because = rows.filter { it.id.startsWith("because_") }.take(3)
        val first = listOf("continue_watching", "cinemana_picks", because.firstOrNull()?.id, "cinemana_top_movies", "cinemana_top_series")
        val ids = first.filterNotNull() + because.drop(1).map { it.id } + "cinemana_my_list"
        return (ids.mapNotNull { id -> rows.find { it.id == id } } + rows.filterNot { it.id in ids || it.id.startsWith("because_") })
            .filter { it.items.isNotEmpty() }
    }

    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private fun normalize(name: String) = name.lowercase(Locale.ROOT).replace(punctuation, "")
}

/** Unknown stored classifications cannot leak into a restricted profile. SPEC: CIN-08 */
object CinemaFilter {
    fun allows(title: CinemanaTitle, level: ContentLevel): Boolean = ContentGate.allowsRating(title.rating, level)
}
