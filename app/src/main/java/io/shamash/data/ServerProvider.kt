package io.shamash.data

import io.shamash.domain.ContentGate
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.RowSource
import io.shamash.domain.RowSourceProvider
import io.shamash.domain.RowSources
import io.shamash.domain.Shelf
import io.shamash.domain.TestResult
import io.shamash.domain.TestableProvider
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves a string resource (the add-on's own words for rows and test results). */
typealias Say = (Int) -> String

/**
 * What the add-ons that read the user's own server or feed share: on when the profile enables it, its rows chosen in
 * the settings panel (PEX-06), fetched at most every 2 h (PEX-07), each source within 8 s, the content level applied
 * to every card, nothing shown when anything fails. SPEC: PEX-06, PEX-07
 */
abstract class ServerProvider(
    final override val id: String,
    protected val say: Say,
    clock: () -> Long = System::currentTimeMillis,
    ttl: Long = 2 * Refresh.HOUR,
) : Provider, RowSourceProvider, TestableProvider {
    private val cache = TimedCache<String, List<Shelf>>(clock, Refresh.every(ttl))

    /** The shelves of the wanted sources, ids "<provider>:<source>". Called at most every 2 h per key. */
    protected abstract suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf>

    /** Anything besides the profile and the rows that should make the cached rows stale (e.g. the user's feed list). */
    protected open val salt: String get() = ""

    override fun isEnabled(profile: Profile) = id in profile.addons

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (!isEnabled(profile)) return emptyList()
        val wanted = RowSources.capped(rowSources()).filter { RowSources.isOn(profile, id, it) }
        if (wanted.isEmpty()) return emptyList()
        val key = listOf(profile.id, profile.contentLevel, wanted.joinToString(",") { it.id }, salt).joinToString("|")
        val shelves = softly("$id rows", emptyList()) { cache.get(key) { build(profile, wanted) } }
        return shelves.map { ContentGate.filter(it, profile.contentLevel) }.filter { it.items.isNotEmpty() }
    }

    override suspend fun featured(profile: Profile): List<MediaItem> =
        shelves(profile).flatMap { it.items }.filter { it.backdropUrl != null }.take(FEATURED_MAX)

    override suspend fun search(query: String, profile: Profile): List<MediaItem> = emptyList()

    /** One source's shelf: null (and a log line) when it fails or takes over 8 s. */
    protected suspend fun source(
        s: RowSource, style: io.shamash.domain.ShelfStyle, load: suspend () -> List<MediaItem>,
    ): Shelf? {
        val items = softly("$id ${s.id}", null as List<MediaItem>?) {
            withTimeoutOrNull(RowSources.FETCH_TIMEOUT_MS) { load() }
        } ?: return null
        if (items.isEmpty()) return null
        return Shelf(
            RowSources.shelfId(id, s.id), titleKey = "", style = style, items = items,
            title = s.titleText ?: s.titleRes?.let(say),
        )
    }

    protected fun failed(res: Int) = TestResult(false, say(res))

    companion object {
        const val FEATURED_MAX = 6
    }
}
