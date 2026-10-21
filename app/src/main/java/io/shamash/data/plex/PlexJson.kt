package io.shamash.data.plex

import io.shamash.data.jArr
import io.shamash.data.jLong
import io.shamash.data.jNum
import io.shamash.data.jObj
import io.shamash.data.jStr
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import java.net.URLEncoder

/** A Plex library (a section). */
data class PlexLibrary(val key: String, val title: String)

/** Turns Plex answers into cards. Art is requested resized (PEX-13); the token only goes where Plex needs it. */
object PlexJson {
    const val APP = "com.plexapp.android"
    private const val POSTER_W = 360
    private const val WIDE_W = 1280
    private const val MAX_W = 1920

    fun libraries(root: JsonElement): List<PlexLibrary> = root.jObj("MediaContainer").jArr("Directory").mapNotNull {
        val key = it.jStr("key")
        val title = it.jStr("title")
        if (key == null || title == null || it.jStr("type") !in setOf("movie", "show")) null else PlexLibrary(key, title)
    }

    fun machineId(root: JsonElement): String? = root.jObj("MediaContainer").jStr("machineIdentifier")

    fun items(root: JsonElement, base: HttpUrl, token: String, machineId: String?, providerId: String): List<MediaItem> =
        root.jObj("MediaContainer").jArr("Metadata").mapNotNull { item(it, base, token, machineId, providerId) }

    private fun item(m: JsonElement, base: HttpUrl, token: String, machineId: String?, providerId: String): MediaItem? {
        val ratingKey = m.jStr("ratingKey") ?: return null
        val type = m.jStr("type")
        val episode = type == "episode"
        val title = (if (episode) m.jStr("grandparentTitle") else null) ?: m.jStr("title") ?: return null
        val kind = when (type) { "movie" -> Kind.MOVIE; "episode", "show", "season" -> Kind.SERIES; else -> Kind.VIDEO }
        val duration = m.jNum("duration")
        val offset = m.jNum("viewOffset")
        val poster = (if (episode) m.jStr("grandparentThumb") else null) ?: m.jStr("thumb")
        val art = (if (episode) m.jStr("grandparentArt") else null) ?: m.jStr("art")
        return MediaItem(
            id = "$providerId:$ratingKey", providerId = providerId, title = title, kind = kind,
            open = target(machineId, ratingKey),
            subtitle = subtitle(m, episode),
            overview = m.jStr("summary").orEmpty().take(400),
            posterUrl = poster?.let { image(base, it, token, POSTER_W, POSTER_W * 3 / 2) },
            backdropUrl = art?.let { image(base, it, token, WIDE_W, WIDE_W * 9 / 16) },
            appPackage = APP,
            progress = if (offset != null && duration != null && duration > 0) (offset / duration).toFloat().coerceIn(0f, 1f) else null,
            rating = m.jStr("contentRating"),
        )
    }

    private fun subtitle(m: JsonElement, episode: Boolean): String {
        if (!episode) return m.jNum("year")?.toInt()?.toString().orEmpty()
        val s = m.jNum("parentIndex")?.toInt()
        val e = m.jNum("index")?.toInt()
        val code = if (s != null && e != null) "S$s E$e" else ""
        return listOf(code, m.jStr("title").orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** The Plex app's own link to a title; the Opener falls back to opening the app. */
    fun target(machineId: String?, ratingKey: String): OpenTarget =
        if (machineId == null) OpenTarget.App(APP)
        else OpenTarget.AppPage(
            "plex://server://$machineId/com.plexapp.plugins.library/details?key=" +
                URLEncoder.encode("/library/metadata/$ratingKey", "UTF-8"),
            APP,
        )

    /** A resized picture through Plex's own transcoder; never wider than 1920 px. */
    fun image(base: HttpUrl, path: String, token: String, width: Int, height: Int): String =
        base.newBuilder().addPathSegments("photo/:/transcode")
            .addQueryParameter("width", width.coerceAtMost(MAX_W).toString())
            .addQueryParameter("height", height.toString())
            .addQueryParameter("minSize", "1")
            .addQueryParameter("url", path)
            .addQueryParameter("X-Plex-Token", token)
            .build().toString()

    /** What a signed-in account offers: servers with a token and their best address. */
    data class Server(val name: String, val uri: String, val accessToken: String)

    fun servers(root: JsonElement): List<Server> = (root as? JsonArray)?.mapNotNull { d ->
        if ("server" !in d.jStr("provides").orEmpty().split(',')) return@mapNotNull null
        val token = d.jStr("accessToken") ?: return@mapNotNull null
        val direct = d.jArr("connections").filter { c -> (c as? JsonObject)?.get("relay")?.jsonPrimitive?.booleanOrNull != true }
        val best = direct.firstOrNull { it.jStr("uri")?.startsWith("https://") == true } ?: direct.firstOrNull()
        val uri = best.jStr("uri") ?: return@mapNotNull null
        Server(d.jStr("name") ?: "Plex", uri, token)
    } ?: emptyList()
}
