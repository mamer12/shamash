package io.shamash.feature.profiles

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.shamash.data.DataStores
import io.shamash.domain.ContentLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Minutes used on one day. */
data class Usage(val day: String, val minutes: Int)

/*
 * The small per-profile state that is not part of the Profile: limits, PIN lockout, minutes used today, an owner
 * unlock, and the TV-wide switches (ask on launch, Guest, Guest's content level, the time high-water mark).
 * Local only; exported nowhere but encrypted backups. SPEC: PRF-02, PRF-05, PRF-14, PRF-20
 */
class SecurityStore(stores: DataStores) {
    private val store = stores.store(NAME)

    fun limits(id: String): Flow<ProfileLimits> = store.data.map { ProfileLimits.decode(it[stringPreferencesKey("limits_$id")]) }

    suspend fun setLimits(id: String, limits: ProfileLimits) {
        store.edit { it[stringPreferencesKey("limits_$id")] = ProfileLimits.encode(limits) }
    }

    suspend fun lockout(id: String): LockoutState =
        PinLockout.decode(store.data.first()[stringPreferencesKey("lockout_$id")])

    suspend fun setLockout(id: String, state: LockoutState) {
        store.edit { it[stringPreferencesKey("lockout_$id")] = PinLockout.encode(state) }
    }

    suspend fun usage(id: String): Usage {
        val raw = store.data.first()[stringPreferencesKey("used_$id")]?.split(':')
        return if (raw?.size == 2) Usage(raw[0], raw[1].toIntOrNull() ?: 0) else Usage("", 0)
    }

    suspend fun setUsage(id: String, usage: Usage) {
        store.edit { it[stringPreferencesKey("used_$id")] = "${usage.day}:${usage.minutes}" }
    }

    /** Epoch ms an owner unlock lasts until (0 = none). */
    suspend fun overrideUntil(id: String): Long = store.data.first()[longPreferencesKey("override_$id")] ?: 0L

    suspend fun setOverrideUntil(id: String, epochMs: Long) {
        store.edit { it[longPreferencesKey("override_$id")] = epochMs }
    }

    val askOnLaunch: Flow<Boolean> = store.data.map { it[ASK] ?: true }
    suspend fun setAskOnLaunch(on: Boolean) { store.edit { it[ASK] = on } }

    val guestEnabled: Flow<Boolean> = store.data.map { it[GUEST] ?: false }
    suspend fun setGuestEnabled(on: Boolean) { store.edit { it[GUEST] = on } }

    /** KIDS or FAMILY, chosen by the owner; FAMILY by default. SPEC: PRF-11 */
    val guestLevel: Flow<ContentLevel> = store.data.map { s ->
        if (s[GUEST_LEVEL] == ContentLevel.KIDS.name) ContentLevel.KIDS else ContentLevel.FAMILY
    }
    suspend fun setGuestLevel(level: ContentLevel) {
        store.edit { it[GUEST_LEVEL] = if (level == ContentLevel.KIDS) ContentLevel.KIDS.name else ContentLevel.FAMILY.name }
    }

    /** The latest time ever seen: a clock set back cannot end a lock early. SPEC: PRF-15 */
    suspend fun highWater(): Long = store.data.first()[HIGH_WATER] ?: 0L
    suspend fun setHighWater(epochMs: Long) { store.edit { it[HIGH_WATER] = epochMs } }

    suspend fun forget(id: String) {
        store.edit { s ->
            listOf("limits_", "lockout_", "used_").forEach { s.remove(stringPreferencesKey(it + id)) }
            s.remove(longPreferencesKey("override_$id"))
        }
    }

    private companion object {
        const val NAME = "profile_security"
        val ASK = booleanPreferencesKey("ask_on_launch")
        val GUEST = booleanPreferencesKey("guest_enabled")
        val GUEST_LEVEL = stringPreferencesKey("guest_level")
        val HIGH_WATER = longPreferencesKey("high_water")
    }
}
