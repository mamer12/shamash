package io.shamash.data

import io.shamash.BuildConfig
import io.shamash.domain.Profile

/**
 * The TMDB key and the YouTube OAuth client, set at build time in local.properties (`tmdb.key`, `youtube.clientId`,
 * `youtube.clientSecret`; never committed) and baked into BuildConfig. They win over anything stored and are never
 * written to the profile store, so they can't leak into backups or the phone page.
 */
data class BuildKeys(val tmdb: String = "", val youtubeClientId: String = "", val youtubeClientSecret: String = "") {
    fun applyTo(p: Profile): Profile = p.copy(
        tmdbKey = tmdb.ifBlank { p.tmdbKey },
        youtubeClientId = youtubeClientId.ifBlank { p.youtubeClientId },
        youtubeClientSecret = youtubeClientSecret.ifBlank { p.youtubeClientSecret },
    )

    fun stripFrom(p: Profile): Profile = p.copy(
        tmdbKey = if (tmdb.isBlank()) p.tmdbKey else "",
        youtubeClientId = if (youtubeClientId.isBlank()) p.youtubeClientId else "",
        youtubeClientSecret = if (youtubeClientSecret.isBlank()) p.youtubeClientSecret else "",
    )

    override fun toString() = "BuildKeys(tmdb=${tmdb.isNotBlank()}, youtube=${youtubeClientId.isNotBlank()})"

    companion object {
        fun fromBuild() = BuildKeys(BuildConfig.TMDB_KEY, BuildConfig.YOUTUBE_CLIENT_ID, BuildConfig.YOUTUBE_CLIENT_SECRET)
    }
}
