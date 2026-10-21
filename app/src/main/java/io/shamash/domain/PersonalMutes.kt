package io.shamash.domain

import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * "Not for you": titles hidden everywhere for the profile. A title matches by provider id + id, else by normalized
 * title + year + kind (so a re-keyed title stays hidden). Public surface: Mute, MuteIndex, Mutes. SPEC: PER-07, PER-08
 */
@Serializable
data class Mute(
    val providerId: String,
    val id: String,
    val title: String,
    val kind: Kind,
    val year: String? = null,
    val at: Long = 0,
    val lessLikeThis: Boolean = false,
    val genres: List<String> = emptyList(),
)

object Mutes {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val YEAR = Regex("\\b(19|20)\\d{2}\\b")

    fun normalize(title: String): String = title.lowercase(Locale.ROOT).replace(NON_WORD, "")

    fun yearOf(item: MediaItem): String? = YEAR.find(item.subtitle)?.value

    fun of(item: MediaItem, at: Long, lessLikeThis: Boolean = false) = Mute(
        item.providerId, ItemKeys.of(item), item.title, item.kind, yearOf(item), at, lessLikeThis, item.genres.take(3),
    )

    /** Hides a title; one entry per title, the newest wins. */
    fun add(list: List<Mute>, mute: Mute): List<Mute> = list.filterNot { MuteIndex(listOf(it)).matches(mute) } + mute

    fun remove(list: List<Mute>, mute: Mute): List<Mute> = list.filterNot { it == mute }

    fun filter(items: List<MediaItem>, index: MuteIndex): List<MediaItem> =
        if (index.isEmpty) items else items.filterNot(index::isMuted)

    fun filter(shelf: Shelf, index: MuteIndex): Shelf =
        if (index.isEmpty) shelf else shelf.copy(items = filter(shelf.items, index))
}

/** The mutes of one profile, ready to test many titles quickly. */
class MuteIndex(mutes: List<Mute>) {
    private val keys = mutes.map { it.providerId to it.id }.toSet()
    private val fuzzy = mutes.filter { it.kind == Kind.MOVIE || it.kind == Kind.SERIES }
        .map { Triple(Mutes.normalize(it.title), it.year, it.kind) }.filter { it.first.isNotEmpty() }.toSet()
    val isEmpty = mutes.isEmpty()

    fun isMuted(item: MediaItem): Boolean {
        if (isEmpty) return false
        if ((item.providerId to ItemKeys.of(item)) in keys) return true
        if (item.kind != Kind.MOVIE && item.kind != Kind.SERIES) return false
        return Triple(Mutes.normalize(item.title), Mutes.yearOf(item), item.kind) in fuzzy
    }

    fun matches(mute: Mute): Boolean = isMuted(
        MediaItem(mute.id, mute.providerId, mute.title, mute.kind, OpenTarget.Internal("home"), subtitle = mute.year.orEmpty()),
    )
}
