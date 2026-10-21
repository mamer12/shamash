package io.shamash.data.backup

import io.shamash.data.Logger
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Safety copies of the non-secret profiles and layout, written before a migration (PLT-03), the newest three kept.
 * Never holds secrets, PIN hashes or feeds with logins; lives in app storage, outside Android's backup (ARCH-06).
 * SPEC: PLT-03
 */
class LocalBackups(private val dir: File, private val keep: Int = KEEP) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Writes a copy named for [stamp]; false (and a log line) when it could not. Never throws. */
    fun save(payload: BackupPayload, stamp: Long): Boolean = try {
        val clean = BackupSnapshot.restrict(payload.copy(createdAt = stamp), BackupOptions())
        dir.mkdirs()
        val tmp = File(dir, "tmp-$stamp")
        tmp.writeText(json.encodeToString(BackupPayload.serializer(), clean), Charsets.UTF_8)
        check(tmp.renameTo(File(dir, name(stamp)))) { "rename" }
        prune()
        true
    } catch (e: Exception) {
        Logger.w("local backup", e)
        false
    }

    /** Newest first. */
    fun list(): List<File> = dir.listFiles { f -> PATTERN.matches(f.name) }.orEmpty().sortedByDescending { it.name }

    private fun prune() = list().drop(keep).forEach { it.delete() }

    private fun name(stamp: Long) = "profiles-%015d.json".format(stamp)

    companion object {
        const val KEEP = 3
        private val PATTERN = Regex("profiles-\\d{15}\\.json")
    }
}
