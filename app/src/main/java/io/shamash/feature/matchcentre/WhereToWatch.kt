package io.shamash.feature.matchcentre

import io.shamash.domain.Carousel
import io.shamash.domain.Match
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.TvApp

/**
 * Where each match is watched for one profile on this TV: the [BroadcastMap]'s choice among the installed apps the
 * profile subscribes to. Before the TV's apps are known (none listed yet) the map's first app stands in, so cards
 * don't flash "no app" while the list loads. Pure. SPEC: MC-10
 */
class WhereToWatch(
    private val map: BroadcastMap,
    private val region: String,
    private val subscriptions: Set<String>,
    private val installed: Set<String>,
) {
    /** The app this match opens, or null: no app on the TV shows it. */
    fun app(match: Match): String? =
        if (installed.isEmpty()) map.appsFor(match.competition, region).firstOrNull()
        else map.choose(match, region, subscriptions, installed)

    /** The match as a card or billboard item that opens [app]. */
    fun item(match: Match): MediaItem = Carousel.matchItem(match, app(match))

    companion object {
        fun of(map: BroadcastMap, profile: Profile, installed: Collection<TvApp>) = WhereToWatch(
            map, profile.region, profile.subscriptions, installed.mapTo(HashSet()) { it.packageName },
        )

        /** The name an overlay says: the TV's label for [pkg], its package when unlabeled, null when there's no app. */
        fun label(pkg: String?, apps: Collection<TvApp>): String? =
            pkg?.let { p -> apps.firstOrNull { it.packageName == p }?.label?.takeIf { it.isNotBlank() } ?: p }
    }
}
