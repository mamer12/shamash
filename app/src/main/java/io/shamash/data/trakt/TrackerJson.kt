package io.shamash.data.trakt

import io.shamash.data.jArr
import io.shamash.data.jLong
import io.shamash.data.jObj
import io.shamash.data.jStr
import io.shamash.data.json
import io.shamash.domain.Kind
import io.shamash.domain.MyListEntry
import io.shamash.domain.OpenTarget
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.time.Instant

/**
 * Lenient readers for Trakt's and Simkl's answers (fixtures under `fixtures/trakt`): anything missing or odd is
 * skipped, never thrown. SPEC: PEX-26
 */
object TrackerJson {
    fun instant(text: String?): Long? = text?.let { runCatching { Instant.parse(it.trim()).toEpochMilli() }.getOrNull() }

    fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    /** "science-fiction" -> "Science Fiction" (the English genre names the taste profile uses). */
    fun genre(slug: String): String =
        slug.split('-', ' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun obj(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    /** A device-code answer (both trackers use these names). Null when it lacks a code or address. */
    fun deviceCode(body: String, now: Long): DeviceCode? {
        val root = obj(body) ?: return null
        val user = root.jStr("user_code") ?: return null
        val url = root.jStr("verification_url") ?: root.jStr("verification_uri") ?: return null
        val device = root.jStr("device_code") ?: user
        val interval = (root.jLong("interval") ?: 5).toInt().coerceIn(1, 60)
        val expires = (root.jLong("expires_in") ?: 600).coerceIn(30, 3600)
        return DeviceCode(user, url, device, interval, now + expires * 1000)
    }

    /** An OAuth token answer; [now] dates its expiry. */
    fun session(body: String, now: Long): Session? {
        val root = obj(body) ?: return null
        val access = root.jStr("access_token") ?: return null
        val expiresIn = root.jLong("expires_in")
        return Session(access, root.jStr("refresh_token"), expiresIn?.let { now + it * 1000 })
    }

    fun error(body: String): String? = obj(body).jStr("error")

    private fun title(kind: Kind, tracker: String, media: JsonElement?, at: Long?): TrackedTitle? {
        val name = media.jStr("title")?.trim()?.take(120) ?: return null
        val ids = media.jObj("ids")
        val tmdb = ids.jStr("tmdb")?.toIntOrNull()
        val own = ids.jStr(tracker) ?: ids.jStr("slug") ?: return null
        val tag = if (kind == Kind.MOVIE) "movie" else "series"
        return TrackedTitle(
            kind, "$tracker:$tag:" + (tmdb?.let { "tmdb$it" } ?: "id$own"), name, media.jLong("year")?.toInt(), tmdb,
            media.jStr("certification"), media.jArr("genres").mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.let(::genre) }.take(3),
            at,
        )
    }

    /** Trakt `/sync/watchlist`: movies and shows (seasons and episodes count as their show). */
    fun traktWatchlist(body: String): List<TrackedTitle> =
        (obj(body) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e -> traktEntry(e, "listed_at") }.distinctBy { it.key }

    /** Trakt `/sync/history`: newest first, one per title. */
    fun traktHistory(body: String): List<TrackedTitle> =
        (obj(body) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e -> traktEntry(e, "watched_at") }
            .sortedByDescending { it.at ?: 0 }.distinctBy { it.key }

    private fun traktEntry(e: JsonElement, dateKey: String): TrackedTitle? {
        val at = instant(e.jStr(dateKey))
        return when (e.jStr("type")) {
            "movie" -> title(Kind.MOVIE, "trakt", e.jObj("movie"), at)
            "show", "season", "episode" -> title(Kind.SERIES, "trakt", e.jObj("show"), at)
            else -> null
        }
    }

    /** Simkl `/sync/all-items`: "plantowatch" is the watchlist; anything watched after [sinceMs] is history. */
    fun simklLibrary(body: String, sinceMs: Long): Library {
        val root = obj(body) as? JsonObject ?: return Library(emptyList(), emptyList())
        val list = ArrayList<TrackedTitle>()
        val watched = ArrayList<TrackedTitle>()
        for ((group, field, kind) in listOf(Triple("movies", "movie", Kind.MOVIE), Triple("shows", "show", Kind.SERIES), Triple("anime", "show", Kind.SERIES))) {
            root.jArr(group).forEach { e ->
                val status = e.jStr("status")
                val last = instant(e.jStr("last_watched_at"))
                if (status == "plantowatch") {
                    title(kind, "simkl", e.jObj(field), instant(e.jStr("added_to_watchlist_at")))?.let { list += it }
                } else if (last != null && last > sinceMs && (status == "completed" || status == "watching")) {
                    title(kind, "simkl", e.jObj(field), last)?.let { watched += it }
                }
            }
        }
        return Library(list.distinctBy { it.key }, watched.sortedByDescending { it.at ?: 0 }.distinctBy { it.key })
    }

    /** The body that marks movies watched (both trackers read this shape). */
    fun historyBody(movies: List<Pair<Int, Long>>): String =
        """{"movies":[""" + movies.joinToString(",") { (id, at) -> """{"watched_at":"${iso(at)}","ids":{"tmdb":$id}}""" } + "]}"

    /** How a tracked title sits in My List: TMDB titles open where they stream, others open search. */
    fun entry(providerId: String, t: TrackedTitle): MyListEntry {
        val open = t.tmdbId?.let { OpenTarget.Lookup(it, t.kind, t.title, "") }
            ?: OpenTarget.Internal("search?q=" + URLEncoder.encode(t.title, "UTF-8"))
        return MyListEntry(t.key, providerId, t.title, t.kind, null, open, t.at ?: 0, t.year?.toString(), t.genres, t.rating)
    }
}
