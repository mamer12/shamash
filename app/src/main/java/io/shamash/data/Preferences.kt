package io.shamash.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.shamash.domain.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * The active profile and small secrets, on the TV only. The profile comes from [profiles] (PRF-02); secrets stay in the
 * original store under `secret_<key>` (the shared namespace, kept so existing sign-ins and the mode survive).
 * SPEC: ONB-06, ARCH-06, PRF-02
 */
class Preferences(stores: DataStores, val profiles: ProfileStore = ProfileStore(stores)) {
    private val secrets = stores.store(ProfileStore.LEGACY)

    constructor(context: Context) : this(
        DataStores(File(context.filesDir, "datastore"), CoroutineScope(SupervisorJob() + Dispatchers.IO)),
    )

    /** The active profile; follows a profile switch. */
    val profile: Flow<Profile> = profiles.active

    suspend fun current(): Profile = profile.first()

    suspend fun save(p: Profile) = profiles.save(p)

    /** A small secret or value outside the profile (YouTube sign-in, phone PIN…). */
    suspend fun secret(key: String): String? = secrets.data.first()[stringPreferencesKey("secret_$key")]

    suspend fun setSecret(key: String, value: String?) {
        secrets.edit { s ->
            val k = stringPreferencesKey("secret_$key")
            if (value == null) s.remove(k) else s[k] = value
        }
    }
}
