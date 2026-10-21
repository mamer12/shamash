package io.shamash.data.cinemana

import io.shamash.domain.MediaItem
import io.shamash.domain.MyListEntry
import io.shamash.domain.PersonalBridge
import io.shamash.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Cinemana's list, finished titles and history as the core's One My List sees them. The add-on's own list stays as it
 * is and keeps working alone; this only reads it and applies the core's adds and removes. SPEC: PER-13
 */
class CinemanaBridge(private val library: CinemaLibrary) : PersonalBridge {
    override val providerId = CinemanaProvider.ID

    /** The saved list, read before the first answer so a not-yet-loaded store never looks empty. */
    override val listEntries: Flow<List<MyListEntry>> = flow {
        library.store.snapshot()
        library.store.state.map { saved -> saved.myList.mapIndexed { i, t -> MyListEntry.of(library.item(t), -i.toLong()) } }
            .distinctUntilChanged().collect { emit(it) }
    }

    override val finished: Flow<MediaItem> = library.store.finished.map { library.item(it) }

    override suspend fun addToList(item: MediaItem, profile: Profile) {
        val id = item.id.substringAfter(':')
        if (library.store.snapshot().myList.any { it.pageId == id }) return
        library.detail(id, profile)?.title?.let { library.store.toggleList(it) }
    }

    override suspend fun removeFromList(itemKey: String) {
        val id = itemKey.substringAfter(':')
        library.store.snapshot().myList.firstOrNull { it.pageId == id }?.let { library.store.toggleList(it) }
    }

    override suspend fun clearHistory() = library.store.clearPersonal()
}
