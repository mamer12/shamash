package io.shamash.data.espn

import io.shamash.data.json
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import io.shamash.domain.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.OffsetDateTime

/** ESPN's scoreboard shapes shared by the sport parsers. An event that doesn't fit is skipped, never fatal. SPEC: MC-08 */
internal object EspnBoard {
    @Serializable data class Status(val displayClock: String = "", val type: StatusType = StatusType())
    @Serializable data class StatusType(val state: String = "pre", val shortDetail: String = "")
    @Serializable data class Flag(val href: String = "")
    @Serializable data class Athlete(val displayName: String = "", val shortName: String = "", val flag: Flag? = null)
    @Serializable data class Team(val displayName: String = "", val shortDisplayName: String? = null, val logo: String = "")
    @Serializable data class Line(
        val value: Double? = null,
        val runs: Double? = null,
        val wickets: Double? = null,
        val overs: Double? = null,
    )
    @Serializable data class SessionType(val abbreviation: String = "", val text: String = "")
    @Serializable
    data class Competitor(
        val homeAway: String = "",
        val order: Int = 0,
        val score: String? = null,
        val winner: Boolean? = null,
        val seed: String? = null,
        val team: Team? = null,
        val athlete: Athlete? = null,
        val linescores: List<Line> = emptyList(),
    )
    @Serializable
    data class Competition(
        val id: String = "",
        val date: String = "",
        val type: SessionType? = null,
        val status: Status? = null,
        val competitors: List<Competitor> = emptyList(),
    )
    @Serializable
    data class Event(
        val id: String = "",
        val name: String = "",
        val shortName: String? = null,
        val date: String = "",
        val status: Status = Status(),
        val competitions: List<Competition> = emptyList(),
    )

    /** The league's name and the board's events, each decoded on its own. */
    fun events(text: String, fallbackName: String): Pair<String, List<Event>> {
        val root = json.parseToJsonElement(text).jsonObject
        val name = root["leagues"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.let(::plain)
            ?.takeIf { it.isNotBlank() } ?: fallbackName
        val events = root["events"]?.jsonArray.orEmpty().mapNotNull { e ->
            runCatching { json.decodeFromJsonElement(Event.serializer(), e) }.getOrNull()
        }
        return name to events
    }

    private fun plain(e: JsonElement): String = (e as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

    fun stateOf(espn: String) = when (espn) {
        "in" -> MatchState.LIVE
        "post" -> MatchState.DONE
        else -> MatchState.PRE
    }

    fun millis(date: String): Long? = runCatching { OffsetDateTime.parse(date).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(date).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    /** A home/away board of teams (basketball, cricket): [scoreOf] says how each sport writes a side's score. */
    fun teamBoard(
        text: String,
        league: String,
        sport: Sport,
        isMine: (String) -> Boolean,
        scoreOf: (Competitor) -> String,
    ): List<Match> {
        val (leagueName, events) = events(text, league)
        return events.mapNotNull { e -> teamMatch(e, league, leagueName, sport, isMine, scoreOf) }
    }

    private fun teamMatch(
        e: Event, league: String, leagueName: String, sport: Sport, isMine: (String) -> Boolean,
        scoreOf: (Competitor) -> String,
    ): Match? {
        val sides = e.competitions.firstOrNull()?.competitors.orEmpty()
        val home = sides.firstOrNull { it.homeAway == "home" } ?: sides.firstOrNull { it.order == 1 } ?: return null
        val away = sides.firstOrNull { it.homeAway == "away" } ?: sides.firstOrNull { it.order == 2 } ?: return null
        val homeTeam = home.team ?: return null
        val awayTeam = away.team ?: return null
        val state = stateOf(e.status.type.state)
        fun side(c: Competitor, t: Team) = io.shamash.domain.Team(
            name = t.displayName, shortName = t.shortDisplayName ?: t.displayName, logoUrl = t.logo,
            score = if (state == MatchState.PRE) "" else scoreOf(c),
        )
        return Match(
            id = e.id, competition = league, competitionName = leagueName,
            home = side(home, homeTeam), away = side(away, awayTeam),
            startsAt = millis(e.date) ?: return null, state = state,
            clock = if (state == MatchState.PRE) "" else e.status.type.shortDetail,
            mine = isMine(homeTeam.displayName) || isMine(awayTeam.displayName), sport = sport,
        )
    }
}
