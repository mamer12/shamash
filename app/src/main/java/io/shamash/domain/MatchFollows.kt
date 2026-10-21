package io.shamash.domain

/** What a profile follows: teams (the old `teams` set too), players and whole sports. SPEC: MC-09 */
object MatchFollows {
    const val TEAM = "team"
    const val PLAYER = "player"
    const val SPORT = "sport"

    /** Every follow, the legacy team names included (same set, so old profiles keep working). */
    fun all(profile: Profile): Set<Follow> =
        profile.follows + profile.teams.filter { it.isNotBlank() }.map { Follow(TEAM, it.trim()) }

    /** True for a team or player name the profile follows. */
    fun mine(profile: Profile): (String) -> Boolean {
        val names = all(profile).filter { it.kind == TEAM || it.kind == PLAYER }.map { it.name.trim() }
            .filter { it.isNotEmpty() }
        return { name -> names.any { name.contains(it, ignoreCase = true) } }
    }

    fun sportFollowed(profile: Profile, sport: Sport): Boolean =
        profile.follows.any { it.kind == SPORT && sportOf(it.name) == sport }

    /** The follows a match touches; empty when it is not the profile's. */
    fun matching(profile: Profile, match: Match): List<Follow> = all(profile).filter { f ->
        when (f.kind) {
            SPORT -> sportOf(f.name) == match.sport
            TEAM, PLAYER -> f.name.isNotBlank() &&
                listOf(match.home.name, match.away.name).any { it.contains(f.name.trim(), ignoreCase = true) }
            else -> false
        }
    }

    /** "F1", "Formula 1", "tennis"... */
    fun sportOf(name: String): Sport? {
        val n = name.trim().lowercase().replace(" ", "")
        return when (n) {
            "f1", "formula1", "formulaone" -> Sport.F1
            "tennis" -> Sport.TENNIS
            "nba", "basketball" -> Sport.BASKETBALL
            "cricket" -> Sport.CRICKET
            "soccer", "football" -> Sport.SOCCER
            else -> null
        }
    }
}
