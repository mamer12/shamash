package io.shamash.feature.home

import androidx.compose.runtime.Immutable
import io.shamash.domain.Greeting
import io.shamash.domain.Match
import io.shamash.domain.MediaItem
import io.shamash.domain.Moment
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.TvApp
import io.shamash.domain.Weather
import io.shamash.ui.Featured
import io.shamash.ui.WeatherNow

/** What one provider gave on its last load. */
@Immutable
data class ProviderContent(val shelves: List<Shelf> = emptyList(), val featured: List<MediaItem> = emptyList())

/** Everything loaded so far (each source updates its part). */
@Immutable
data class Loaded(
    val providers: Map<String, ProviderContent> = emptyMap(),
    val order: List<String> = emptyList(),
    val pending: Set<String> = emptySet(),
    val matches: List<Match> = emptyList(),
    val installed: List<TvApp> = emptyList(),
    val recent: List<TvApp> = emptyList(),
    val weather: Weather? = null,
    val started: Boolean = false,
)

/** The inputs of one home state: the profile, what was loaded, what was watched, and the time. */
data class HomeInput(val profile: Profile, val loaded: Loaded, val history: List<MediaItem>, val now: Long, val hour: Int)

/** The whole home screen, built off the main thread in one go. SPEC: PERF-05 */
@Immutable
data class HomeUi(
    val ready: Boolean = false,
    val greeting: Greeting = Greeting(Moment.EVENING, null),
    val moment: Moment = Moment.EVENING,
    val featured: List<Featured> = emptyList(),
    val home: List<Shelf> = emptyList(),
    val discover: List<Shelf> = emptyList(),
    val appsRow: List<TvApp> = emptyList(),
    val recentApps: List<TvApp> = emptyList(),
    val allApps: List<TvApp> = emptyList(),
    val weather: WeatherNow? = null,
    val city: String = "",
)
