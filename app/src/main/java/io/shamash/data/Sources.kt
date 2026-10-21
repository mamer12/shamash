package io.shamash.data

import android.content.Context
import io.shamash.data.espn.EspnMatches
import io.shamash.data.justwatch.JustWatchLinks
import io.shamash.data.tmdb.TmdbClient
import io.shamash.data.tmdb.TmdbProvider
import io.shamash.data.tod.TodProvider
import io.shamash.data.weather.Geocoder
import io.shamash.data.weather.OpenMeteo
import io.shamash.data.youtube.SecretStore
import io.shamash.data.youtube.YouTubeAuth
import io.shamash.data.youtube.YouTubeProvider
import io.shamash.domain.LinkResolver
import io.shamash.domain.MatchSource
import io.shamash.domain.Provider
import io.shamash.domain.WatchHistory
import io.shamash.domain.WeatherSource

/**
 * Where each source is built: the one place that lists them (the ServiceLocator calls these). Adding a provider means
 * adding it here. SPEC: ARCH-02, PRV-14
 */
object Sources {
    /**
     * [history] seeds YouTube's recommendations with what was opened from Shamash (PRV-07); [context] gives the
     * Cinemana add-on a place for its progress and My List (in memory without one).
     */
    fun providers(
        http: Http, prefs: Preferences, apps: TvApps, history: WatchHistory? = null, context: Context? = null,
    ): List<Provider> {
        val installed: (String) -> Boolean = apps::isInstalled
        val tmdb = TmdbClient(http)
        return listOf(
            TmdbProvider(tmdb, installed),
            YouTubeProvider(http, youtubeAuth(http, prefs), history, installed),
            TodProvider(http, installed),
        ) + listOfNotNull(context?.let(io.shamash.feature.companion.QueueProvider::of)) + addOnRegistry.providers { addOn ->
            DataDeps(
                credentials(prefs, addOn), prefs::current, installed,
                listOfNotNull(
                    Http::class.java to http, Preferences::class.java to prefs,
                    context?.let { Context::class.java to it },
                ).toMap(),
            )
        }
    }

    /** The optional add-ons, one registration line each (PEX-01). Settings -> Add-ons lists these. */
    val addOns: List<io.shamash.domain.AddOn> = listOf(
        io.shamash.feature.cinemana.CinemanaAddon.addOn,
        io.shamash.data.plex.PlexProvider.addOn,
        io.shamash.data.jellyfin.JellyfinProvider.addOn,
        io.shamash.data.iptv.IptvProvider.addOn,
        io.shamash.data.myfeed.MyFeedProvider.addOn,
        io.shamash.data.podcasts.PodcastProvider.addOn,
        io.shamash.data.radio.RadioProvider.addOn,
        io.shamash.data.live.LiveProvider.addOn,
        io.shamash.data.trakt.TrackerProvider.trakt,
        io.shamash.data.trakt.TrackerProvider.simkl,
        io.shamash.data.widgets.WidgetsProvider.addOn,
        io.shamash.addons.example.ExampleAddOn.addOn,   // the template (docs/ADDONS.md); delete in your fork
    )

    val addOnRegistry = AddOnRegistry(addOns)

    /** An add-on's credentials for the active profile; values never leave the TV. SPEC: PEX-03 */
    fun credentials(prefs: Preferences, addOn: io.shamash.domain.AddOn) =
        SecretCredentials(PreferenceSecrets(prefs), addOn.id, addOn.requires) { prefs.current().id }

    /** YouTube sign-in for Settings and onboarding; instances share the stored sign-in. SPEC: PRV-06 */
    fun youtubeAuth(http: Http, prefs: Preferences): YouTubeAuth =
        YouTubeAuth(http, PreferenceSecrets(prefs), prefs::current)

    fun matches(http: Http): MatchSource = EspnMatches(http)

    fun weather(http: Http): WeatherSource = OpenMeteo(http)

    fun links(http: Http): LinkResolver = JustWatchLinks(http)

    /** City → coordinates for onboarding. SPEC: ONB-02 */
    fun geocoder(http: Http): Geocoder = Geocoder(http)

    private class PreferenceSecrets(private val prefs: Preferences) : SecretStore {
        override suspend fun secret(key: String) = prefs.secret(key)
        override suspend fun setSecret(key: String, value: String?) = prefs.setSecret(key, value)
    }
}
