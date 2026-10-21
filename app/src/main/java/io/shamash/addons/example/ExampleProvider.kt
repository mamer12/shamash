package io.shamash.addons.example

import io.shamash.R
import io.shamash.data.HttpPolicy
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.ServerUrl
import io.shamash.domain.Profile
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl

/**
 * The template provider: reads one JSON catalog from the address the user typed, with an optional bearer token.
 * [ServerProvider] does the shared work: on only when the profile enables it, the rows chosen in settings, a 2 h cache,
 * 8 s per row, the ContentGate on every card, nothing shown when anything fails. Requests go through [ServerHttp], so
 * they are tagged private (never in the HTTP cache) and errors name the host only. SPEC: PLT-05, PEX-04..07
 */
class ExampleProvider(
    private val credentials: ProviderCredentials,
    private val server: ServerHttp,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ExampleAddOn.ID, say, clock) {
    override fun rowSources(): List<RowSource> = ExampleAddOn.rows

    /** https always; http only on the home network. Null when nothing usable is saved. */
    private suspend fun address(): HttpUrl? =
        ServerUrl.parse(credentials.get(ExampleAddOn.URL).orEmpty(), HttpPolicy.PRIVATE_ONLY)

    private suspend fun headers(): Map<String, String> =
        credentials.get(ExampleAddOn.TOKEN)?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    /** The whole catalog, or null when no address is saved; throws when it can't be read (callers stay soft). */
    suspend fun catalog(): ExampleCatalog? {
        val url = address() ?: return null
        val root = server.json(url, headers(), TIMEOUT_MS, MAX_BYTES)
        return ExampleCatalog.parse(root, rowSources().map { it.id })
    }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val catalog = catalog() ?: return emptyList()
        return wanted.mapNotNull { s -> source(s, style(s)) { catalog.row(s.id) } }
    }

    /** "Test connection": a human message that never echoes the address or the token. SPEC: PEX-05 */
    override suspend fun test(): TestResult {
        if (address() == null) return failed(R.string.example_test_bad_address)
        return try {
            val count = catalog()?.size ?: 0
            if (count == 0) failed(R.string.example_test_empty) else TestResult(true, say(R.string.example_test_ok).format(count))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(R.string.example_test_failed)
        }
    }

    private fun style(s: RowSource) = if (s.id == "popular") ShelfStyle.POSTER else ShelfStyle.WIDE

    private companion object {
        const val TIMEOUT_MS = 6_000L
        const val MAX_BYTES = 1024L * 1024
    }
}
