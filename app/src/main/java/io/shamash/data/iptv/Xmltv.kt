package io.shamash.data.iptv

import io.shamash.data.SafeXml
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone

/** A programme on the guide. */
data class Programme(val title: String, val start: Long, val stop: Long)

/** What is on now and next for one channel. */
data class NowNext(val now: Programme?, val next: Programme?)

/**
 * XMLTV read as a stream. Only the channels asked for are kept, and for each only the programme on now and the one
 * after: memory stays tiny however large the guide. Bad XML, a DTD or a truncated file gives nothing, silently.
 * SPEC: PEX-15, PEX-22
 */
object Xmltv {
    /** [wanted]: the guide's channel id or display name (lower case) -> our channel id. */
    fun nowNext(input: InputStream, wanted: Map<String, String>, at: Long): Map<String, NowNext> {
        val handler = Handler(wanted, at)
        return try {
            SafeXml.parse(input, handler)
            handler.result()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** `20260101120000 +0300` (offset and seconds optional) to epoch ms; null when it isn't a time. */
    fun time(text: String?): Long? {
        val t = text?.trim() ?: return null
        val digits = t.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        fun n(from: Int, to: Int) = digits.substring(from, to).toInt()
        val offset = t.drop(digits.length).trim().let { o ->
            if (o.length >= 5 && (o[0] == '+' || o[0] == '-') && o.substring(1, 5).all { it.isDigit() }) {
                (o.substring(1, 3).toInt() * 60 + o.substring(3, 5).toInt()) * 60_000L * (if (o[0] == '-') -1 else 1)
            } else 0L
        }
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(n(0, 4), n(4, 6) - 1, n(6, 8), n(8, 10), n(10, 12), if (digits.length >= 14) n(12, 14) else 0)
        return c.timeInMillis - offset
    }

    private class Handler(private val wanted: Map<String, String>, private val at: Long) : DefaultHandler() {
        private val xmlToOurs = HashMap<String, String>()
        private val now = HashMap<String, Programme>()
        private val next = HashMap<String, Programme>()
        private var channelId: String? = null
        private val names = ArrayList<String>()
        private var programme: Triple<String, Long, Long>? = null   // channel, start, stop
        private var text: StringBuilder? = null
        private var title: String? = null

        override fun startElement(u: String?, local: String?, q: String?, a: Attributes?) {
            when (local) {
                "channel" -> { channelId = a?.getValue("id"); names.clear() }
                "programme" -> {
                    val start = time(a?.getValue("start"))
                    val stop = time(a?.getValue("stop"))
                    val channel = a?.getValue("channel")?.let { ours(it) }
                    programme = if (start != null && stop != null && channel != null && stop > at) Triple(channel, start, stop) else null
                    title = null
                }
                "display-name" -> if (channelId != null) text = StringBuilder()
                "title" -> if (programme != null && title == null) text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray, s: Int, len: Int) {
            text?.let { if (it.length < 200) it.append(ch, s, len) }
        }

        override fun endElement(u: String?, local: String?, q: String?) {
            when (local) {
                "display-name" -> text?.let { names += it.toString().trim(); text = null }
                "channel" -> {
                    val id = channelId
                    if (id != null) (wanted[id.lowercase()] ?: names.firstNotNullOfOrNull { wanted[it.lowercase()] })
                        ?.let { xmlToOurs[id] = it }
                    channelId = null
                }
                "title" -> text?.let { title = it.toString().trim(); text = null }
                "programme" -> { keep(); programme = null }
            }
        }

        private fun ours(xmlId: String) = xmlToOurs[xmlId] ?: wanted[xmlId.lowercase()]

        private fun keep() {
            val (channel, start, stop) = programme ?: return
            val name = title?.takeIf { it.isNotEmpty() } ?: return
            val p = Programme(name, start, stop)
            if (start <= at) {
                if (now[channel]?.let { it.start < start } != false) now[channel] = p
            } else if (next[channel]?.let { start < it.start } != false) {
                next[channel] = p
            }
        }

        fun result(): Map<String, NowNext> =
            (now.keys + next.keys).associateWith { NowNext(now[it], next[it]) }
    }
}
