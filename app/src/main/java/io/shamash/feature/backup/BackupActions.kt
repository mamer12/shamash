package io.shamash.feature.backup

import io.shamash.data.Logger
import io.shamash.data.Preferences
import io.shamash.data.backup.BackupCodec
import io.shamash.data.backup.BackupError
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.BackupPlanner
import io.shamash.data.backup.BackupProfile
import io.shamash.data.backup.BackupResult
import io.shamash.data.backup.BackupSnapshot
import io.shamash.data.backup.ImportMode
import io.shamash.data.backup.ImportPlan
import io.shamash.data.backup.LocalBackups
import io.shamash.domain.AddOn
import io.shamash.domain.Profile
import io.shamash.feature.profiles.ProfileLimits
import io.shamash.feature.profiles.SecurityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The outcome of applying an import. [Failed] means nothing visible changed. */
sealed interface ApplyResult {
    data class Done(val added: Int, val replaced: Int) : ApplyResult
    data object Failed : ApplyResult
}

/**
 * Reads the TV's state into a backup and writes a backup into it (PLT-01, CMP-13..15). The file is fully decrypted,
 * validated and planned before the first write; additions come before deletions, and a failure on the way undoes
 * what was added, so a refused or failed import leaves the profiles as they were. Passphrases and secrets are never
 * logged. SPEC: PLT-01, PLT-02, PLT-03, CMP-15
 */
class BackupActions(
    private val prefs: Preferences,
    private val security: SecurityStore,
    private val addOns: List<AddOn>,
    private val local: LocalBackups?,
    private val codec: BackupCodec = BackupCodec(),
) {
    private val store get() = prefs.profiles

    suspend fun export(passphrase: String, options: BackupOptions): BackupResult<ByteArray> {
        val payload = snapshot()
        return withContext(Dispatchers.Default) { codec.export(payload, passphrase, options) }
    }

    suspend fun open(file: ByteArray, passphrase: String): BackupResult<BackupPayload> =
        withContext(Dispatchers.Default) { codec.open(file, passphrase) }

    suspend fun preview(payload: BackupPayload) = BackupPlanner.preview(payload, store.profiles.first())

    suspend fun apply(payload: BackupPayload, mode: ImportMode): ApplyResult {
        val before = store.profiles.first()
        val ownerId = store.ownerId() ?: return ApplyResult.Failed
        local?.save(snapshot().let { BackupSnapshot.restrict(it, BackupOptions()) }, System.currentTimeMillis())
        val plan = BackupPlanner.plan(payload, before, ownerId, mode)
        val created = mutableListOf<String>()
        return try {
            val ids = write(plan, payload, ownerId, created)
            restoreSecrets(payload.secrets, ids, mode)
            plan.remove.forEach { store.delete(it) }
            ApplyResult.Done(plan.add.size, plan.replace.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("backup import, undone", e)
            created.forEach { store.delete(it) }
            before.firstOrNull { it.id == ownerId }?.let { store.save(it) }
            ApplyResult.Failed
        }
    }

    /** Old backup id -> id on this TV, for every profile written. */
    private suspend fun write(plan: ImportPlan, payload: BackupPayload, ownerId: String, created: MutableList<String>): Map<String, String> {
        val ids = HashMap<String, String>()
        plan.replace.forEach { b ->
            store.save(BackupSnapshot.toProfile(b, ownerId))
            ids[payload.ownerId] = ownerId
            restoreLimits(ownerId, b)
        }
        plan.add.forEach { b ->
            val added = store.add(BackupSnapshot.toProfile(b, "")) ?: error("profile limit")
            created += added.id
            ids[b.id] = added.id
            restoreLimits(added.id, b)
        }
        return ids
    }

    private suspend fun restoreLimits(id: String, b: BackupProfile) {
        b.extras[LIMITS]?.let { security.setLimits(id, ProfileLimits.decode(it)) }
    }

    private suspend fun restoreSecrets(secrets: Map<String, String>, ids: Map<String, String>, mode: ImportMode) {
        secrets.forEach { (key, value) ->
            val owner = ids.keys.firstOrNull { key.startsWith(it + "_") }
            when {
                owner != null -> prefs.setSecret(ids.getValue(owner) + key.removePrefix(owner), value)
                mode == ImportMode.REPLACE || prefs.secret(key) == null -> prefs.setSecret(key, value)
            }
        }
    }

    private suspend fun snapshot(): BackupPayload {
        val profiles = store.profiles.first()
        val secrets = HashMap<String, String>()
        suspend fun take(key: String) { prefs.secret(key)?.takeIf { it.isNotEmpty() }?.let { secrets[key] = it } }
        take(YOUTUBE_REFRESH)
        profiles.forEach { p -> addOns.forEach { a -> a.requires.forEach { f -> take("${p.id}_${a.id}_${f.key}") } } }
        return BackupPayload(
            ownerId = store.ownerId().orEmpty(),
            profiles = profiles.map { p ->
                val limits = security.limits(p.id).first()
                BackupSnapshot.of(p, extras = if (limits.isEmpty && limits.allowedApps == null) emptyMap() else mapOf(LIMITS to ProfileLimits.encode(limits)))
            },
            secrets = secrets,
        )
    }

    companion object {
        const val LIMITS = "limits"
        private const val YOUTUBE_REFRESH = "youtube_refresh"

        fun message(e: BackupError): Int = when (e) {
            BackupError.WEAK_PASSPHRASE -> io.shamash.R.string.backup_err_weak
            BackupError.TOO_LARGE -> io.shamash.R.string.backup_err_large
            BackupError.NOT_A_BACKUP -> io.shamash.R.string.backup_err_not
            BackupError.NEWER_VERSION -> io.shamash.R.string.backup_err_newer
            BackupError.WRONG_PASSPHRASE_OR_DAMAGED -> io.shamash.R.string.backup_err_wrong
            BackupError.INVALID -> io.shamash.R.string.backup_err_invalid
        }
    }
}
