package io.shamash.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.shamash.domain.KeyMode
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/*
 * The profiles on this TV (PRF-01..03): a small `profiles` store (the list, the active id, the owner id, last used)
 * and one `profile_<id>` store per profile. The first start moves the v1 single profile into an OWNER profile
 * (PRF-17); if that fails the old store stays untouched and the app runs on it ("legacy mode").
 * Public surface: profiles, active, activeId, ownerId, current(), save, add, delete, setActive, MAX_PROFILES.
 * Secrets stay out of here (see Preferences.secret).
 */
class ProfileStore(
    private val stores: DataStores,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val meta get() = stores.store(META)
    private val legacy get() = stores.store(LEGACY)
    private val lock = Mutex()
    @Volatile private var ready = false
    @Volatile private var legacyMode = false

    /** The profile in use now; follows a switch. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<Profile> = flow {
        ensureMigrated()
        if (legacyMode) emitAll(legacy.data.map { ProfileCodec.read(it) })
        else emitAll(
            meta.data.map { it[ACTIVE] }.distinctUntilChanged().flatMapLatest { id ->
                if (id == null) flow { } else stores.store(profileStore(id)).data.map { ProfileCodec.read(it, id) }
            },
        )
    }

    /** Every profile, the owner first, then in the order they were added. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profiles: Flow<List<Profile>> = flow {
        ensureMigrated()
        if (legacyMode) emitAll(legacy.data.map { listOf(ProfileCodec.read(it)) })
        else emitAll(
            meta.data.map { ids(it) }.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) flow { emit(emptyList()) }
                else combine(ids.map { id ->
                    stores.store(profileStore(id)).data.map { ProfileCodec.read(it, id) }
                }) { it.toList() }
            },
        )
    }

    val activeId: Flow<String?> = flow {
        ensureMigrated()
        emitAll(meta.data.map { it[ACTIVE] }.distinctUntilChanged())
    }

    suspend fun current(): Profile = active.first()

    suspend fun ownerId(): String? { ensureMigrated(); return meta.data.first()[OWNER] }

    /** Saves [p] under its id (a blank or unknown id means the active profile). The owner stays an ADULT. SPEC: PRF-03 */
    suspend fun save(p: Profile) {
        ensureMigrated()
        if (legacyMode) { legacy.edit { ProfileCodec.write(it, p) }; return }
        val state = meta.data.first()
        val id = p.id.takeIf { it in ids(state) } ?: state[ACTIVE] ?: return
        val safe = if (id == state[OWNER]) p.copy(kind = ProfileKind.ADULT) else p
        stores.store(profileStore(id)).edit { ProfileCodec.write(it, safe) }
    }

    /** Adds a profile with a fresh id; null at [MAX_PROFILES]. There is never a second owner. SPEC: PRF-01, PRF-03 */
    suspend fun add(p: Profile): Profile? {
        ensureMigrated()
        if (legacyMode) return null
        return lock.withLock {
            val state = meta.data.first()
            if (ids(state).size >= MAX_PROFILES) return@withLock null
            val created = p.copy(id = newId())
            stores.store(profileStore(created.id)).edit { ProfileCodec.write(it, created) }
            meta.edit { it[LIST] = (ids(it) + created.id).joinToString(SEP) }
            created
        }
    }

    /** Removes a profile and its store; the owner cannot be deleted. Deleting the active one activates the owner. */
    suspend fun delete(id: String): Boolean {
        ensureMigrated()
        if (legacyMode) return false
        return lock.withLock {
            val state = meta.data.first()
            if (id == state[OWNER] || id !in ids(state)) return@withLock false
            meta.edit {
                it[LIST] = (ids(it) - id).joinToString(SEP)
                if (it[ACTIVE] == id) it[ACTIVE] = it[OWNER].orEmpty()
                it.remove(lastUsedKey(id))
            }
            stores.store(profileStore(id)).edit { it.clear() }
            true
        }
    }

    suspend fun setActive(id: String): Boolean {
        ensureMigrated()
        if (legacyMode) return false
        val now = clock()
        return lock.withLock {
            if (id !in ids(meta.data.first())) return@withLock false
            meta.edit { it[ACTIVE] = id; it[lastUsedKey(id)] = now }
            true
        }
    }

    suspend fun lastUsed(id: String): Long? { ensureMigrated(); return meta.data.first()[lastUsedKey(id)] }

    /** True when the v1 profile could not be moved and the old store is in use (PRF-17). */
    suspend fun isLegacyMode(): Boolean { ensureMigrated(); return legacyMode }

    private suspend fun ensureMigrated() {
        if (ready) return
        lock.withLock {
            if (ready) return
            try {
                migrate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w("profiles migration, staying on the old store", e)
                legacyMode = true
            }
            ready = true
        }
    }

    /** v1 -> owner profile; the old keys go only after the new store reads back equal. SPEC: PRF-17 */
    private suspend fun migrate() {
        val state = meta.data.first()
        if (state[MIGRATED] != true || ids(state).isEmpty()) {
            val id = state[OWNER] ?: newId()
            val old = ProfileCodec.read(legacy.data.first(), id).copy(kind = ProfileKind.ADULT, keyMode = KeyMode.SHARED)
            stores.store(profileStore(id)).edit { ProfileCodec.write(it, old) }
            check(ProfileCodec.read(stores.store(profileStore(id)).data.first(), id) == old) { "read back differs" }
            meta.edit {
                it[LIST] = id; it[ACTIVE] = id; it[OWNER] = id; it[lastUsedKey(id)] = clock(); it[MIGRATED] = true
            }
        }
        if (ProfileCodec.hasLegacyKeys(legacy.data.first())) legacy.edit { ProfileCodec.clearLegacy(it) }
    }

    private fun ids(s: Preferences): List<String> = s[LIST].orEmpty().split(SEP).filter { it.isNotBlank() }

    private fun profileStore(id: String) = "profile_$id"
    private fun lastUsedKey(id: String) = longPreferencesKey("last_used_$id")

    companion object {
        const val MAX_PROFILES = 6
        const val LEGACY = "profile"
        private const val META = "profiles"
        private const val SEP = ","
        private val LIST = stringPreferencesKey("list")
        private val ACTIVE = stringPreferencesKey("active")
        private val OWNER = stringPreferencesKey("owner")
        private val MIGRATED = booleanPreferencesKey("migrated")
    }
}
