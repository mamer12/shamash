package io.shamash.data.cinemana

import io.shamash.data.Http
import io.shamash.domain.ContentLevel
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/** Cinemana's own family filter as its `level` parameter: 0 everything, 2 up to PG-13 / TV-14, 3 kids. SPEC: CIN-08 */
object CinemanaLevel {
    fun of(level: ContentLevel): String = when (level) {
        ContentLevel.ALL -> "0"
        ContentLevel.FAMILY -> "2"
        ContentLevel.KIDS -> "3"
    }
}

/** Cinemana's category ids for the genres Shamash shows, by English name (lowercase). */
object CinemanaGenres {
    val IDS = linkedMapOf(
        "action" to "84", "comedy" to "59", "drama" to "62", "thriller" to "80", "crime" to "60",
        "sci-fi" to "78", "horror" to "70", "romance" to "77", "adventure" to "56", "animation" to "57",
        "fantasy" to "67", "mystery" to "76", "documentary" to "61",
    )

    fun idOf(genre: String): String? = IDS[genre.trim().lowercase()]
}

/**
 * Cinemana's public API (no key). Every list carries the profile's family filter; answers are kept a few minutes in
 * memory (the preview and the player ask for the same files), each request tried twice. Errors throw: callers fail
 * soft. SPEC: CIN-01, CIN-08, PRV-13
 */
class CinemanaApi(
    private val http: Http,
    clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = API,
) {
    private val answers = MemoCache<String, String>(clock, MAX_ANSWERS)

    suspend fun banner(level: ContentLevel) = CinemanaJson.titles(get("banner/level/${CinemanaLevel.of(level)}"))

    suspend fun groups(level: ContentLevel, arabic: Boolean): List<CinemanaGroup> =
        CinemanaJson.groups(get("videoGroups/lang/${if (arabic) "ar" else "en"}/level/${CinemanaLevel.of(level)}/"))

    suspend fun newly(level: ContentLevel, count: Int) =
        CinemanaJson.titles(get("newlyVideosItems/level/${CinemanaLevel.of(level)}/offset/$count/page/0/"))

    suspend fun latest(level: ContentLevel, series: Boolean, page: Int = 0): List<CinemanaTitle> {
        val path = if (series) "latestSeries" else "latestMovies"
        return CinemanaJson.titles(get("$path/level/${CinemanaLevel.of(level)}/itemsPerPage/$PAGE/page/$page/"))
    }

    /** One genre, recent years first (the way Netflix fills its genre rows). */
    suspend fun genre(level: ContentLevel, categoryId: String, series: Boolean, page: Int = 0): List<CinemanaTitle> =
        CinemanaJson.titles(get(search(level, series, page) { addQueryParameter("category_id", categoryId).addQueryParameter("year", RECENT) }))

    suspend fun search(level: ContentLevel, query: String, series: Boolean): List<CinemanaTitle> =
        CinemanaJson.titles(get(search(level, series, 0) { addQueryParameter("videoTitle", query.trim()) }))

    suspend fun details(id: String, level: ContentLevel = ContentLevel.ALL): CinemanaDetails? =
        CinemanaJson.details(get("allVideoInfo/id/${checkedId(id)}/level/${CinemanaLevel.of(level)}", FILES_FRESH_MS))

    suspend fun episodes(seriesId: String, level: ContentLevel = ContentLevel.ALL): List<CinemanaTitle> =
        CinemanaJson.episodes(get("videoSeason/id/${checkedId(seriesId)}/level/${CinemanaLevel.of(level)}"), seriesId)

    suspend fun files(id: String, level: ContentLevel = ContentLevel.ALL): List<VideoFile> =
        CinemanaJson.files(get("transcoddedFiles/id/${checkedId(id)}/level/${CinemanaLevel.of(level)}", FILES_FRESH_MS))

    suspend fun subtitles(id: String, level: ContentLevel = ContentLevel.ALL): SubtitleFiles =
        CinemanaJson.subtitles(get("translationFiles/id/${checkedId(id)}/level/${CinemanaLevel.of(level)}", FILES_FRESH_MS))

    private fun search(
        level: ContentLevel, series: Boolean, page: Int, more: okhttp3.HttpUrl.Builder.() -> okhttp3.HttpUrl.Builder,
    ): String = "${baseUrl}AdvancedSearch".toHttpUrl().newBuilder()
        .addQueryParameter("level", CinemanaLevel.of(level))
        .addQueryParameter("type", if (series) "series" else "movies")
        .more()
        .addQueryParameter("page", page.toString())
        .build().toString().removePrefix(baseUrl)

    /** The answer for [path], from memory while fresh, else the network (two tries). */
    private suspend fun get(path: String, freshMs: Long = FRESH_MS): String =
        answers.get(path, freshMs) { fetch(path) }

    private suspend fun fetch(path: String): String {
        var last: IOException? = null
        repeat(TRIES) { attempt ->
            if (attempt > 0) delay(RETRY_MS)
            try {
                return checked(http.text(baseUrl + path, ACCEPT))
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("no answer")
    }

    /** The server sometimes answers an empty or HTML page: a failure, not an empty list. */
    private fun checked(body: String): String {
        val start = body.trimStart().firstOrNull()
        if (start != '[' && start != '{') throw IOException("not JSON")
        return body
    }

    private fun checkedId(id: String): String {
        require(id.length in 1..20 && id.all { it in '0'..'9' })
        return id
    }

    companion object {
        const val API = "https://cinemana.shabakaty.cc/api/android/"
        private val ACCEPT = mapOf("Accept" to "application/json")
        private const val TRIES = 2
        private const val RETRY_MS = 700L
        private const val PAGE = 24
        private const val RECENT = "2010,2030"
        private const val MAX_ANSWERS = 96
        private const val FRESH_MS = 30 * 60_000L
        private const val FILES_FRESH_MS = 10 * 60_000L
    }
}
