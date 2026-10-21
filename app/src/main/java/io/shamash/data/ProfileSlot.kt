package io.shamash.data

import io.shamash.data.youtube.SecretStore
import kotlinx.serialization.KSerializer

/**
 * One small JSON value per profile in the on-TV store (`<name>_<profileId>`): an add-on's lists and choices that are
 * not credentials (subscriptions, favorites, follows). Unreadable reads as [empty]; [empty] is stored as nothing.
 * SPEC: PEX-23, PEX-24, PEX-25, PEX-26, PEX-27
 */
class ProfileSlot<T>(
    private val store: SecretStore,
    private val name: String,
    private val serializer: KSerializer<T>,
    private val empty: T,
    private val profileId: suspend () -> String,
) {
    private suspend fun key() = "${name}_${profileId()}"

    suspend fun get(): T = runCatching {
        store.secret(key())?.takeIf { it.isNotEmpty() }?.let { json.decodeFromString(serializer, it) }
    }.getOrNull() ?: empty

    suspend fun set(value: T) {
        store.setSecret(key(), if (value == empty) null else json.encodeToString(serializer, value))
    }

    suspend fun update(change: (T) -> T): T = change(get()).also { set(it) }

    suspend fun clear() = store.setSecret(key(), null)
}

/** The app's preferences as the secret store add-ons keep their per-profile data in. */
fun Preferences.asSecretStore(): SecretStore = object : SecretStore {
    override suspend fun secret(key: String) = this@asSecretStore.secret(key)
    override suspend fun setSecret(key: String, value: String?) = this@asSecretStore.setSecret(key, value)
}
