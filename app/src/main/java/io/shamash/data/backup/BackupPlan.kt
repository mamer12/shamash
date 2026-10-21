package io.shamash.data.backup

import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind

enum class ImportMode { MERGE, REPLACE }

/** What an import would do, shown to the person before it happens (CMP-15). */
data class ImportPreview(val profiles: List<PreviewRow>, val hasSecrets: Boolean, val hasPins: Boolean)
data class PreviewRow(val name: String, val kind: ProfileKind, val rows: Int, val follows: Int, val exists: Boolean)

/**
 * The profiles an import writes: [add] are new, [replace] overwrite an existing id, [remove] are existing profiles
 * dropped by REPLACE (never the owner). Computed in full before anything is written. SPEC: CMP-15
 */
data class ImportPlan(val add: List<BackupProfile>, val replace: List<BackupProfile>, val remove: List<String>)

object BackupPlanner {
    fun preview(payload: BackupPayload, existing: List<Profile>): ImportPreview {
        val have = existing.map { it.id }.toSet()
        return ImportPreview(
            payload.profiles.map { PreviewRow(it.name, it.kind, it.layout.rows.size, it.follows.size, it.id in have) },
            hasSecrets = payload.secrets.isNotEmpty() || payload.profiles.any { it.tmdbKey != null || it.youtubeClientSecret != null },
            hasPins = payload.profiles.any { it.pinHash != null },
        )
    }

    /**
     * MERGE adds backup profiles that are not here (matched by id, or by name and kind), room permitting. REPLACE
     * makes this TV match the backup: the owner keeps its id and takes the backup owner's data; others are swapped.
     */
    fun plan(payload: BackupPayload, existing: List<Profile>, ownerId: String, mode: ImportMode, max: Int = BackupLimits.MAX_PROFILES): ImportPlan {
        val incoming = payload.profiles
        return when (mode) {
            ImportMode.MERGE -> {
                val here = existing.toMutableList()
                val add = ArrayList<BackupProfile>()
                for (b in incoming) {
                    val known = here.any { it.id == b.id || (it.name.equals(b.name, true) && it.kind == b.kind) }
                    if (known || here.size >= max) continue
                    add += b
                    here += Profile(id = b.id, name = b.name, kind = b.kind)
                }
                ImportPlan(add, emptyList(), emptyList())
            }
            ImportMode.REPLACE -> {
                val owner = incoming.first { it.id == payload.ownerId }.copy(id = ownerId, kind = ProfileKind.ADULT)
                val others = incoming.filter { it.id != payload.ownerId }.take(max - 1)
                ImportPlan(others, listOf(owner), existing.map { it.id }.filter { it != ownerId })
            }
        }
    }
}
