package io.shamash.data.backup

import io.shamash.domain.Profile
import java.net.URI

/**
 * Between a [Profile] and its backup form, and the single place that decides what leaves the TV: secrets, PIN hashes
 * and sign-in values stay out unless the person opted in (CMP-12, CMP-14). Pure. SPEC: CMP-12, CMP-14
 */
object BackupSnapshot {
    /** Everything the profile holds, secrets included; always pass the result through [restrict] before writing. */
    fun of(p: Profile, feeds: List<BackupFeed> = emptyList(), extras: Map<String, String> = emptyMap()) = BackupProfile(
        id = p.id, name = p.name, kind = p.kind, avatarColor = p.avatarColor, keyMode = p.keyMode,
        contentLevel = p.contentLevel, onboarded = p.onboarded, city = p.city, latitude = p.latitude,
        longitude = p.longitude, region = p.region, addons = p.addons, follows = p.follows, layout = p.layout,
        subscriptions = p.subscriptions, teams = p.teams, competitions = p.competitions, genres = p.genres,
        keepRunning = p.keepRunning, feeds = feeds, extras = extras, pinHash = p.pinHash,
        tmdbKey = p.tmdbKey.ifBlank { null }, youtubeClientId = p.youtubeClientId.ifBlank { null },
        youtubeClientSecret = p.youtubeClientSecret.ifBlank { null },
    )

    /** What a restored profile looks like; fields a backup leaves out come back empty, never guessed. */
    fun toProfile(b: BackupProfile, id: String = b.id) = Profile(
        id = id, kind = b.kind, avatarColor = b.avatarColor, pinHash = b.pinHash, keyMode = b.keyMode,
        addons = b.addons, follows = b.follows, layout = b.layout, onboarded = b.onboarded, name = b.name,
        city = b.city, latitude = b.latitude, longitude = b.longitude, region = b.region,
        subscriptions = b.subscriptions, teams = b.teams, competitions = b.competitions, genres = b.genres,
        tmdbKey = b.tmdbKey.orEmpty(), youtubeClientId = b.youtubeClientId.orEmpty(),
        youtubeClientSecret = b.youtubeClientSecret.orEmpty(), contentLevel = b.contentLevel,
        keepRunning = b.keepRunning,
    )

    /** Removes what [options] does not allow. Feed addresses that carry a login or a token count as secrets. */
    fun restrict(payload: BackupPayload, options: BackupOptions): BackupPayload = payload.copy(
        profiles = payload.profiles.map { b ->
            b.copy(
                pinHash = b.pinHash.takeIf { options.includePins },
                tmdbKey = b.tmdbKey.takeIf { options.includeSecrets },
                youtubeClientId = b.youtubeClientId.takeIf { options.includeSecrets },
                youtubeClientSecret = b.youtubeClientSecret.takeIf { options.includeSecrets },
                feeds = if (options.includeSecrets) b.feeds else b.feeds.filterNot { carriesSecret(it.url) },
            )
        },
        secrets = if (options.includeSecrets) payload.secrets.filterKeys { !BackupValidator.isForbiddenSecret(it) } else emptyMap(),
    )

    fun carriesSecret(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return true
        return uri.rawUserInfo != null || !uri.rawQuery.isNullOrEmpty()
    }
}
