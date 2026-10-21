package io.shamash.feature.matchcentre

import io.shamash.domain.CompetitionDef
import io.shamash.domain.Competitions
import io.shamash.domain.Follow
import io.shamash.domain.MatchFollows
import io.shamash.domain.Profile
import io.shamash.domain.Sport

/** The Match Centre settings' edits, pure: follows, competitions by sport, quiet hours. SPEC: MC-07, MC-09, MC-11, MC-12 */
object MatchSettingsEdits {
    const val QUIET_STEP_MIN = 30
    private const val DAY_MIN = 24 * 60

    /** The profile's teams and players as one comma-separated line, for a text field. */
    fun followsText(p: Profile): String = (p.teams + p.follows.filter { it.kind == MatchFollows.PLAYER }.map { it.name })
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

    /**
     * The typed names become the teams, except names already followed as players, which stay players (their alert
     * level is kept, MC-11); sport follows are kept.
     */
    fun withFollowsText(p: Profile, text: String): Profile {
        val names = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val players = p.follows.filter { it.kind == MatchFollows.PLAYER }.associateBy { it.name.trim().lowercase() }
        val (kept, teams) = names.partition { it.lowercase() in players }
        return p.copy(
            teams = teams.toSet(),
            follows = p.follows.filter { it.kind == MatchFollows.SPORT }.toSet() + kept.map { players.getValue(it.lowercase()) },
        )
    }

    /** The name a whole-sport follow is saved under (one [MatchFollows.sportOf] reads back). */
    fun sportName(sport: Sport): String = when (sport) {
        Sport.SOCCER -> "football"
        Sport.F1 -> "F1"
        Sport.TENNIS -> "tennis"
        Sport.BASKETBALL -> "basketball"
        Sport.CRICKET -> "cricket"
    }

    fun followsSport(p: Profile, sport: Sport): Boolean = MatchFollows.sportFollowed(p, sport)

    /** Follows (or stops following) a whole sport; any other spelling of the same sport goes too. */
    fun withSport(p: Profile, sport: Sport, on: Boolean): Profile {
        val others = p.follows.filterNot { it.kind == MatchFollows.SPORT && MatchFollows.sportOf(it.name) == sport }.toSet()
        return p.copy(follows = if (on) others + Follow(MatchFollows.SPORT, sportName(sport)) else others)
    }

    /** The catalog's competitions by sport, in the sports' order, each sport once. SPEC: MC-07 */
    fun groups(): List<Pair<Sport, List<CompetitionDef>>> {
        val grouped = Competitions.grouped()
        return Sport.entries.mapNotNull { s -> grouped[s]?.takeIf { it.isNotEmpty() }?.let { s to it } }
    }

    /** One sport's chips changed: the others (and codes not in the catalog, from older profiles) stay as they were. */
    fun withGroup(picked: Set<String>, group: List<CompetitionDef>, chosen: Set<String>): Set<String> {
        val ids = group.mapTo(HashSet()) { it.id }
        return (picked - ids) + chosen.filter { it in ids }
    }

    /** The follows whose alert level can be set, in a stable order (sports first, then by name). SPEC: MC-11 */
    fun alertFollows(p: Profile): List<Follow> = MatchFollows.all(p)
        .distinctBy { AlertSettings.key(it) }
        .sortedWith(compareBy({ it.kind != MatchFollows.SPORT }, { it.name.lowercase() }))

    /** A quiet-hours edge moved by [steps] of [QUIET_STEP_MIN], wrapping around midnight. SPEC: MC-12 */
    fun shift(minute: Int, steps: Int): Int = Math.floorMod(minute + steps * QUIET_STEP_MIN, DAY_MIN)

    /** A weekday (1 = Monday .. 7 = Sunday) turned on or off; none on means every day. SPEC: MC-12 */
    fun toggleDay(days: Set<Int>, day: Int): Set<Int> = if (day in days) days - day else days + day

    /** "07:30" for minute 450. */
    fun clock(minute: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
}
