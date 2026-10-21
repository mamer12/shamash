package io.shamash.data.widgets

import io.shamash.data.SafeXml
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

/** One headline: plain text and an http(s) link. */
data class Headline(val title: String, val link: String, val source: String)

/**
 * Headlines only from an RSS or Atom news feed (the same safety as My feed: no DTDs, plain text, https links), at most
 * [max] per feed. SPEC: PEX-27, PEX-22
 */
object Headlines {
    private val TAGS = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")

    fun parse(input: InputStream, max: Int = 3): List<Headline> {
        val h = Handler(max)
        SafeXml.parse(input, h)
        val source = plain(h.feedTitle, 40)
        return h.items.map { it.copy(source = source) }
    }

    fun plain(text: String?, max: Int = 140) = text.orEmpty().replace(TAGS, " ").replace(SPACE, " ").trim().take(max)

    private fun safe(raw: String?) = raw?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }

    private class Handler(private val max: Int) : DefaultHandler() {
        val items = ArrayList<Headline>()
        var feedTitle = ""
        private var inItem = false
        private var title = ""
        private var link = ""
        private var text: StringBuilder? = null

        override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
            when (l) {
                "item", "entry" -> if (items.size < max) { inItem = true; title = ""; link = "" }
                "link" -> {
                    val href = a?.getValue("href")
                    val rel = a?.getValue("rel")
                    if (inItem && href != null && (rel == null || rel == "alternate")) link = href else text = StringBuilder()
                }
                "title" -> text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray, s: Int, len: Int) {
            text?.let { if (it.length < 1000) it.append(ch, s, len) }
        }

        override fun endElement(u: String?, l: String?, q: String?) {
            val value = text?.toString()
            if (l == "title" || l == "link") text = null
            when (l) {
                "title" -> if (inItem) { if (title.isEmpty()) title = value.orEmpty() } else if (feedTitle.isEmpty()) feedTitle = value.orEmpty()
                "link" -> if (inItem && link.isEmpty() && value != null) link = value.trim()
                "item", "entry" -> if (inItem) {
                    inItem = false
                    val t = plain(title)
                    val href = safe(link)
                    if (t.isNotEmpty() && href != null) items += Headline(t, href, "")
                }
            }
        }
    }
}
