package io.shamash.data.tmdb

import io.shamash.data.Certification
import io.shamash.data.json
import io.shamash.domain.Kind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** A movie or show as TMDB lists it. */
data class TmdbTitle(
    val id: Int,
    val kind: Kind,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val popularity: Double = 0.0,
)

/** Pure parsers of TMDB's answers (the client fetches, these read). */
object TmdbJson {
    @Serializable
    private data class Results<T>(val results: List<T> = emptyList())

    @Serializable
    private data class WatchProvider(
        @SerialName("provider_id") val id: Int,
        @SerialName("provider_name") val name: String = "",
    )

    @Serializable
    private data class Title(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        val popularity: Double = 0.0,
        @SerialName("media_type") val mediaType: String? = null,
    ) {
        fun toTitle(kind: Kind) = (title ?: name)?.takeIf { it.isNotBlank() }?.let {
            TmdbTitle(id, kind, it.trim(), overview.orEmpty(), posterPath, backdropPath, popularity)
        }
    }

    @Serializable
    private data class Offers(val flatrate: List<WatchProvider> = emptyList())

    @Serializable
    private data class Found(
        @SerialName("movie_results") val movies: List<Title> = emptyList(),
        @SerialName("tv_results") val shows: List<Title> = emptyList(),
    )

    /** `/watch/providers/movie`: each known service's TMDB provider ids (a service can have several). SPEC: PRV-01 */
    fun serviceIds(text: String): Map<StreamingService, List<Int>> =
        json.decodeFromString<Results<WatchProvider>>(text).results
            .mapNotNull { p -> StreamingService.fromProviderName(p.name)?.let { it to p.id } }
            .groupBy({ it.first }, { it.second })

    /** `/discover/{movie,tv}`. */
    fun titles(text: String, kind: Kind): List<TmdbTitle> =
        json.decodeFromString<Results<Title>>(text).results.mapNotNull { it.toTitle(kind) }

    /** `/search/multi`: movies and shows only, in TMDB's order. */
    fun search(text: String): List<TmdbTitle> =
        json.decodeFromString<Results<Title>>(text).results.mapNotNull { r -> kindOf(r.mediaType)?.let(r::toTitle) }

    /** `/{movie,tv}/{id}/watch/providers`: the services that stream it in [region] with a subscription. */
    fun flatrate(text: String, region: String): List<StreamingService> {
        val byRegion = json.parseToJsonElement(text).jsonObject["results"] as? JsonObject ?: return emptyList()
        val offers = byRegion[region.uppercase()] ?: return emptyList()
        return json.decodeFromJsonElement<Offers>(offers).flatrate
            .mapNotNull { StreamingService.fromProviderName(it.name) }
            .distinct()
    }

    /**
     * The age rating from `/movie/{id}/release_dates` or `/tv/{id}/content_ratings`: [region]'s first, else the US one,
     * normalized for the content gate; null when neither says (the title stays hidden for restricted profiles).
     * SPEC: PRF-12
     */
    fun certification(text: String, kind: Kind, region: String): String? {
        val results = json.parseToJsonElement(text).jsonObject["results"] as? JsonArray ?: return null
        fun rated(country: String): String? = results.firstNotNullOfOrNull { entry ->
            val o = entry as? JsonObject ?: return@firstNotNullOfOrNull null
            if (o["iso_3166_1"]?.jsonPrimitive?.contentOrNull != country) return@firstNotNullOfOrNull null
            val raw = if (kind == Kind.SERIES) listOf(o["rating"]?.jsonPrimitive?.contentOrNull)
            else (o["release_dates"] as? JsonArray).orEmpty().map { (it as? JsonObject)?.get("certification")?.jsonPrimitive?.contentOrNull }
            raw.firstNotNullOfOrNull { Certification.normalize(it) }
        }
        return rated(region.uppercase()) ?: rated("US")
    }

    /** `/find/{imdb}`: the title with a wide picture, a movie before a show. */
    fun found(text: String): TmdbTitle? {
        val found = json.decodeFromString<Found>(text)
        return (found.movies.mapNotNull { it.toTitle(Kind.MOVIE) } + found.shows.mapNotNull { it.toTitle(Kind.SERIES) })
            .firstOrNull { it.backdropPath != null }
    }

    private fun kindOf(mediaType: String?): Kind? = when (mediaType) {
        "movie" -> Kind.MOVIE
        "tv" -> Kind.SERIES
        else -> null
    }
}

/** TMDB image URLs at the sizes Shamash draws them. SPEC: PERF-04 */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun poster(path: String?): String? = path?.let { BASE + "w342" + it }

    /** 16:9 art, wide enough for the carousel. SPEC: ARCH-10 */
    fun wide(path: String?): String? = path?.let { BASE + "w1280" + it }
}
