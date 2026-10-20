package io.shamash.data

import io.shamash.domain.LinkResolver
import io.shamash.domain.MatchSource
import io.shamash.domain.Provider
import io.shamash.domain.WeatherSource

/**
 * Where each source is built: the one place that lists them (the ServiceLocator calls these). Adding a provider means
 * adding it here. SPEC: ARCH-02, PRV-14
 */
object Sources {
    fun providers(http: Http, prefs: Preferences, apps: TvApps): List<Provider> = emptyList()

    fun matches(http: Http): MatchSource = object : MatchSource {
        override suspend fun matches(profile: io.shamash.domain.Profile) = emptyList<io.shamash.domain.Match>()
    }

    fun weather(http: Http): WeatherSource = object : WeatherSource {
        override suspend fun current(profile: io.shamash.domain.Profile): io.shamash.domain.Weather? = null
    }

    fun links(http: Http): LinkResolver = object : LinkResolver {
        override suspend fun resolve(target: io.shamash.domain.OpenTarget.Lookup, region: String): String? = null
    }
}
