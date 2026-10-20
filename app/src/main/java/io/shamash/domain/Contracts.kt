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
