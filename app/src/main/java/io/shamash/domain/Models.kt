package io.shamash.domain

/** What a card is (decides its shape and how it opens). SPEC: ARCH-10 */
enum class Kind { MOVIE, SERIES, VIDEO, MATCH, LIVE }

/** How to open an item, most specific first; the Opener falls back to launching the app. SPEC: ARCH-11 */
sealed interface OpenTarget {
    /** A page link the app opens on that title (falls back to [App] when nothing handles it). */
    data class AppPage(val uri: String, val appPackage: String) : OpenTarget

    /** A TMDB title whose page link is looked up when it's opened (JustWatch). */
    data class Lookup(val tmdbId: Int, val kind: Kind, val title: String, val appPackage: String) : OpenTarget

    data class App(val appPackage: String) : OpenTarget

    data class Video(val youtubeId: String) : OpenTarget

    /** A screen inside Shamash ("search?q=…"). */
    data class Internal(val route: String) : OpenTarget
}

/** One card anywhere in Shamash. SPEC: ARCH-10 */
data class MediaItem(
    val id: String,
    val providerId: String,
    val title: String,
    val kind: Kind,
    val open: OpenTarget,
    val subtitle: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,     // 16:9; the carousel needs ≥ 1280 wide
    val appPackage: String? = null,
    val progress: Float? = null,         // 0..1 for Continue watching
    val startsAt: Long? = null,          // epoch ms, matches and live events
    val match: Match? = null,
)

/** How a row draws its cards. */
enum class ShelfStyle { WIDE, POSTER, RANKED, VIDEO, MATCH, APPS }

/**
 * A row of cards. The title is a string-resource key (UI maps it) plus an optional argument, so the domain stays free
 * of Android resources. SPEC: ARCH-07
 */
data class Shelf(
    val id: String,
    val titleKey: String,
    val style: ShelfStyle,
    val items: List<MediaItem>,
    val titleArg: String? = null,
)

enum class MatchState { PRE, LIVE, DONE }

data class Team(val name: String, val shortName: String, val logoUrl: String, val score: String = "")

/** A football match. SPEC: MC-01 */
data class Match(
    val id: String,
    val competition: String,       // ESPN code, e.g. "esp.1"
    val competitionName: String,
    val home: Team,
    val away: Team,
    val startsAt: Long,
    val state: MatchState,
    val clock: String = "",        // "67'", "HT", "FT"
    val mine: Boolean = false,     // one of the user's teams plays
)

data class Weather(
    val temperatureC: Double,
    val code: Int,
    val isDay: Boolean,
    val sunrise: Long,
    val sunset: Long,
)

/** A launchable TV app. */
data class TvApp(val packageName: String, val label: String, val streaming: Boolean)

/** What the user told setup; every screen reads it. SPEC: ONB-06 */
data class Profile(
    val onboarded: Boolean = false,
    val name: String = "",
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val region: String = "US",                    // two letters, for "where to watch"
    val subscriptions: Set<String> = emptySet(),  // app packages the user pays for
    val teams: Set<String> = emptySet(),
    val competitions: Set<String> = emptySet(),   // ESPN codes
    val genres: Set<String> = emptySet(),
    val tmdbKey: String = "",
    val youtubeClientId: String = "",
    val youtubeClientSecret: String = "",
    val cinemanaAddon: Boolean = false,
    val keepRunning: Set<String> = setOf("com.netflix.ninja", "com.google.android.youtube.tv"),
) {
    val hasTmdb get() = tmdbKey.isNotBlank()
    val hasYoutubeClient get() = youtubeClientId.isNotBlank() && youtubeClientSecret.isNotBlank()

    fun isMyTeam(teamName: String): Boolean =
        teams.any { it.isNotBlank() && teamName.contains(it.trim(), ignoreCase = true) }
}
