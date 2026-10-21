package io.shamash.data.podcasts

import io.shamash.data.SafeXml
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One episode with something to play. */
data class Episode(
    val title: String,
    val audioUrl: String,
    val mime: String,
    val image: String?,
    val dateMs: Long?,
)

/** A show: its title, art and newest episodes (feed order). */
data class Podcast(val title: String, val image: String?, val episodes: List<Episode>)

/**
 * Reads a podcast RSS feed: the channel's title and art, and up to [MAX_EPISODES] items that carry an audio
 * `<enclosure>`. Text is plain, addresses must be http(s), DTDs are refused (SafeXml). SPEC: PEX-23, PEX-22
 */
object PodcastFeed {
    const val MAX_EPISODES = 20
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private val TAGS = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")

    fun parse(input: InputStream): Podcast {
        val handler = Handler()
        SafeXml.parse(input, handler)
        return Podcast(plain(handler.showTitle), handler.showImage, handler.episodes.toList())
    }

    fun plain(text: String?, max: Int = 160): String = text.orEmpty().replace(TAGS, " ").replace(SPACE, " ").trim().take(max)

    fun safeUrl(raw: String?): String? = raw?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }

    private val DATES = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z", "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX")

    fun date(text: String?): Long? {
        val t = text?.trim()?.replace(" GMT", " +0000")?.replace(" UTC", " +0000") ?: return null
        for (p in DATES) {
            val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
            runCatching { return f.parse(t)?.time }
        }
        return null
    }

    private class Handler : DefaultHandler() {
        val episodes = ArrayList<Episode>()
        var showTitle = ""
        var showImage: String? = null
        private var inItem = false
        private var inImage = false
        private var title = ""
        private var audio: String? = null
        private var mime = ""
        private var art: String? = null
        private var published = ""
        private var text: StringBuilder? = null

        override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
            val name = l.orEmpty()
            when {
                name == "item" -> if (episodes.size < MAX_EPISODES) {
                    inItem = true; title = ""; audio = null; mime = ""; art = null; published = ""
                }
                name == "image" && u == ITUNES -> {
                    val href = safeUrl(a?.getValue("href"))
                    if (inItem) art = art ?: href else showImage = href ?: showImage
                }
                name == "image" && !inItem -> inImage = true
                name == "enclosure" && inItem && audio == null -> {
                    val type = a?.getValue("type").orEmpty()
                    val url = safeUrl(a?.getValue("url"))
                    if (url != null && (type.startsWith("audio") || type.isEmpty())) { audio = url; mime = type.ifEmpty { "audio/*" } }
                }
                (name == "title" || name == "pubDate" || (name == "url" && inImage)) && u != ITUNES -> text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray, s: Int, len: Int) {
            text?.let { if (it.length < 2000) it.append(ch, s, len) }
        }

        override fun endElement(u: String?, l: String?, q: String?) {
            val name = l.orEmpty()
            val value = text?.toString()
            if (name == "title" || name == "pubDate" || name == "url") text = null
            when {
                name == "item" && inItem -> { finish(); inItem = false }
                name == "image" && u != ITUNES -> inImage = false
                name == "url" && inImage && value != null -> if (showImage == null) showImage = safeUrl(value)
                name == "title" && value != null && u != ITUNES -> if (inItem) { if (title.isEmpty()) title = value } else if (showTitle.isEmpty() && !inImage) showTitle = value
                name == "pubDate" && value != null && inItem -> published = value
            }
        }

        private fun finish() {
            val url = audio ?: return
            val t = plain(title)
            if (t.isEmpty()) return
            episodes += Episode(t, url, mime, art, date(published))
        }
    }
}

/** One show the profile follows. */
@kotlinx.serialization.Serializable
data class Subscription(val url: String, val title: String = "") {
    override fun toString() = "Subscription(host=${io.shamash.data.Redact.host(url)})"
}

/** OPML (a podcast app's export): every outline with an `xmlUrl`, at most [max]. SPEC: PEX-23 */
object Opml {
    fun parse(input: InputStream, max: Int = PodcastStore.MAX): List<Subscription> {
        val found = LinkedHashMap<String, Subscription>()
        SafeXml.parse(input, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
                if (l != "outline" || found.size >= max) return
                val url = PodcastFeed.safeUrl(a?.getValue("xmlUrl")) ?: return
                val title = PodcastFeed.plain(a?.getValue("title") ?: a?.getValue("text"))
                found.putIfAbsent(url, Subscription(url, title))
            }
        })
        return found.values.toList()
    }
}
