package io.shamash.data.cinemana

import io.shamash.domain.Kind
import kotlinx.serialization.Serializable

/** A movie, a series or one episode in Cinemana's public catalog (also how titles are saved on the TV). */
@Serializable
data class CinemanaTitle(
    val id: String,
    val title: String,
    val kind: Kind,
    val poster: String,
    val overview: String,
    val imdbId: String?,
    val otherTitle: String = "",
    val year: String = "",
    val stars: String = "",
    val rating: String = "",                 // "PG-13", "TV-MA"; "" when unknown
    val genres: List<String> = emptyList(),  // English names, at most three
    val minutes: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val rootId: String = "",                 // the series of an episode ("" for movies and series roots)
    val cover: String? = null,               // Cinemana's own wide art (banner titles)
    val backdrop: String? = null,            // TMDB's wide art, once looked up
) {
    val isSeries: Boolean get() = kind == Kind.SERIES

    /** The page a title opens: an episode opens its series. SPEC: CIN-03 */
    val pageId: String get() = rootId.ifEmpty { id }

    /** The best wide picture known (Cinemana's cover, else TMDB's). */
    val wide: String? get() = cover ?: backdrop
}

/** A part of a video to skip: an intro, or a scene Cinemana marks for families. Milliseconds. */
@Serializable
data class Skip(val startMs: Long, val endMs: Long)

/** What a title's page shows beyond the list fields. */
data class CinemanaDetails(
    val title: CinemanaTitle,
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val trailer: String? = null,
    val intros: List<Skip> = emptyList(),
    val scenes: List<Skip> = emptyList(),
)

/** One playable file. */
data class VideoFile(val label: String, val height: Int, val url: String)

/** Subtitle files of one video (either may be missing). */
data class SubtitleFiles(val arabic: String? = null, val english: String? = null) {
    fun forLanguage(language: String): String? = when (language) {
        SubtitleLanguage.ARABIC -> arabic
        SubtitleLanguage.ENGLISH -> english
        else -> null
    }
}

/** Subtitle choices, as saved. */
object SubtitleLanguage {
    const val ARABIC = "ar"
    const val ENGLISH = "en"
    const val OFF = "off"
    val ALL = listOf(ARABIC, ENGLISH, OFF)
}

/** One of the server's curated home groups ("Featured Movies"…). */
data class CinemanaGroup(val id: String, val title: String, val titles: List<CinemanaTitle>)

/** A title name and year from another catalog (TMDB), to be matched to Cinemana's. */
data class NameYear(val name: String, val year: String)
