package io.shamash.feature.companion

import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.BackupResult
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.Profile
import java.net.InetAddress

/** What the TV said about a backup the phone asked for. */
enum class Confirm { YES, NO, BUSY }

/** How an import asked from the phone ended on the TV. */
sealed interface ImportOutcome {
    data class Done(val added: Int, val replaced: Int) : ImportOutcome
    data object Declined : ImportOutcome
    data object Busy : ImportOutcome
    data object Failed : ImportOutcome
}

/** The parts of the phone API's subject (the TV) a change touches, for the toast the TV shows. */
enum class Changed { ROWS, TEAMS, FEEDS, QUEUE }

/**
 * Everything the phone API needs from the TV, so the API itself stays pure and testable: the active profile and
 * its PINs, the row model, feeds, the queue, and the backup with the TV's own confirmation. SPEC: CMP-06..15
 */
interface PhoneBackend {
    suspend fun active(): Profile
    suspend fun owner(): Profile?

    /** Checks a 4-digit profile PIN with that profile's own lockout (PRF-05). */
    suspend fun checkPin(profileId: String, pin: String): PinAnswer

    /** Saves the active profile and tells the TV what changed (applied live, a short toast). */
    suspend fun saveProfile(profile: Profile, what: Changed)

    /** Every row Home could show for the active profile, default titles in the TV's language. */
    suspend fun rowChoices(): List<RowChoice>

    /** The active profile's feeds; null when My feed isn't available on this TV. */
    suspend fun feeds(): List<UserFeed>?
    suspend fun saveFeeds(feeds: List<UserFeed>)

    /** "Try it": the first three mapped titles; throws when the feed can't be read. */
    suspend fun tryFeed(feed: UserFeed): List<String>

    /** DNS for the feed address check (CMP-10). */
    fun resolve(host: String): List<InetAddress>

    suspend fun queue(profileId: String): List<QueueEntry>
    suspend fun addToQueue(profileId: String, entry: QueueEntry): Boolean

    suspend fun confirmExport(): Confirm
    suspend fun exportFile(passphrase: String, options: BackupOptions): BackupResult<ByteArray>
    suspend fun openFile(file: ByteArray, passphrase: String): BackupResult<BackupPayload>

    /** Shows the preview on the TV; the person there picks Merge or Replace (owner PIN) and it is applied. */
    suspend fun confirmImport(payload: BackupPayload): ImportOutcome

    /** Today's date for the file name ("2026-10-21"). */
    fun today(): String
    fun now(): Long
}
