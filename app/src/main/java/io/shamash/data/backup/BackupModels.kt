package io.shamash.data.backup

import io.shamash.domain.ContentLevel
import io.shamash.domain.Follow
import io.shamash.domain.KeyMode
import io.shamash.domain.Layout
import io.shamash.domain.ProfileKind
import kotlinx.serialization.Serializable

/*
 * What a backup holds (CMP-12) and the choices that shape it. Pure data. Public surface: BackupProfile, BackupFeed,
 * BackupPayload, BackupOptions, BackupLimits. SPEC: PLT-02, CMP-12
 */

@Serializable
data class BackupFeed(val name: String, val url: String)

/**
 * One profile. [pinHash], [tmdbKey], [youtubeClientId] and [youtubeClientSecret] are null unless the user opted in
 * when the file was made. [extras] carries small non-secret text values other features own (for example `limits`).
 */
@Serializable
data class BackupProfile(
    val id: String,
    val name: String = "",
    val kind: ProfileKind = ProfileKind.ADULT,
    val avatarColor: Int = 0,
    val keyMode: KeyMode = KeyMode.SHARED,
    val contentLevel: ContentLevel = ContentLevel.ALL,
    val onboarded: Boolean = false,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val region: String = "US",
    val addons: Set<String> = emptySet(),
    val follows: Set<Follow> = emptySet(),
    val layout: Layout = Layout(),
    val subscriptions: Set<String> = emptySet(),
    val teams: Set<String> = emptySet(),
    val competitions: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val keepRunning: Set<String> = emptySet(),
    val feeds: List<BackupFeed> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val pinHash: String? = null,
    val tmdbKey: String? = null,
    val youtubeClientId: String? = null,
    val youtubeClientSecret: String? = null,
)

/** The decrypted body. [secrets] is empty unless the file was made with [BackupOptions.includeSecrets]. */
@Serializable
data class BackupPayload(
    val createdAt: Long = 0,
    val ownerId: String = "",
    val profiles: List<BackupProfile> = emptyList(),
    val secrets: Map<String, String> = emptyMap(),
)

/** What the person ticked when exporting (CMP-12, CMP-14). Both default off. */
data class BackupOptions(val includeSecrets: Boolean = false, val includePins: Boolean = false)

/** Limits every import and export obeys, so a hostile or corrupt file cannot exhaust memory. SPEC: CMP-15 */
object BackupLimits {
    const val MAX_FILE_BYTES = 1 shl 20          // CMP-06: backup import <= 1 MB
    const val MAX_PROFILES = 6                   // ProfileStore.MAX_PROFILES
    const val MAX_TEXT = 200
    const val MAX_SET = 200
    const val MAX_ROWS = 200
    const val MAX_FEEDS = 40
    const val MAX_EXTRAS = 20
    const val MAX_EXTRA_VALUE = 8_000
    const val MAX_SECRETS = 400
    const val MAX_SECRET_VALUE = 4_000
    const val MIN_PASSPHRASE = 8
    const val MAX_PASSPHRASE = 256
    const val MIN_ITERATIONS = 210_000
    const val MAX_ITERATIONS = 2_000_000
}
