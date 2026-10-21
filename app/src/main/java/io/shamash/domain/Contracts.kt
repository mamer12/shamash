package io.shamash.domain

import kotlinx.coroutines.flow.Flow

/** A content source. Implementations fail soft: errors give empty lists. SPEC: ARCH-05, Provider contract */
interface Provider {
    val id: String
    fun isEnabled(profile: Profile): Boolean
    suspend fun shelves(profile: Profile): List<Shelf>
    suspend fun featured(profile: Profile): List<MediaItem>
    suspend fun search(query: String, profile: Profile): List<MediaItem>
}

/** Matches of the profile's competitions, today + 6 days. SPEC: MC-01, PRV-11 */
interface MatchSource {
    suspend fun matches(profile: Profile): List<Match>
}

interface WeatherSource {
    suspend fun current(profile: Profile): Weather?
}

/** Looks up a title's own page in its app (Apple TV, Netflix). SPEC: PRV-05 */
interface LinkResolver {
    suspend fun resolve(target: OpenTarget.Lookup, region: String): String?
}

/** What was opened from Shamash, newest first. SPEC: HOME-11 */
interface WatchHistory {
    val items: Flow<List<MediaItem>>
    suspend fun record(item: MediaItem)
}

/** The TV's apps. */
interface AppsSource {
    fun installed(): List<TvApp>
    fun recent(limit: Int): List<TvApp>
}

/** A provider that can play a short preview of a title inside its tile. SPEC: HOME-20 */
interface PreviewSource {
    /** A playable URL for the item (and, for range-request streaming, headers), or null when there's none. */
    suspend fun preview(item: MediaItem): Preview?
}

data class Preview(val url: String, val headers: Map<String, String> = emptyMap(), val subtitlesUrl: String? = null)

/** Providers with local state invalidate their Home shelves after a mutation. SPEC: CIN-07, CIN-09 */
interface RefreshableProvider {
    val changes: Flow<Unit>
}
