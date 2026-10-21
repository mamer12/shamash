package io.shamash.data.myfeed

import io.shamash.data.json
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.ContentLevel
import io.shamash.domain.FeedEntry
import io.shamash.domain.FeedKind
import io.shamash.domain.FeedStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Where a JSON feed keeps what a card needs: JSON pointers, [items] from the root and the rest from each item. */
@Serializable
data class JsonMapping(
    val items: String = "",
    val title: String = "/title",
    val link: String = "/url",
    val image: String = "",
    val subtitle: String = "",
    val date: String = "",
)

/**
 * A feed the user added. [level] says who it suits (a KIDS profile sees only KIDS feeds); [allowHttp] is the per-feed
 * switch for plain http; [authHeader] ("Name: value") is kept with the feeds in the secret store. SPEC: PEX-20..22
 */
@Serializable
data class UserFeed(
    val id: String,
    val title: String,
    val url: String,
    val kind: FeedKind = FeedKind.RSS,
    val mapping: JsonMapping? = null,
    val style: FeedStyle = FeedStyle.WIDE,
    val level: ContentLevel = ContentLevel.ALL,
    val allowHttp: Boolean = false,
    val authHeader: String = "",
) {
    /** The header to send, or null when none or malformed. */
    fun header(): Pair<String, String>? {
        val name = authHeader.substringBefore(':', "").trim()
        val value = authHeader.substringAfter(':', "").trim()
        return if (name.isNotEmpty() && value.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' }) name to value else null
    }

    override fun toString() = "UserFeed(id=$id, host=${io.shamash.data.Redact.host(url)})"

    /** What the phone page sees and edits (CMP-10). */
    fun entry() = FeedEntry(id, title, url, kind, style, level, allowHttp)

    /** [e] as a feed; the mapping and login header of [old] are kept only while the address and kind are the same. */
    fun merged(e: FeedEntry): UserFeed {
        val same = e.url == url && e.kind == kind
        return UserFeed(e.id, e.title, e.url, e.kind, if (same) mapping else null, e.style, e.level, e.allowHttp, if (same) authHeader else "")
    }

    companion object {
        fun of(e: FeedEntry) = UserFeed(e.id, e.title, e.url, e.kind, null, e.style, e.level, e.allowHttp)
    }
}

/** The profile's feeds (at most [MAX]), on the TV only. SPEC: PEX-20 */
class FeedStore(private val store: SecretStore, private val profileId: suspend () -> String) {
    private val serializer = ListSerializer(UserFeed.serializer())

    private suspend fun key() = "myfeed_${profileId()}"

    suspend fun list(): List<UserFeed> = runCatching {
        json.decodeFromString(serializer, store.secret(key()).orEmpty().ifEmpty { "[]" })
    }.getOrDefault(emptyList())

    suspend fun save(feeds: List<UserFeed>): List<UserFeed> {
        val kept = feeds.distinctBy { it.id }.take(MAX)
        store.setSecret(key(), if (kept.isEmpty()) null else json.encodeToString(serializer, kept))
        return kept
    }

    suspend fun clear() = store.setSecret(key(), null)

    companion object {
        const val MAX = 10
    }
}
