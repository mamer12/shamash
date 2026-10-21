package io.shamash.data.espn

import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import io.shamash.domain.Team

/**
 * Reads ESPN's tennis scoreboard: each tournament's singles matches, two players (surname and seed under the flag)
 * and the games of each set ("6 3 7"). Doubles and anything else odd are skipped. SPEC: MC-08
 */
object EspnTennis {
    fun scoreboard(text: String, league: String, isMine: (String) -> Boolean): List<Match> {
        val (_, events) = EspnBoard.events(text, league)
        return events.flatMap { e -> e.competitions.mapNotNull { match(e, it, league, isMine) } }
    }

    private fun match(e: EspnBoard.Event, c: EspnBoard.Competition, league: String, isMine: (String) -> Boolean): Match? {
        val players = c.competitors.filter { it.athlete != null }.sortedBy { it.order }
        if (players.size != 2 || c.id.isBlank()) return null
        val status = c.status ?: e.status
        val state = EspnBoard.stateOf(status.type.state)
        val (a, b) = players
        return Match(
            id = c.id, competition = league, competitionName = e.name,
            home = player(a, state), away = player(b, state),
            startsAt = EspnBoard.millis(c.date.ifBlank { e.date }) ?: return null, state = state,
            clock = if (state == MatchState.PRE) "" else status.type.shortDetail,
            mine = isMine(a.athlete?.displayName.orEmpty()) || isMine(b.athlete?.displayName.orEmpty()),
            sport = Sport.TENNIS,
        )
    }

    private fun player(c: EspnBoard.Competitor, state: MatchState): Team {
        val athlete = c.athlete ?: EspnBoard.Athlete()
        val surname = athlete.displayName.trim().substringAfterLast(' ')
        val seed = c.seed?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val games = c.linescores.mapNotNull { it.value?.toInt() }.joinToString(" ")
        return Team(athlete.displayName, surname + seed, athlete.flag?.href.orEmpty(), if (state == MatchState.PRE) "" else games)
    }
}
