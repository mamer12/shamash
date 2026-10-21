package io.shamash.feature.personalization

import io.shamash.data.Preferences
import io.shamash.data.softly
import io.shamash.data.tmdb.TmdbClient
import io.shamash.domain.OpenTarget
import io.shamash.domain.RankedOffer
import io.shamash.domain.Weights
import io.shamash.domain.WhereToWatch

/**
 * Chooses where a TMDB title opens: its offers in the profile's region, ranked by what the user subscribes to and
 * has installed (WhereToWatch). No key, no answer or an error gives null, and the title opens as it did. SPEC: PER-09, PER-10
 */
class WherePicker(
    private val tmdb: TmdbClient,
    private val prefs: Preferences,
    private val installed: (String) -> Boolean,
    private val personal: Personalization,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun ranked(target: OpenTarget.Lookup): List<RankedOffer> = softly("where to watch", emptyList()) {
        val p = prefs.current()
        if (!p.hasTmdb) return@softly emptyList()
        val signals = personal.state.value.data.signals
        val now = clock()
        WhereToWatch.rank(
            tmdb.offers(p.tmdbKey, target.kind, target.tmdbId, p.region), p.subscriptions, installed,
        ) { Weights.appWeight(signals, it, now) }
    }

    /** The title with its best app, or null to keep the title's own. */
    suspend fun pick(target: OpenTarget.Lookup): OpenTarget.Lookup? {
        val best = WhereToWatch.best(ranked(target)) ?: return null
        return target.copy(appPackage = best.offer.appPackage)
    }
}
