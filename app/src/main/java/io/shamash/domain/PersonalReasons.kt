package io.shamash.domain

/*
 * One honest line under a recommended row, and the signals behind it. A reason exists only when the row says why or
 * the profile's own history does; nothing else is made up (no "% match"). Public surface: ReasonKind, Reason, Reasons.
 * SPEC: PER-11, PER-12
 */
enum class ReasonKind { BECAUSE_WATCHED, BECAUSE_LOVED, BECAUSE_LIKED, BECAUSE_GENRE, POPULAR_WITH_SERVICE, FROM_CHANNEL }

data class Reason(val kind: ReasonKind, val arg: String? = null)

object Reasons {
    const val MAX_CHARS = 60

    /** [shelf]'s own reason, else the one its kind of row implies; null for lists and feeds. SPEC: PER-11 */
    fun forShelf(shelf: Shelf, topGenres: List<String>): Reason? {
        shelf.reason?.let { return it }
        val arg = shelf.titleArg?.takeIf { it.isNotBlank() }
        return when (shelf.titleKey) {
            "cinemana_because_watched" -> arg?.let { Reason(ReasonKind.BECAUSE_WATCHED, it) }
            "cinemana_because_loved" -> arg?.let { Reason(ReasonKind.BECAUSE_LOVED, it) }
            "cinemana_because_liked" -> arg?.let { Reason(ReasonKind.BECAUSE_LIKED, it) }
            "cinemana_picks" -> topGenres.firstOrNull()?.let { Reason(ReasonKind.BECAUSE_GENRE, it.replaceFirstChar(Char::titlecase)) }
            "popular_on" -> arg?.let { Reason(ReasonKind.POPULAR_WITH_SERVICE, it) }
            else -> null
        }
    }

    fun clip(text: String): String = if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS - 1).trimEnd() + "…"

    /** Up to three signals that explain why [item] is shown: same title, else same genres, newest first. SPEC: PER-12 */
    fun evidence(item: MediaItem, signals: List<Signal>): List<Signal> {
        val own = ItemKeys.of(item)
        val genres = item.genres.map { it.lowercase() }.toSet()
        val useful = signals.filter {
            it.kind in setOf(SignalKind.FINISH, SignalKind.OPEN, SignalKind.LIKE) && it.itemKey != own && it.title.isNotBlank()
        }
        val related = useful.filter { s -> s.genres.any { it.lowercase() in genres } }
        return related.asReversed().distinctBy { it.itemKey }.take(3)
    }
}
