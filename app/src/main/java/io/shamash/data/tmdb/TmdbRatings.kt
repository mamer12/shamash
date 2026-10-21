package io.shamash.data.tmdb

import io.shamash.data.softly
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Fills [MediaItem.rating] from TMDB's certifications, only for profiles that restrict content (ALL never asks).
 * A title TMDB doesn't rate keeps a null rating and is hidden by the gate; a failed lookup is not remembered, so
 * it is asked again next time. SPEC: PRF-12
 */
class TmdbRatings(private val client: TmdbClient) {
    private val known = ConcurrentHashMap<String, String>()   // "" = TMDB has no rating for it
    private val limit = Semaphore(PARALLEL)

    suspend fun rate(items: List<MediaItem>, profile: Profile): List<MediaItem> {
        if (profile.contentLevel == ContentLevel.ALL || items.isEmpty()) return items
        val region = profile.region.uppercase()
        return coroutineScope {
            items.map { item -> async { item.copy(rating = item.rating ?: lookup(item, profile.tmdbKey, region)) } }.awaitAll()
        }
    }

    private suspend fun lookup(item: MediaItem, key: String, region: String): String? {
        val parts = item.id.split(':')
        if (parts.size != 3 || parts[0] != TmdbCatalog.PROVIDER_ID) return null
        val kind = if (parts[1] == "series") Kind.SERIES else Kind.MOVIE
        val id = parts[2].toIntOrNull() ?: return null
        val cacheKey = "$region:${parts[1]}:$id"
        known[cacheKey]?.let { return it.ifEmpty { null } }
        val answer = limit.withPermit {
            softly<String?>("TMDB rating $id", null) {
                client.certification(key, TmdbTitle(id, kind, item.title), region).orEmpty()
            }
        } ?: return null
        known[cacheKey] = answer
        return answer.ifEmpty { null }
    }

    private companion object {
        const val PARALLEL = 6
    }
}
