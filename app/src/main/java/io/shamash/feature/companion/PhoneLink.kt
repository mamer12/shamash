package io.shamash.feature.companion

import java.net.URI
import java.util.Locale

/** What the phone sent, sorted by how the TV handles it. SPEC: CMP-04 */
sealed interface PhoneMessage {
    data object Empty : PhoneMessage
    data class YouTube(val videoId: String) : PhoneMessage
    data class Tod(val url: String) : PhoneMessage
    data class Link(val url: String) : PhoneMessage
    data class Words(val text: String) : PhoneMessage
}

/** Sorts a pasted or shared text: a YouTube video, a TOD link, another web link, or words to search for. */
object PhoneLink {
    private val youtube = Regex(
        """(?:youtube\.com/(?:watch\?(?:[^\s#]*&)?v=|shorts/|live/|embed/)|youtu\.be/)([\w-]{11})""",
        RegexOption.IGNORE_CASE,
    )
    private val webLink = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private const val TOD_HOST = "tod.tv"

    fun classify(text: String): PhoneMessage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return PhoneMessage.Empty
        youtube.find(trimmed)?.let { return PhoneMessage.YouTube(it.groupValues[1]) }
        val url = webLink.find(trimmed)?.value ?: return PhoneMessage.Words(trimmed)
        return if (isTod(url)) PhoneMessage.Tod(url) else PhoneMessage.Link(url)
    }

    private fun isTod(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return false
        return host == TOD_HOST || host.endsWith(".$TOD_HOST")
    }
}
