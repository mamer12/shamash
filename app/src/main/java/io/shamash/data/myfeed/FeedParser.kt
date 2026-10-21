package io.shamash.data.myfeed

import io.shamash.data.SafeXml
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One entry of a feed, all plain text. */
data class FeedItem(val title: String, val link: String, val image: String?, val subtitle: String, val dateMs: Long?)

/**
 * Reads RSS, Atom and mapped JSON into [FeedItem]s. Text is plain (tags removed, never rendered), links and pictures
 * must be http or https, at most [MAX_ITEMS] entries. Anything unreadable gives fewer items, not a crash.
 * SPEC: PEX-21, PEX-22
 */
object FeedParser {
    const val MAX_ITEMS = 20
    private const val MRSS = "http://search.yahoo.com/mrss/"
    private val TAGS = Regex("<[^>]*>")
    private val IMG = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** RSS or Atom (decided by the root element). Throws on a DTD or on XML that isn't well formed. */
    fun xml(input: InputStream): List<FeedItem> {
        val handler = XmlHandler()
        SafeXml.parse(input, handler)
        return handler.items.take(MAX_ITEMS)
    }

    fun plain(text: String?, max: Int = 200): String =
        text.orEmpty().replace(TAGS, " ").replace(Regex("\\s+"), " ").trim().take(max)

    internal fun safeUrl(raw: String?): String? {
        val u = raw?.trim().orEmpty()
        return u.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }

    private fun date(text: String?): Long? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd",
        )
        val cleaned = t.replace(Regex("\\.\\d+(?=Z|[+-])"), "").replace(" GMT", " +0000").replace(" UTC", " +0000")
        for (p in patterns) {
            val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
            runCatching { return f.parse(cleaned)?.time }
        }
        return null
    }

    internal fun subtitle(ms: Long?): String =
        ms?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()

    private class XmlHandler : DefaultHandler() {
        val items = ArrayList<FeedItem>()
        private var inItem = false
        private var title = ""
        private var link = ""
        private var image: String? = null
        private var published = ""
        private var body = ""
        private var text: StringBuilder? = null

        override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
            val name = l.orEmpty()
            if (!inItem) {
                if ((name == "item" || name == "entry") && items.size < MAX_ITEMS) {
                    inItem = true; title = ""; link = ""; image = null; published = ""; body = ""
                }
                return
            }
            when {
                name == "link" && a?.getValue("href") != null -> {
                    val rel = a.getValue("rel")
                    if (rel == null || rel == "alternate") link = a.getValue("href").orEmpty()
                }
                name == "enclosure" && image == null && a?.getValue("type").orEmpty().startsWith("image") -> image = safeUrl(a?.getValue("url"))
                u == MRSS && (name == "thumbnail" || (name == "content" && a?.getValue("medium") == "image")) && image == null ->
                    image = safeUrl(a?.getValue("url"))
                name in TEXT_FIELDS && u != MRSS -> text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray, s: Int, len: Int) {
            text?.let { if (it.length < 4000) it.append(ch, s, len) }
        }

        override fun endElement(u: String?, l: String?, q: String?) {
            val name = l.orEmpty()
            if (!inItem) return
            val value = text?.toString()
            text = null
            when {
                name == "title" && value != null && title.isEmpty() -> title = value
                name == "link" && value != null && link.isEmpty() -> link = value.trim()
                (name == "pubDate" || name == "published" || name == "updated" || name == "date") && value != null && published.isEmpty() -> published = value
                (name == "description" || name == "summary" || name == "content" || name == "encoded") && value != null && body.isEmpty() -> body = value
                name == "item" || name == "entry" -> { finish(); inItem = false }
            }
        }

        private fun finish() {
            val t = plain(title)
            val href = safeUrl(link)
            if (t.isEmpty() || href == null) return
            val art = image ?: IMG.find(body)?.groupValues?.get(1)?.let { safeUrl(it) }
            val ms = date(published)
            items += FeedItem(t, href, art, subtitle(ms), ms)
        }

        private companion object {
            val TEXT_FIELDS = setOf("title", "link", "pubDate", "published", "updated", "date", "description", "summary", "content", "encoded")
        }
    }

    /** JSON by pointers: [JsonMapping.items] locates the list, the others are read from each entry. */
    fun json(root: JsonElement, m: JsonMapping): List<FeedItem> {
        val list = (pointer(root, m.items) as? JsonArray) ?: return emptyList()
        return list.asSequence().mapNotNull { e ->
            val title = plain(text(pointer(e, m.title)))
            val link = safeUrl(text(pointer(e, m.link))) ?: return@mapNotNull null
            if (title.isEmpty()) return@mapNotNull null
            val ms = if (m.date.isBlank()) null else text(pointer(e, m.date))?.let { raw -> raw.toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000 else it } ?: date(raw) }
            val sub = if (m.subtitle.isBlank()) subtitle(ms) else plain(text(pointer(e, m.subtitle)), 120)
            FeedItem(title, link, if (m.image.isBlank()) null else safeUrl(text(pointer(e, m.image))), sub, ms)
        }.take(MAX_ITEMS).toList()
    }

    private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    /** A JSON pointer ("/data/items/0/title"); "" is the whole document. Null when it leads nowhere. */
    fun pointer(root: JsonElement, path: String): JsonElement? {
        if (path.isEmpty() || path == "/") return root
        var current: JsonElement = root
        for (raw in path.trimStart('/').split('/')) {
            val key = raw.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[key] ?: return null
                is JsonArray -> key.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current
    }
}
