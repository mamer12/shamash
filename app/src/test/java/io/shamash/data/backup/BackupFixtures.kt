package io.shamash.data.backup

import io.shamash.domain.ContentLevel
import io.shamash.domain.Follow
import io.shamash.domain.Layout
import io.shamash.domain.ProfileKind
import io.shamash.domain.RowPref

internal object BackupFixtures {
    const val PASS = "correct horse battery"

    val owner = BackupProfile(
        id = "p-owner", name = "Mustafa", kind = ProfileKind.ADULT, avatarColor = 0x11223344, onboarded = true,
        city = "Baghdad", latitude = 33.3, longitude = 44.4, region = "IQ",
        addons = setOf("cinemana"), follows = setOf(Follow("team", "Real Madrid")),
        layout = Layout(rows = listOf(RowPref("matches", pinned = true, order = 1), RowPref("tmdb:popular", hidden = true))),
        teams = setOf("Real Madrid"), competitions = setOf("uefa.champions"), genres = setOf("drama"),
        subscriptions = setOf("com.netflix.ninja"), keepRunning = setOf("com.netflix.ninja"),
        feeds = listOf(BackupFeed("News", "https://example.org/feed.xml"), BackupFeed("Private", "https://u:pw@example.org/f?token=abc")),
        extras = mapOf("limits" to """{"dailyMinutes":60}"""),
        pinHash = "pbkdf2\$hash", tmdbKey = "TMDB-SECRET-KEY", youtubeClientId = "YT-ID", youtubeClientSecret = "YT-SECRET",
    )
    val kid = BackupProfile(id = "p-kid", name = "Lina", kind = ProfileKind.KIDS, contentLevel = ContentLevel.KIDS)

    fun payload(secrets: Map<String, String> = mapOf("p-owner_plex_token" to "PLEX-TOKEN", "youtube_refresh" to "REFRESH")) =
        BackupPayload(ownerId = "p-owner", profiles = listOf(owner, kid), secrets = secrets)

    fun codec(clock: () -> Long = { 1_700_000_000_000 }) = BackupCodec(clock = clock)

    fun sealed(options: BackupOptions = BackupOptions(), payload: BackupPayload = payload()): ByteArray =
        (codec().export(payload, PASS, options) as BackupResult.Ok).value

    fun opened(bytes: ByteArray, pass: String = PASS) = codec().open(bytes, pass)
}
