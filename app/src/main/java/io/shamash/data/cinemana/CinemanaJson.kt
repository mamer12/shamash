package io.shamash.data.cinemana

import io.shamash.data.json
import io.shamash.domain.Kind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure parsers of Cinemana's answers. Any field may be missing, null, the word "null", a number or a string, and an
 * empty object may come as `[]`, so everything is read from the JSON tree. SPEC: PRV-13, CIN-01, CIN-03
 */
object CinemanaJson {
    private val IMDB_ID = Regex("tt\\d{5,}")
    private const val SERIES = "2"
    private const val IMAGES_HOST = "https://cnth2.shabakaty.cc/"
    private const val MAX_GENRES = 3
    private const val INTRO_MIN_MS = 5_000L
    private const val SCENE_MIN_MS = 500L
    private const val YEAR_DIGITS = 4
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MS = 1000.0
    private val SPACES = Regex("\\s+")
    private val FILM_RATINGS = listOf("", "G", "PG", "PG-13", "R", "NC-17")
    private val SERIES_RATINGS = listOf("", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")

    /** A list of videos (newlyVideosItems, latestMovies, AdvancedSearch, banner, videoSeason). */
    fun titles(text: String): List<CinemanaTitle> = array(json.parseToJsonElement(text)).mapNotNull(::title)

    /** videoGroups: every curated group with its titles, in the server's order. */
    fun groups(text: String): List<CinemanaGroup> =
        array(obj(json.parseToJsonElement(text))?.get("groups")).mapNotNull { g ->
            val o = obj(g) ?: return@mapNotNull null
            val titles = array(o["content"]).mapNotNull(::title)
            CinemanaGroup(o.s("groupsID"), o.s("title"), titles).takeIf { titles.isNotEmpty() }
        }

    /** videoGroups: the "Featured …" groups (movies, series), each as a list. */
    fun featuredGroups(text: String): List<List<CinemanaTitle>> =
        groups(text).filter { it.title.startsWith("Featured") }.map { it.titles }

    /** videoSeason: a series' episodes by season and number, each knowing its series. */
    fun episodes(text: String, seriesId: String): List<CinemanaTitle> =
        titles(text)
            .map { if (it.id == seriesId) it else it.copy(rootId = seriesId) }
            .sortedWith(compareBy({ it.season }, { it.episode }))

    /** allVideoInfo: the title with its people, trailer and the parts to skip. */
    fun details(text: String): CinemanaDetails? {
        val o = obj(json.parseToJsonElement(text)) ?: return null
        val title = title(o) ?: return null
        return CinemanaDetails(
            title = title,
            cast = names(o["actorsInfo"], max = 5),
            directors = names(o["directorsInfo"], max = 2),
            trailer = o.s("trailer").takeIf { it.startsWith("http") },
            intros = array(o["introSkipping"]).mapNotNull { x -> obj(x)?.let { skip(it.s("start"), it.s("end"), INTRO_MIN_MS) } },
            scenes = scenes(obj(o["skippingDurations"])),
        )
    }

    /** transcoddedFiles: the files, lowest resolution first. */
    fun files(text: String): List<VideoFile> =
        array(json.parseToJsonElement(text)).mapNotNull { e ->
            val o = obj(e) ?: return@mapNotNull null
            val url = o.s("videoUrl").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val label = o.s("resolution")
            VideoFile(label, label.filter(Char::isDigit).toIntOrNull() ?: 0, url)
        }.sortedBy { it.height }

    /** translationFiles: the Arabic and English SRT files. */
    fun subtitles(text: String): SubtitleFiles {
        val o = obj(json.parseToJsonElement(text)) ?: return SubtitleFiles()
        return SubtitleFiles(
            arabic = o.s("arTranslationFilePath").takeIf { it.startsWith("http") },
            english = o.s("enTranslationFilePath").takeIf { it.startsWith("http") },
        )
    }

    internal fun title(e: JsonElement): CinemanaTitle? {
        val o = obj(e) ?: return null
        val id = o.s("nb").ifEmpty { return null }
        val en = o.s("en_title")
        val ar = o.s("ar_title")
        val name = en.ifEmpty { ar }.ifEmpty { return null }
        val poster = poster(o).ifEmpty { return null }
        val image = o.s("imgObjUrl")
        val series = o.s("kind") == SERIES
        return CinemanaTitle(
            id = id,
            title = name,
            kind = if (series) Kind.SERIES else Kind.MOVIE,
            poster = poster,
            overview = o.s("en_content").ifEmpty { o.s("ar_content") }.replace(SPACES, " "),
            imdbId = IMDB_ID.find(o.s("imdbUrlRef"))?.value,
            otherTitle = if (name == en) ar else en,
            year = o.s("year").takeIf { it.length == YEAR_DIGITS }.orEmpty(),
            stars = o.s("stars").takeUnless { it.startsWith("0") }.orEmpty(),
            rating = rating(o, series),
            genres = genres(o["categories"]),
            minutes = ((o.s("duration").toDoubleOrNull() ?: 0.0) / SECONDS_PER_MINUTE).roundToInt(),
            season = o.s("season").toIntOrNull() ?: 0,
            episode = o.s("episodeNummer").toIntOrNull() ?: 0,
            rootId = o.s("rootSeries").takeUnless { it == "0" || it == id }.orEmpty(),
            cover = image.takeIf { isCover(it) },
        )
    }

    /** A banner title's "image" is its wide cover; its poster is then a path on the images host. */
    private fun poster(o: JsonObject): String {
        val image = o.s("imgObjUrl")
        val medium = o.s("imgMediumThumbObjUrl")
        val small = o.s("imgThumbObjUrl")
        val chosen = if (isCover(image)) medium.ifEmpty { small } else medium.ifEmpty { image }.ifEmpty { small }
        return if (chosen.isEmpty() || chosen.startsWith("http")) chosen else IMAGES_HOST + chosen
    }

    private fun isCover(image: String) = image.contains("cover-images")

    private fun rating(o: JsonObject, series: Boolean): String {
        val n = o.s(if (series) "seriesRating" else "filmRating").toIntOrNull() ?: return ""
        return (if (series) SERIES_RATINGS else FILM_RATINGS).getOrNull(n).orEmpty()
    }

    private fun genres(e: JsonElement?): List<String> =
        array(e).mapNotNull { obj(it)?.s("en_title") }
            .filter { it.isNotEmpty() && !it.equals("uncategorized", ignoreCase = true) }
            .take(MAX_GENRES)

    private fun names(e: JsonElement?, max: Int): List<String> =
        array(e).mapNotNull { obj(it)?.s("name")?.takeIf(String::isNotEmpty) }.take(max)

    /** skippingDurations: {start: [..], end: [..]} (or [] when there are none). */
    private fun scenes(o: JsonObject?): List<Skip> {
        val starts = array(o?.get("start"))
        val ends = array(o?.get("end"))
        return starts.indices.filter { it < ends.size }.mapNotNull { i -> skip(text(starts[i]), text(ends[i]), SCENE_MIN_MS) }
    }

    private fun skip(start: String, end: String, minMs: Long): Skip? {
        val a = ((start.toDoubleOrNull() ?: return null) * MS).roundToLong()
        val b = ((end.toDoubleOrNull() ?: return null) * MS).roundToLong()
        return Skip(a, b).takeIf { b - a >= minMs }
    }

    private fun array(e: JsonElement?): List<JsonElement> = (e as? JsonArray).orEmpty()
    private fun obj(e: JsonElement?): JsonObject? = e as? JsonObject

    /** The field as trimmed text; "" when missing, null or "null". */
    private fun JsonObject.s(key: String): String = text(this[key])

    private fun text(e: JsonElement?): String {
        val p = e as? JsonPrimitive ?: return ""
        if (p is JsonNull) return ""
        return p.content.trim().takeUnless { it == "null" }.orEmpty()
    }
}
