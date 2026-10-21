package io.shamash.feature.companion

import io.shamash.data.Logger
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.URI
import java.util.UUID

/** A link or title sent with "Add to queue": opened later through the same CMP-04 rules as "Open now". */
@Serializable
data class QueueEntry(val id: String, val text: String, val title: String, val addedAt: Long)

/**
 * The queue's rules, pure: newest first, the same text once, at most [MAX] per profile; a card per entry whose OK
 * opens the queue page (open or dismiss). SPEC: CMP-11
 */
object QueueOps {
    const val MAX = 30
    const val MAX_TEXT = 2_000
    const val MAX_TITLE = 80
    const val PROVIDER = "queue"
    const val SCHEME = "shamash-queue:"

    fun entry(text: String, now: Long, id: String = UUID.randomUUID().toString()): QueueEntry? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_TEXT || !plain(t)) return null
        return QueueEntry(id, t, titleOf(t), now)
    }

    fun add(list: List<QueueEntry>, entry: QueueEntry): List<QueueEntry> =
        (listOf(entry) + list.filterNot { it.text == entry.text }).take(MAX)

    fun remove(list: List<QueueEntry>, id: String): List<QueueEntry> = list.filterNot { it.id == id }

    /** The words around a link ("Great match https://…"), else "YouTube" or the link's host, else the text. */
    fun titleOf(text: String): String {
        val link = WEB.find(text)?.value
        val words = (if (link != null) text.replace(link, " ") else text).trim()
        val host = link?.let { runCatching { URI(it).host }.getOrNull() }
        val title = when {
            words.isNotBlank() -> words
            PhoneLink.classify(text) is PhoneMessage.YouTube -> "YouTube"
            host != null -> host.removePrefix("www.")
            else -> text
        }
        return title.replace(Regex("\\s+"), " ").take(MAX_TITLE)
    }

    private val WEB = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    /** The Home card; its OK lands on the queue page for this entry. */
    fun card(entry: QueueEntry, ownPackage: String): MediaItem {
        val video = (PhoneLink.classify(entry.text) as? PhoneMessage.YouTube)?.videoId
        return MediaItem(
            id = "$PROVIDER:${entry.id}", providerId = PROVIDER, title = entry.title, kind = Kind.VIDEO,
            open = OpenTarget.AppPage(SCHEME + entry.id, ownPackage),
            backdropUrl = video?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
        )
    }

    fun idOf(uri: String): String? = uri.takeIf { it.startsWith(SCHEME) }?.removePrefix(SCHEME)?.takeIf { it.isNotBlank() }
}

/**
 * The queue on the TV, one small JSON file per profile, written whole to a temp file then renamed. A file that can't
 * be read is an empty queue. [changes] ticks after each write, so Home redraws its row. SPEC: CMP-11
 */
class QueueStore(private val dir: File) {
    private val serializer = ListSerializer(QueueEntry.serializer())
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    @Synchronized
    fun list(profileId: String): List<QueueEntry> = runCatching {
        val f = file(profileId)
        if (!f.exists()) emptyList() else PhoneJson.out.decodeFromString(serializer, f.readText())
    }.getOrElse { Logger.w("queue unreadable, starting empty", it); emptyList() }

    fun add(profileId: String, entry: QueueEntry): Boolean = edit(profileId) { QueueOps.add(it, entry) }

    fun remove(profileId: String, id: String): Boolean = edit(profileId) { QueueOps.remove(it, id) }

    private fun edit(profileId: String, change: (List<QueueEntry>) -> List<QueueEntry>): Boolean {
        val ok = synchronized(this) {
            runCatching {
                dir.mkdirs()
                val next = change(list(profileId))
                val tmp = File(dir, file(profileId).name + ".tmp")
                tmp.writeText(PhoneJson.out.encodeToString(serializer, next))
                if (!tmp.renameTo(file(profileId))) error("rename")
            }.onFailure { Logger.w("queue not saved", it) }.isSuccess
        }
        if (ok) _changes.tryEmit(Unit)
        return ok
    }

    companion object {
        @Volatile private var instance: QueueStore? = null

        /** The one store the TV's row and the phone server share (one per process). */
        fun get(context: android.content.Context): QueueStore = instance ?: synchronized(this) {
            instance ?: QueueStore(File(context.applicationContext.filesDir, "companion-queue")).also { instance = it }
        }
    }

    private fun file(profileId: String) = File(dir, "queue_" + profileId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "default" } + ".json")
}
