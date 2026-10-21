package io.shamash.domain

import java.util.Locale

/*
 * Learned order, pure: provider and row weights from the last 30 days, shelf and carousel order from them, and the
 * genres finished in the same part of the day. Pinned and user-ordered rows are never touched (the caller passes only
 * rows with no user order). Public surface: Weights. SPEC: PER-03, PER-05, PER-16
 */
object Weights {
    const val WINDOW_MS = 30L * 86_400_000L
    const val MIN_SIGNALS = 5
    private const val LOW = 0.5
    private const val HIGH = 2.0

    /** `clamp(1 + 0.15*finishes - 0.1*skips, 0.5, 2.0)`. SPEC: PER-05 */
    fun weight(finishes: Int, skips: Int): Double = (1.0 + 0.15 * finishes - 0.1 * skips).coerceIn(LOW, HIGH)

    private fun recent(signals: List<Signal>, now: Long) = signals.filter { now - it.at in 0..WINDOW_MS }

    private fun of(signals: List<Signal>): Double =
        weight(signals.count { it.kind == SignalKind.FINISH }, signals.count { it.kind == SignalKind.SKIP })

    /** Nothing is weighted with fewer than five signals. SPEC: PER-16 */
    fun learning(signals: List<Signal>, enabled: Boolean = true): Boolean = enabled && signals.size >= MIN_SIGNALS

    fun providerWeight(signals: List<Signal>, providerId: String, now: Long): Double =
        of(recent(signals, now).filter { it.providerId == providerId })

    /** How much the user finishes in an app, for ties in "where to watch". SPEC: PER-09 */
    fun appWeight(signals: List<Signal>, appPackage: String, now: Long): Double =
        if (signals.size < MIN_SIGNALS) 1.0 else of(recent(signals, now).filter { it.appPackage == appPackage })

    /** A row's weight: its provider's, times what was finished or skipped among its own titles. SPEC: PER-05 */
    fun shelfWeight(shelf: Shelf, signals: List<Signal>, now: Long): Double {
        val keys = shelf.items.map(ItemKeys::of).toSet()
        val provider = shelf.items.groupingBy { it.providerId }.eachCount().maxByOrNull { it.value }?.key
        val own = of(recent(signals, now).filter { it.itemKey in keys })
        return (provider?.let { providerWeight(signals, it, now) } ?: 1.0) * own
    }

    /** [shelves] (rows with no user order) heaviest first; equal weights keep their order. SPEC: PER-05 */
    fun orderShelves(shelves: List<Shelf>, signals: List<Signal>, now: Long, enabled: Boolean = true): List<Shelf> {
        if (!learning(signals, enabled)) return shelves
        return shelves.sortedByDescending { shelfWeight(it, signals, now) }
    }

    /** The carousel's provider turns, heaviest first. SPEC: PER-05, LAY-12 */
    fun orderProviders(order: List<String>, signals: List<Signal>, now: Long, enabled: Boolean = true): List<String> {
        if (!learning(signals, enabled)) return order
        return order.sortedByDescending { providerWeight(signals, it, now) }
    }

    /** Genres finished in [moment] (most often first); every finished genre when that moment has none. SPEC: PER-03 */
    fun momentGenres(signals: List<Signal>, moment: Moment): List<String> {
        val finished = signals.filter { it.kind == SignalKind.FINISH }
        val same = finished.filter { Moment.at(it.hourOfDay) == moment }
        return ranked((same.ifEmpty { finished }).flatMap { it.genres })
    }

    /** [candidates] with the most genres in [wanted] first; the first one when none overlap. */
    fun pick(candidates: List<MediaItem>, wanted: List<String>): MediaItem? {
        if (wanted.isEmpty()) return candidates.firstOrNull()
        val set = wanted.map(::norm).toSet()
        return candidates.maxByOrNull { c -> c.genres.count { norm(it) in set } }?.takeIf { c -> c.genres.any { norm(it) in set } }
            ?: candidates.firstOrNull()
    }

    private fun norm(g: String) = g.trim().lowercase(Locale.ROOT)

    private fun ranked(genres: List<String>): List<String> =
        genres.map(::norm).filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
}
