package io.shamash.data.backup

import kotlinx.serialization.json.JsonObject

/**
 * Brings an older payload up to the current shape, one version at a time. A step takes the payload of version `n`
 * and returns version `n + 1`; a gap in the chain makes the file unreadable rather than guessed. SPEC: CMP-15
 */
class BackupMigrations(private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()) {
    /** Null when a step is missing or throws. */
    fun upgrade(payload: JsonObject, from: Int, to: Int): JsonObject? {
        var current = payload
        for (v in from until to) {
            val step = steps[v] ?: return null
            current = try { step(current) } catch (e: Exception) { return null }
        }
        return current
    }
}
