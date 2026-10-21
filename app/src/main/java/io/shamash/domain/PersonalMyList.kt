package io.shamash.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/*
 * One My List across providers, per profile, pure. Entries dedupe by the mute key (provider id + id, else title + year
 * + kind), newest first, at most 40 on Home. An add-on that keeps its own list mirrors it through [PersonalBridge];
 * [MyListSync] reconciles the two sides without losing either. Public surface: MyListEntry, MyListOps, PersonalBridge,
 * MyListSync. SPEC: PER-13..15
 */
@Serializable
data class MyListEntry(
    val itemKey: String,
    val providerId: String,
    val title: String,
    val kind: Kind,
    val artUrl: String?,
    val open: OpenTarget,
    val addedAt: Long,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val rating: String? = null,
) {
    fun toItem() = MediaItem(
        id = itemKey, providerId = providerId, title = title, kind = kind, open = open,
        subtitle = year.orEmpty(), posterUrl = artUrl, backdropUrl = null, rating = rating, genres = genres,
    )

    companion object {
        fun of(item: MediaItem, at: Long) = MyListEntry(
            ItemKeys.of(item), item.providerId, item.title, item.kind, item.posterUrl ?: item.backdropUrl, item.open, at,
            Mutes.yearOf(item), item.genres, item.rating,
        )
    }
}

/** A provider's own list and signals, as the core sees them (all optional). */
interface PersonalBridge {
    val providerId: String

    /** The add-on's own My List now (loaded first, never an empty placeholder). */
    val listEntries: Flow<List<MyListEntry>>

    /** A title the add-on finished (progress at its threshold). */
    val finished: Flow<MediaItem>

    suspend fun addToList(item: MediaItem, profile: Profile)
    suspend fun removeFromList(itemKey: String)

    /** Clears the add-on's own ratings and watch history (Settings -> clear my history). SPEC: PER-04 */
    suspend fun clearHistory()
}

object MyListOps {
    const val HOME_MAX = 40
    const val MAX = 500

    private fun same(a: MyListEntry, b: MyListEntry) = a.itemKey == b.itemKey ||
        ((a.kind == Kind.MOVIE || a.kind == Kind.SERIES) && a.kind == b.kind && a.year == b.year &&
            Mutes.normalize(a.title).isNotEmpty() && Mutes.normalize(a.title) == Mutes.normalize(b.title))

    fun contains(list: List<MyListEntry>, item: MediaItem): Boolean = find(list, item) != null

    fun find(list: List<MyListEntry>, item: MediaItem): MyListEntry? {
        val probe = MyListEntry.of(item, 0)
        return list.firstOrNull { same(it, probe) }
    }

    fun add(list: List<MyListEntry>, entry: MyListEntry): List<MyListEntry> =
        (listOf(entry) + list.filterNot { same(it, entry) }).take(MAX)

    fun remove(list: List<MyListEntry>, itemKey: String): List<MyListEntry> = list.filterNot { it.itemKey == itemKey }

    /** Add when absent, remove when present. */
    fun toggle(list: List<MyListEntry>, item: MediaItem, at: Long): List<MyListEntry> =
        find(list, item)?.let { hit -> remove(list, hit.itemKey) } ?: add(list, MyListEntry.of(item, at))

    /** The Home row: newest first, at most 40, only titles whose provider is on now, muted ones left out. SPEC: PER-14, PER-15 */
    fun row(list: List<MyListEntry>, availableProviders: Set<String>, mutes: MuteIndex): List<MediaItem> =
        list.sortedByDescending { it.addedAt }.filter { it.providerId in availableProviders || availableProviders.isEmpty() }
            .map { it.toItem() }.filterNot(mutes::isMuted).take(HOME_MAX)

    /** Entries whose provider is gone: kept, shown as unavailable in Settings with Remove. SPEC: PER-15 */
    fun unavailable(list: List<MyListEntry>, availableProviders: Set<String>): List<MyListEntry> =
        list.filter { it.providerId !in availableProviders }
}

/** What one reconcile decided for one add-on. */
data class SyncPlan(
    val list: List<MyListEntry>,
    val seen: List<String>,
    val addToMirror: List<MyListEntry>,
    val removeFromMirror: List<String>,
)

object MyListSync {
    /**
     * [core] is the profile's list, [mirror] the add-on's list now, [seen] the add-on's keys after the last sync
     * (empty before the first one: then everything on both sides is kept and merged). A key new in the add-on is added
     * to the core; one gone from it is removed from the core; a core entry of this add-on that the add-on never had
     * is added to it; one gone from the core is removed from the add-on. Nothing is deleted on a first sync.
     */
    fun reconcile(providerId: String, core: List<MyListEntry>, mirror: List<MyListEntry>, seen: Set<String>, first: Boolean): SyncPlan {
        val mirrorKeys = mirror.map { it.itemKey }.toSet()
        val coreOwn = core.filter { it.providerId == providerId }
        val coreKeys = coreOwn.map { it.itemKey }.toSet()
        val addedInMirror = mirror.filter { it.itemKey !in seen && it.itemKey !in coreKeys }
        val removedInMirror = if (first) emptySet() else seen - mirrorKeys
        val removedInCore = if (first) emptySet() else (seen - coreKeys).filter { it in mirrorKeys }.toSet()
        val addToMirror = coreOwn.filter { it.itemKey !in mirrorKeys && it.itemKey !in seen }
        var list = core
        removedInMirror.forEach { key -> list = MyListOps.remove(list, key) }
        addedInMirror.forEach { e -> list = MyListOps.add(list, e) }
        val after = (mirrorKeys + addToMirror.map { it.itemKey }) - removedInCore
        return SyncPlan(list, after.toList(), addToMirror, removedInCore.toList())
    }
}
