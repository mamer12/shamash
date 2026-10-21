package io.shamash.domain

/*
 * The add-on contract: a provider that is off until the profile enables it, listed by one registration line, with the
 * credentials it needs described as data. Public surface: AddOn, Deps, CredentialField, CredentialKind,
 * ProviderCredentials. SPEC: PEX-01..03
 */

/** What a provider needs from the app to be built; the data layer implements it. */
interface Deps {
    val credentials: ProviderCredentials
    fun isInstalled(appPackage: String): Boolean
    suspend fun profile(): Profile

    /** A shared service by type (HTTP client, context…); null when the app doesn't offer it. */
    fun <T : Any> service(type: Class<T>): T?
}

/** One add-on. [create] gives null when it isn't configured (fail soft). SPEC: PEX-01 */
class AddOn(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val requires: List<CredentialField> = emptyList(),
    val create: (Deps) -> Provider?,
)

enum class CredentialKind { URL, TEXT, SECRET, TOKEN }

/** One value an add-on asks for. SPEC: PEX-03 */
data class CredentialField(
    val key: String,
    val labelRes: Int,
    val kind: CredentialKind = CredentialKind.TEXT,
    val required: Boolean = true,
    val hintRes: Int? = null,
)

/**
 * An add-on's stored credentials. Backed by the `secret_` store (PRF-09). Reading never logs values and `toString()`
 * of an implementation must redact them. SPEC: PEX-03, PEX-04
 */
interface ProviderCredentials {
    val fields: List<CredentialField>
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String?)
}
