package io.shamash.data

import io.shamash.data.youtube.SecretStore
import io.shamash.domain.CredentialField
import io.shamash.domain.ProviderCredentials
import java.net.URI

/**
 * An add-on's credentials for the active profile, kept in the secret store as `secret_<profileId>_<addOnId>_<key>`
 * (the store adds `secret_`). On the TV only: not in the profile, not in backups, not in the HTTP cache, and never in
 * a log line. SPEC: PEX-03, PEX-04
 */
class SecretCredentials(
    private val store: SecretStore,
    private val addOnId: String,
    override val fields: List<CredentialField>,
    private val profileId: suspend () -> String,
) : ProviderCredentials {
    init {
        require(PART.matches(addOnId)) { "add-on id" }
        fields.forEach { require(PART.matches(it.key)) { "credential key" } }
    }

    private suspend fun name(key: String) = "${profileId()}_${addOnId}_$key"

    override suspend fun get(key: String): String? =
        if (fields.none { it.key == key }) null else store.secret(name(key))?.takeIf { it.isNotEmpty() }

    override suspend fun set(key: String, value: String?) {
        require(fields.any { it.key == key }) { "unknown credential key" }
        store.setSecret(name(key), value?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Whether every required field has a value. */
    suspend fun isComplete(): Boolean = fields.all { !it.required || get(it.key) != null }

    /** Forgets every value (Remove). */
    suspend fun clear() = fields.forEach { set(it.key, null) }

    override fun toString() = "SecretCredentials(addOn=$addOnId, fields=${fields.map { it.key }}, values=redacted)"

    private companion object {
        val PART = Regex("[A-Za-z0-9.-]+")
    }
}

/** What may be logged about an address that can carry credentials: the host only. SPEC: PEX-04 */
object Redact {
    fun host(url: String): String = runCatching { URI(url.trim()).host }.getOrNull() ?: "unknown host"
}
