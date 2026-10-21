package io.shamash.data.cinemana

import io.shamash.data.Http
import io.shamash.data.json
import io.shamash.data.tmdb.TmdbAuth
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The TMDB calls the add-on makes (Cinemana has no trending, recommendations or wide art of its own). Names are asked
 * in English, the language of Cinemana's titles they're matched against. Errors throw. SPEC: CIN-01, CIN-11
 */
class CinemanaTmdb(private val http: Http, clock: () -> Long = System::currentTimeMillis) {
    private val answers = MemoCache<String, String>(clock, MAX_ANSWERS)

    /** This week's trending movies or series, best first. */
    suspend fun trending(key: String, series: Boolean): List<NameYear> =
        TmdbNames.results(get(key, "trending/${kind(series)}/week"), series)

    /** TMDB's recommendations for the title with this IMDb id (looked up as a series or a movie). */
    suspend fun recommendations(key: String, imdbId: String, series: Boolean): List<NameYear> {
        val found = TmdbNames.find(get(key, "find/$imdbId", mapOf("external_source" to "imdb_id")), series) ?: return emptyList()
        return TmdbNames.results(get(key, "${kind(found.second)}/${found.first}/recommendations"), found.second)
    }

    /** The title's wide picture (w1280), or null. */
    suspend fun backdrop(key: String, imdbId: String): String? =
        TmdbNames.backdrop(get(key, "find/$imdbId", mapOf("external_source" to "imdb_id")))?.let { WIDE + it }

    private suspend fun get(key: String, path: String, params: Map<String, String> = emptyMap()): String =
        answers.get("$path?$params", FRESH_MS) {
            val auth = TmdbAuth.of(key)
            val url = "$API/$path".toHttpUrl().newBuilder()
                .apply { (params + ("language" to "en-US") + auth.query).forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            http.text(url.toString(), auth.headers + ("Accept" to "application/json"))
        }

    private fun kind(series: Boolean) = if (series) "tv" else "movie"

    private companion object {
        const val API = "https://api.themoviedb.org/3"
        const val WIDE = "https://image.tmdb.org/t/p/w1280"
        const val MAX_ANSWERS = 64
        const val FRESH_MS = 6 * 3_600_000L
    }
}

/** Pure readers of the TMDB answers above. */
object TmdbNames {
    /** `results` as names and years (`title`/`release_date` for movies, `name`/`first_air_date` for series). */
    fun results(text: String, series: Boolean): List<NameYear> {
        val results = (json.parseToJsonElement(text) as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
        return results.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val name = o.text(if (series) "name" else "title").ifEmpty { return@mapNotNull null }
            NameYear(name, o.text(if (series) "first_air_date" else "release_date").take(YEAR_DIGITS))
        }
    }

    /** `/find`: the TMDB id, and whether it's a series; the wanted kind first, then the other. */
    fun find(text: String, series: Boolean): Pair<Long, Boolean>? {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        for (tv in listOf(series, !series)) {
            val first = (o[if (tv) "tv_results" else "movie_results"] as? JsonArray)?.firstOrNull() as? JsonObject
            val id = (first?.get("id") as? JsonPrimitive)?.longOrNull
            if (id != null) return id to tv
        }
        return null
    }

    /** `/find`: the first result's `backdrop_path`. */
    fun backdrop(text: String): String? {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        return listOf("movie_results", "tv_results").asSequence()
            .mapNotNull { (o[it] as? JsonArray)?.firstOrNull() as? JsonObject }
            .map { it.text("backdrop_path") }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

    private const val YEAR_DIGITS = 4
}
