package io.shamash.data.jellyfin

import io.shamash.data.jArr
import io.shamash.data.jLong
import io.shamash.data.jObj
import io.shamash.data.jStr
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl

data class JellyfinLibrary(val id: String, val name: String)

/** Turns Jellyfin and Emby answers into cards. Pictures are asked for at the size drawn (PEX-13). */
object JellyfinJson {
    private const val POSTER_W = 360
    private const val WIDE_W = 1280
    private const val TICKS_PER_MS = 10_000.0

    /** The id and token from `AuthenticateByName`. */
    fun login(root: JsonElement): Pair<String, String>? {
        val token = root.jStr("AccessToken") ?: return null
        val user = root.jObj("User").jStr("Id") ?: return null
        return user to token
    }

    fun libraries(root: JsonElement): List<JellyfinLibrary> = root.jArr("Items").mapNotNull {
        val id = it.jStr("Id") ?: return@mapNotNull null
        val type = it.jStr("CollectionType")
        if (type != null && type !in setOf("movies", "tvshows", "homevideos", "boxsets", "musicvideos")) null
        else JellyfinLibrary(id, it.jStr("Name") ?: return@mapNotNull null)
    }

    /** `Items` of a list answer, or the array itself (Latest answers with a bare array). */
    fun items(root: JsonElement, base: HttpUrl, providerId: String, appPackage: String): List<MediaItem> {
        val list = (root as? JsonArray)?.toList() ?: root.jArr("Items")
        return list.mapNotNull { item(it, base, providerId, appPackage) }
    }

    private fun item(m: JsonElement, base: HttpUrl, providerId: String, appPackage: String): MediaItem? {
        val id = m.jStr("Id") ?: return null
        val type = m.jStr("Type")
        val episode = type == "Episode"
        val title = (if (episode) m.jStr("SeriesName") else null) ?: m.jStr("Name") ?: return null
        val kind = when (type) { "Movie" -> Kind.MOVIE; "Episode", "Series", "Season" -> Kind.SERIES; else -> Kind.VIDEO }
        val posterId = if (episode) m.jStr("SeriesId") ?: id else id
        val posterTag = if (episode) m.jStr("SeriesPrimaryImageTag") else m.jObj("ImageTags").jStr("Primary")
        val backdropId = m.jArr("BackdropImageTags").firstOrNull()?.let { id } ?: m.jStr("ParentBackdropItemId")
        val position = m.jObj("UserData").jLong("PlaybackPositionTicks")
        val total = m.jLong("RunTimeTicks")
        return MediaItem(
            id = "$providerId:$id", providerId = providerId, title = title, kind = kind,
            open = OpenTarget.App(appPackage),
            subtitle = subtitle(m, episode),
            overview = m.jStr("Overview").orEmpty().take(400),
            posterUrl = if (posterTag != null) image(base, posterId, "Primary", POSTER_W) else null,
            backdropUrl = backdropId?.let { image(base, it, "Backdrop", WIDE_W) },
            appPackage = appPackage,
            progress = if (position != null && total != null && total > 0) (position.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
            rating = m.jStr("OfficialRating"),
        )
    }

    private fun subtitle(m: JsonElement, episode: Boolean): String {
        if (!episode) return m.jLong("ProductionYear")?.toString().orEmpty()
        val s = m.jLong("ParentIndexNumber")
        val e = m.jLong("IndexNumber")
        val code = if (s != null && e != null) "S$s E$e" else ""
        return listOf(code, m.jStr("Name").orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** A picture at [width] px. No token: both servers serve pictures of known item ids without one. */
    fun image(base: HttpUrl, itemId: String, type: String, width: Int): String =
        base.newBuilder().addPathSegments("Items/$itemId/Images/$type").addQueryParameter("maxWidth", width.toString())
            .build().toString()
}
