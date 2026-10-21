package io.shamash.domain

/*
 * What an add-on offers beyond its credentials: the rows it can show (PEX-06) and a connection test (PEX-05). Both are
 * optional interfaces a Provider also implements, so the Provider contract itself stays as it is. Row on/off and
 * renames are saved as the profile's [RowPref]s, keyed "<providerId>:<sourceId>" (LAY-01).
 * Public surface: RowSource, RowSourceProvider, TestableProvider, TestResult, RowSources. SPEC: PEX-05, PEX-06
 */

/** One row an add-on can offer. Either [titleRes] or [titleText] names it. */
data class RowSource(
    val id: String,
    val titleRes: Int? = null,
    val titleText: String? = null,
    val defaultOn: Boolean = true,
) {
    init {
        require(id.isNotBlank() && ':' !in id) { "row source id" }
        require(titleRes != null || !titleText.isNullOrBlank()) { "row source needs a title" }
    }
}

/** A provider that lists the rows it can offer (at most [RowSources.MAX]). */
interface RowSourceProvider {
    fun rowSources(): List<RowSource>
}

/** The outcome of "Test connection": [message] is human, and never echoes a credential. */
data class TestResult(val ok: Boolean, val message: String)

/** A provider that can check its own connection with the stored credentials. */
interface TestableProvider {
    suspend fun test(): TestResult
}

object RowSources {
    const val MAX = 12
    const val FETCH_TIMEOUT_MS = 8_000L

    /** The id a source's shelf has on Home. */
    fun shelfId(providerId: String, sourceId: String) = "$providerId:$sourceId"

    /** What a provider may offer: the first [MAX]. */
    fun capped(sources: List<RowSource>): List<RowSource> = sources.distinctBy { it.id }.take(MAX)

    private fun pref(p: Profile, providerId: String, s: RowSource) =
        LayoutOps.prefFor(shelfId(providerId, s.id), p.layout.rows)

    /** Hidden with no saved choice: off by default, or a preset that hides the rows it doesn't name (LAY-07). */
    private fun hiddenByDefault(p: Profile, s: RowSource) = !s.defaultOn || p.layout.hideRest

    /** Whether the row is shown, so fetched: a hidden row is never fetched. SPEC: PEX-06, LAY-04 */
    fun isOn(p: Profile, providerId: String, s: RowSource): Boolean =
        pref(p, providerId, s)?.let { !it.hidden } ?: !hiddenByDefault(p, s)

    /** The user's name for the row, or null for the default title. */
    fun customTitle(p: Profile, providerId: String, s: RowSource): String? = pref(p, providerId, s)?.title

    fun withOn(p: Profile, providerId: String, s: RowSource, on: Boolean): Profile =
        update(p, providerId, s) { it.copy(hidden = !on) }

    fun withTitle(p: Profile, providerId: String, s: RowSource, title: String?): Profile =
        update(p, providerId, s) { it.copy(title = title?.takeIf { it.isNotBlank() }) }

    /** Forgets every saved choice of a provider's rows (Remove). */
    fun cleared(p: Profile, providerId: String): Profile =
        p.copy(layout = p.layout.copy(rows = p.layout.rows.filterNot { it.rowId.startsWith("$providerId:") }))

    private fun update(p: Profile, providerId: String, s: RowSource, change: (RowPref) -> RowPref): Profile {
        val id = shelfId(providerId, s.id)
        val current = p.layout.rows.firstOrNull { it.rowId == id } ?: RowPref(id, hidden = !isOn(p, providerId, s))
        val next = change(current)
        val family = LayoutOps.prefFor(id, p.layout.rows.filterNot { it.rowId == id })   // a preset's `prefix*` pref
        val fallback = family?.hidden ?: hiddenByDefault(p, s)
        val plain = next.hidden == fallback && !next.pinned && next.title == null && next.order == 0
        val rows = p.layout.rows.filterNot { it.rowId == id } + if (plain) emptyList() else listOf(next)
        return p.copy(layout = p.layout.copy(rows = rows))
    }
}
