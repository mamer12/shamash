package io.shamash.domain

/** How a user feed is read. */
enum class FeedKind { RSS, ATOM, JSON }

/** The card shape of a user feed's row. */
enum class FeedStyle { WIDE, VIDEO, POSTER }

/**
 * A user's feed as other screens edit it (the phone page, CMP-10): the fields a person types, never a source's own
 * extras (a JSON mapping, a login header), which stay with the source. SPEC: CMP-10, PEX-20
 */
data class FeedEntry(
    val id: String,
    val title: String,
    val url: String,
    val kind: FeedKind = FeedKind.RSS,
    val style: FeedStyle = FeedStyle.WIDE,
    val level: ContentLevel = ContentLevel.ALL,
    val allowHttp: Boolean = false,
) {
    override fun toString() = "FeedEntry(id=$id)"     // an address can carry a login
}

/**
 * Optional interface for a provider that keeps the active profile's feeds (My feed), so the core can edit them
 * without knowing the add-on (PEX-31). Saving keeps each existing feed's own extras when its address is unchanged.
 */
interface FeedEditor {
    val maxFeeds: Int
    suspend fun feedEntries(): List<FeedEntry>
    suspend fun saveEntries(entries: List<FeedEntry>)

    /** The titles of the first [limit] cards; throws when the feed can't be read. */
    suspend fun tryEntry(entry: FeedEntry, limit: Int): List<String>
}
