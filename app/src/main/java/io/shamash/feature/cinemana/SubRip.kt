package io.shamash.feature.cinemana

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Cues are parsed once; lookup never creates per-frame collections. SPEC: CIN-04, CIN-06 */
class SubRip private constructor(private val cues: List<Cue>) {
    data class Cue(val startMs: Long, val endMs: Long, val text: String)
    private val latestEnd = LongArray(cues.size).also { ends ->
        cues.indices.forEach { ends[it] = maxOf(cues[it].endMs, ends.getOrElse(it - 1) { 0L }) }
    }

    fun at(ms: Long): String? {
        var low = 0
        var high = cues.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (cues[mid].startMs <= ms) low = mid + 1 else high = mid - 1
        }
        while (high >= 0 && latestEnd[high] > ms) {
            if (cues[high].endMs > ms) return cues[high].text
            high--
        }
        return null
    }

    companion object {
        private val times = Regex("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{1,3})[^\\n]*")
        private val tags = Regex("<[^>]*>|\\{[^}]*}")
        private val blocks = Regex("\\n[ \\t]*\\n")
        fun parse(text: String): SubRip = SubRip(text.removePrefix("\uFEFF").replace("\r", "").split(blocks).mapNotNull { block ->
            val match = times.find(block) ?: return@mapNotNull null
            val start = time(match, 1) ?: return@mapNotNull null
            val end = time(match, 5) ?: return@mapNotNull null
            val body = block.substring(match.range.last + 1).replace(tags, "").trim()
            if (end <= start || body.isBlank()) null else Cue(start, end, body)
        }.sortedBy { it.startMs })

        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                    (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))) return bytes.toString(Charsets.UTF_16)
            return try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                bytes.toString(Charset.forName("windows-1256"))
            }
        }

        private fun time(match: MatchResult, start: Int): Long? {
            val hour = match.groupValues[start].toLongOrNull() ?: return null
            val minute = match.groupValues[start + 1].toLongOrNull() ?: return null
            val second = match.groupValues[start + 2].toLongOrNull() ?: return null
            if (minute > 59 || second > 59) return null
            val millis = match.groupValues[start + 3].padEnd(3, '0').toLong()
            return ((hour * 60 + minute) * 60 + second) * 1000 + millis
        }
    }
}
