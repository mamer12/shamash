package io.shamash.data.tmdb

import io.shamash.data.Http
import io.shamash.domain.Kind
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

/** How a TMDB key is sent: a v3 key as `api_key`, a v4 token (a JWT) as a Bearer header. SPEC: PRV-04 */
data class TmdbAuth(val query: Map<String, String>, val headers: Map<String, String>) {
    companion object {
        fun of(key: String): TmdbAuth {
            val trimmed = key.trim()
            return if (trimmed.startsWith("eyJ")) {
                TmdbAuth(emptyMap(), mapOf("Authorization" to "Bearer $trimmed"))
            } else {
                TmdbAuth(mapOf("api_key" to trimmed), emptyMap())
            }
        }
    }
}

/** The TMDB calls Shamash makes; answers go through [TmdbJson]. Errors throw (callers fail soft). */
class TmdbClient(
    private val http: Http,
    private val language: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    suspend fun serviceIds(key: String, region: String): Map<StreamingService, List<Int>> =
        TmdbJson.serviceIds(get(key, "watch/providers/movie", mapOf("watch_region" to region)))

    /** The most popular titles streaming with a subscription on [providerIds] in [region]. SPEC: PRV-02 */
    suspend fun popular(key: String, kind: Kind, providerIds: List<Int>, region: String): List<TmdbTitle> {
        val params = mapOf(
            "watch_region" to region,
            "with_watch_providers" to providerIds.joinToString("|"),
            "with_watch_monetization_types" to "flatrate",
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
            "vote_count.gte" to "20",
        )
        return TmdbJson.titles(get(key, "discover/${path(kind)}", params), kind)
    }

    suspend fun search(key: String, query: String): List<TmdbTitle> =
        TmdbJson.search(get(key, "search/multi", mapOf("query" to query, "include_adult" to "false")))

    suspend fun services(key: String, title: TmdbTitle, region: String): List<StreamingService> =
        TmdbJson.flatrate(get(key, "${path(title.kind)}/${title.id}/watch/providers"), region)

    /** The title's age rating in [region] (or the US), null when unknown. SPEC: PRF-12 */
    suspend fun certification(key: String, title: TmdbTitle, region: String): String? {
        val path = if (title.kind == Kind.SERIES) "tv/${title.id}/content_ratings" else "movie/${title.id}/release_dates"
        return TmdbJson.certification(get(key, path), title.kind, region)
    }

    suspend fun findByImdb(key: String, imdbId: String): TmdbTitle? =
        TmdbJson.found(get(key, "find/$imdbId", mapOf("external_source" to "imdb_id")))

    private suspend fun get(key: String, path: String, params: Map<String, String> = emptyMap()): String {
        val auth = TmdbAuth.of(key)
        val url = "$API/$path".toHttpUrl().newBuilder()
            .apply { (params + ("language" to language()) + auth.query).forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return http.text(url.toString(), auth.headers + ("Accept" to "application/json"))
    }

    private fun path(kind: Kind) = if (kind == Kind.SERIES) "tv" else "movie"

    private companion object {
        const val API = "https://api.themoviedb.org/3"
    }
}
