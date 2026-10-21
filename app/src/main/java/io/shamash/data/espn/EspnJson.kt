package io.shamash.data.espn

import io.shamash.data.json
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Team
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** Reads ESPN's soccer scoreboard. SPEC: PRV-11 */
object EspnJson {
    @Serializable private data class Board(val leagues: List<League> = emptyList(), val events: List<Event> = emptyList())
    @Serializable private data class League(val name: String = "")
    @Serializable
    private data class Event(
        val id: String,
        val date: String,
        val status: Status,
        val competitions: List<Competition> = emptyList(),
    )
    @Serializable private data class Status(val displayClock: String = "", val type: StatusType)
    @Serializable private data class StatusType(val state: String = "pre", val shortDetail: String = "")
    @Serializable private data class Competition(val competitors: List<Competitor> = emptyList())
    @Serializable private data class Competitor(val homeAway: String = "", val score: String? = null, val team: EspnTeam)

    @Serializable
    private data class EspnTeam(val displayName: String = "", val shortDisplayName: String? = null, val logo: String = "")

    /** The board's matches for [league]; [isMine] says whether a team is one of the user's. */
    fun scoreboard(text: String, league: String, isMine: (String) -> Boolean): List<Match> {
        val board = json.decodeFromString<Board>(text)
        val leagueName = board.leagues.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: league
        return board.events.mapNotNull { it.toMatch(league, leagueName, isMine) }
    }

    private fun Event.toMatch(league: String, leagueName: String, isMine: (String) -> Boolean): Match? {
        val sides = competitions.firstOrNull()?.competitors ?: return null
        val home = sides.firstOrNull { it.homeAway == "home" } ?: return null
        val away = sides.firstOrNull { it.homeAway == "away" } ?: return null
        val state = stateOf(status.type.state)
        return Match(
            id = id,
            competition = league,
            competitionName = leagueName,
            home = home.toTeam(state),
            away = away.toTeam(state),
            startsAt = OffsetDateTime.parse(date).toInstant().toEpochMilli(),
            state = state,
            clock = clockOf(state),
            mine = isMine(home.team.displayName) || isMine(away.team.displayName),
        )
    }

    /** "67'" while playing, "HT" at half time, "FT" (or "AET", "FT-Pens") once over. */
    private fun Event.clockOf(state: MatchState) = when (state) {
        MatchState.PRE -> ""
        MatchState.LIVE -> if (status.type.shortDetail == "HT") "HT" else status.displayClock
        MatchState.DONE -> status.type.shortDetail
    }

    private fun Competitor.toTeam(state: MatchState) = Team(
        name = team.displayName,
        shortName = team.shortDisplayName ?: team.displayName,
        logoUrl = team.logo,
        score = if (state == MatchState.PRE) "" else score.orEmpty(),
    )

    private fun stateOf(espn: String) = when (espn) {
        "in" -> MatchState.LIVE
        "post" -> MatchState.DONE
        else -> MatchState.PRE
    }
}
