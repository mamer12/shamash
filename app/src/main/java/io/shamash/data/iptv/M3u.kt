package io.shamash.data.iptv

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** One live channel from a playlist. [id] is the `tvg-id` when there is one, else the name. */
data class Channel(val id: String, val name: String, val group: String, val logo: String?, val url: String, val tvgId: String?)

data class Playlist(val channels: List<Channel>, val groups: List<String>)

/**
 * A streaming M3U reader: line by line, so a playlist of any size is never held whole; at most [MAX] channels are
 * kept and the rest of the stream is not read. Attributes `tvg-id`, `tvg-logo`, `group-title`; a missing one is fine,
 * a BOM is ignored, and a line that isn't understood is skipped. SPEC: PEX-14
 */
object M3u {
    const val MAX = 5000
    private val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(input: InputStream, max: Int = MAX): Playlist {
        val channels = ArrayList<Channel>()
        val seen = HashSet<String>()
        var pending: Channel? = null
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var first = true
            while (channels.size < max) {
                var line = reader.readLine() ?: break
                if (first) { line = line.removePrefix("﻿"); first = false }
                line = line.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> pending = extinf(line)
                    line.startsWith("#") -> Unit
                    "://" in line -> {
                        val p = pending
                        pending = null
                        if (p != null && seen.add(p.id)) channels += p.copy(url = line)
                    }
                }
            }
        }
        return Playlist(channels, channels.map { it.group }.filter { it.isNotEmpty() }.distinct())
    }

    private fun extinf(line: String): Channel? {
        val attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val afterAttrs = line.lastIndexOf('"').coerceAtLeast(0)
        val comma = line.indexOf(',', afterAttrs)
        val name = (if (comma >= 0) line.substring(comma + 1).trim() else "").ifEmpty { attrs["tvg-name"].orEmpty() }
        if (name.isEmpty()) return null
        val tvg = attrs["tvg-id"]?.takeIf { it.isNotEmpty() }
        return Channel(
            id = tvg ?: name, name = name, group = attrs["group-title"].orEmpty(),
            logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http", ignoreCase = true) }, url = "", tvgId = tvg,
        )
    }
}
