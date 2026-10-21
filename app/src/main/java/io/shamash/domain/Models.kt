package io.shamash.domain

import kotlinx.serialization.Serializable

/** What a card is (decides its shape and how it opens). SPEC: ARCH-10 */
enum class Kind { MOVIE, SERIES, VIDEO, MATCH, LIVE }

/** How to open an item, most specific first; the Opener falls back to launching the app. SPEC: ARCH-11 */
@Serializable
sealed interface OpenTarget {
    /** A page link the app opens on that title (falls back to [App] when nothing handles it). */
    @Serializable
    data class AppPage(val uri: String, val appPackage: String, val mime: String? = null) : OpenTarget

    /** A TMDB title whose page link is looked up when it's opened (JustWatch). */
    @Serializable
    data class Lookup(val tmdbId: Int, val kind: Kind, val title: String, val appPackage: String) : OpenTarget

    @Serializable
    data class App(val appPackage: String) : OpenTarget

    /**
     * One screen of an app, by class name, with string extras (e.g. a title in Watchme+); falls back to [App].
     * [resumeExtraKey], set by the provider, is the extra that carries a start position in ms (HOME-20), so the core
     * can resume a preview there without knowing the app.
     */
    @Serializable
    data class AppScreen(
        val appPackage: String,
        val className: String,
        val extras: Map<String, String>,
        val resumeExtraKey: String? = null,
    ) : OpenTarget

    @Serializable
    data class Video(val youtubeId: String) : OpenTarget

    /** A screen inside Shamash ("search?q=…"). */
    @Serializable
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
    val rating: String? = null,          // certification ("PG", "TV-14"); null = unknown. SPEC: PRF-12
    val genres: List<String> = emptyList(),   // English names when the source knows them. SPEC: PER-01
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
    val title: String? = null,          // the user's own name for the row (LAY-06); shown as-is
    val reason: Reason? = null,         // why this row is recommended; null = no line. SPEC: PER-11
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
    val sport: Sport = Sport.SOCCER,   // SPEC: MC-07
    val session: String = "",         // F1 session code (FP1, Q, SPR, R...); the labels are localized (MC-15)
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

/** Which titles add-ons may show. SPEC: CIN-08 */
enum class ContentLevel { ALL, FAMILY, KIDS }

/** Who a profile is for. Exactly one ADULT is the owner (PRF-03). SPEC: PRF-01 */
enum class ProfileKind { ADULT, KIDS, GUEST }

/** Whose keys a profile uses: the owner's (SHARED) or its own. SPEC: PRF-09 */
enum class KeyMode { SHARED, OWN }

/** Something a profile follows (a team, a player, a sport). SPEC: MC-09 */
@Serializable
data class Follow(val kind: String, val name: String)

/**
 * What the user told setup; every screen reads it. One per person on the TV; providers receive the active one.
 * [id] is blank until a store assigns it. New fields default so old stores load unchanged. SPEC: ONB-06, PRF-01, PRF-18
 */
data class Profile(
    val id: String = "",
    val kind: ProfileKind = ProfileKind.ADULT,
    val avatarColor: Int = DEFAULT_AVATAR,                    // ARGB
    val pinHash: String? = null,                              // salted PBKDF2, never the PIN (PRF-05)
    val keyMode: KeyMode = KeyMode.SHARED,
    val addons: Set<String> = emptySet(),                     // enabled AddOn ids (PEX-02)
    val follows: Set<Follow> = emptySet(),
    val layout: Layout = Layout(),
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
    val contentLevel: ContentLevel = ContentLevel.ALL,
    val keepRunning: Set<String> = setOf("com.netflix.ninja", "com.google.android.youtube.tv"),
) {
    val cinemanaAddon get() = "cinemana" in addons
    val hasTmdb get() = tmdbKey.isNotBlank()
    val hasYoutubeClient get() = youtubeClientId.isNotBlank() && youtubeClientSecret.isNotBlank()

    fun isMyTeam(teamName: String): Boolean =
        teams.any { it.isNotBlank() && teamName.contains(it.trim(), ignoreCase = true) }

    fun withAddon(addonId: String, on: Boolean): Profile = copy(addons = if (on) addons + addonId else addons - addonId)

    companion object {
        const val DEFAULT_AVATAR = 0xFFF4C450.toInt()
    }
}
