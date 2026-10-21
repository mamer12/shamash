package io.shamash.feature.matchcentre

import io.shamash.data.json
import io.shamash.domain.Match
import kotlinx.serialization.Serializable

/**
 * Where to watch: per competition and region, the apps that show it, best first (`*` = any region). It is data: a
 * small JSON asset the user can override with a file of the same shape. SPEC: MC-10
 */
@Serializable
data class BroadcastMap(
    val default: Map<String, List<String>> = emptyMap(),
    val competitions: Map<String, Map<String, List<String>>> = emptyMap(),
) {
    /** The apps for a competition in a region: region's own first, then "any region", then the map's default. */
    fun appsFor(competition: String, region: String): List<String> {
        val byRegion = competitions[competition].orEmpty()
        val r = region.uppercase()
        return (byRegion[r].orEmpty() + byRegion["*"].orEmpty() + default[r].orEmpty() + default["*"].orEmpty()).distinct()
    }

    /**
     * The app the card opens: the first listed app that is installed and subscribed, else the first installed one,
     * else null (the card says there is no app). SPEC: MC-10
     */
    fun choose(match: Match, region: String, subscriptions: Set<String>, installed: Set<String>): String? {
        val listed = appsFor(match.competition, region).filter { it in installed }
        return listed.firstOrNull { it in subscriptions } ?: listed.firstOrNull()
    }

    /** [override] wins over this map where it names a competition. */
    fun overriddenBy(override: BroadcastMap) = BroadcastMap(
        default = default + override.default,
        competitions = competitions + override.competitions,
    )

    companion object {
        /** Unreadable text is an empty map (fail soft). */
        fun parse(text: String?): BroadcastMap =
            runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrDefault(BroadcastMap())
    }
}
