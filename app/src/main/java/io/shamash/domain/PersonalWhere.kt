package io.shamash.domain

/*
 * Where to watch, pure: a title's offers in the profile's region ranked by what the user has. Subscribed and
 * installed first; then Flatrate > Free/Ads > Rent > Buy; ties by the provider weight. Public surface: OfferType,
 * WatchOffer, RankedOffer, WhereToWatch. SPEC: PER-09, PER-10
 */
enum class OfferType { FLATRATE, FREE, RENT, BUY }

data class WatchOffer(val appPackage: String, val name: String, val type: OfferType)

/** [inYourApps] is false for a service the user doesn't subscribe to or hasn't installed ("Not in your apps"). */
data class RankedOffer(val offer: WatchOffer, val inYourApps: Boolean)

object WhereToWatch {
    const val MAX = 6

    /** Every service once (its best offer), best first, at most [MAX]. */
    fun rank(
        offers: List<WatchOffer>,
        subscriptions: Set<String>,
        installed: (String) -> Boolean,
        weight: (String) -> Double = { 1.0 },
    ): List<RankedOffer> {
        val best = offers.groupBy { it.appPackage }.values.map { group -> group.minBy { it.type.ordinal } }
        return best.map { RankedOffer(it, it.appPackage in subscriptions && installed(it.appPackage)) }
            .sortedWith(
                compareBy<RankedOffer> { !it.inYourApps }
                    .thenBy { it.offer.type.ordinal }
                    .thenByDescending { weight(it.offer.appPackage) }
                    .thenBy { it.offer.name },
            )
            .take(MAX)
    }

    /** The "Open" target: the best of [rank], which carries the "Not in your apps" flag when nothing of the user's matches. */
    fun best(ranked: List<RankedOffer>): RankedOffer? = ranked.firstOrNull()
}
