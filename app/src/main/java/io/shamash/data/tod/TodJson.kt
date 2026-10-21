package io.shamash.data.tod

import io.shamash.data.json
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.OffsetDateTime

/** One tile of a TOD page's featured list. */
data class TodTile(
    val title: String,
    val slug: String,
    val contentType: String,
    val startsAt: Long?,
    val competition: String,
    val image: TodImage,
)

/** A TOD picture, cut by its crop box and resized by TOD's image server. SPEC: PRV-09 */
data class TodImage(
    val id: String,
    val mediaSettingsId: String,
    val timeStamp: String,
    val x1: Int, val y1: Int, val x2: Int, val y2: Int,
    val fullName: String,
) {
    fun url(width: Int, height: Int) =
        "https://img.tod.tv/resources/images/link/$id/$mediaSettingsId/$timeStamp/$x1:$y1:$x2:$y2/${width}x$height/$fullName"
}

/**
 * Reads the featured list ("slider") TOD's website sends with a page when asked as a React Server Component (`RSC: 1`):
 * a JSON object inside the stream, found by how it starts. SPEC: PRV-09
 */
object TodJson {
    private const val SLIDER = "{\"status\":200,\"data\":{\"railType\":\"slider\""

    @Serializable private data class Slider(val data: SliderData)
    @Serializable private data class SliderData(val items: List<Item> = emptyList())
    @Serializable private data class Competition(val title: String = "")
    @Serializable private data class Images(val tile: Image? = null, val wallpaper: Image? = null)

    @Serializable
    private data class Item(
        val title: String = "",
        val urlSlug: String = "",
        val contentType: String = "",
        val matchStartTime: String? = null,
        val competition: Competition? = null,
        val image: Images? = null,
    )

    @Serializable
    private data class Image(
        val id: String,
        val mediaSettingsId: String,
        val timeStamp: Long,
        val x1: Int = 0, val y1: Int = 0, val x2: Int = 0, val y2: Int = 0,
        val fullName: String,
    )

    fun slider(page: String): List<TodTile> {
        val start = page.indexOf(SLIDER)
        if (start < 0) throw IOException("TOD page changed: no featured list")
        val items = json.decodeFromString<Slider>(objectAt(page, start)).data.items
        return items.mapNotNull(::tile)
    }

    private fun tile(item: Item): TodTile? {
        val picture = item.image?.tile ?: item.image?.wallpaper ?: return null
        if (item.title.isBlank() || item.urlSlug.isBlank()) return null
        val image = with(picture) { TodImage(id, mediaSettingsId, timeStamp.toString(), x1, y1, x2, y2, fullName) }
        return TodTile(
            title = item.title.trim(),
            slug = item.urlSlug,
            contentType = item.contentType,
            startsAt = item.matchStartTime?.let(::epochMs),
            competition = item.competition?.title?.trim().orEmpty(),
            image = image,
        )
    }

    private fun epochMs(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

    /** The JSON object starting at [start], up to its matching closing brace (braces inside strings don't count). */
    internal fun objectAt(text: String, start: Int): String {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        throw IOException("TOD page cut short")
    }
}
