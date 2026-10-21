package io.shamash.data.espn

import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import io.shamash.domain.Team

/**
 * Reads ESPN's Formula 1 scoreboard: each session of a race weekend is a match whose sides are the weekend and the
 * session (the code in [Match.session]); live, the away score is the leader; done, [Match.clock] is the winner. SPEC: MC-08
 */
object EspnF1 {
    fun scoreboard(text: String, league: String, isMine: (String) -> Boolean): List<Match> {
        val (_, events) = EspnBoard.events(text, league)
        return events.flatMap { e -> e.competitions.mapNotNull { session(e, it, league, isMine) } }
    }

    private fun session(e: EspnBoard.Event, c: EspnBoard.Competition, league: String, isMine: (String) -> Boolean): Match? {
        val type = c.type ?: return null
        val code = SessionCodes.of(type.abbreviation, type.text) ?: return null
        val status = c.status ?: e.status
        val state = EspnBoard.stateOf(status.type.state)
        val first = c.competitors.minByOrNull { if (it.order > 0) it.order else Int.MAX_VALUE }?.athlete
        val leader = first?.displayName?.trim()?.substringAfterLast(' ').orEmpty()
        val weekend = Team(e.name, e.shortName ?: e.name, "")
        val label = SessionCodes.english(code)
        return Match(
            id = c.id.ifBlank { "${e.id}-$code" }, competition = league, competitionName = e.name,
            home = weekend,
            away = Team(label, label, first?.flag?.href.orEmpty(), if (state == MatchState.LIVE) leader else ""),
            startsAt = EspnBoard.millis(c.date) ?: return null, state = state,
            clock = when (state) { MatchState.DONE -> leader; MatchState.LIVE -> status.type.shortDetail; else -> "" },
            mine = isMine(e.name), sport = Sport.F1, session = code,
        )
    }
}

/** F1 session codes; labels are localized from the code (MC-15), English here for the model's own text. */
object SessionCodes {
    const val FP1 = "FP1"; const val FP2 = "FP2"; const val FP3 = "FP3"
    const val QUALI = "Q"; const val SPRINT_QUALI = "SQ"; const val SPRINT = "SPR"; const val RACE = "R"

    fun of(abbreviation: String, text: String): String? {
        val a = abbreviation.trim().uppercase()
        val t = text.lowercase()
        return when {
            a in setOf(FP1, FP2, FP3) -> a
            a == "SS" || "sprint" in t && ("shootout" in t || "qualif" in t) -> SPRINT_QUALI
            a == "SPR" || a == "SPRINT" || t == "sprint" || "sprint race" in t -> SPRINT
            a in setOf("QUAL", "Q") || "qualif" in t -> QUALI
            a == "RACE" || a == "R" || t == "race" -> RACE
            else -> null
        }
    }

    fun english(code: String) = when (code) {
        FP1 -> "Practice 1"; FP2 -> "Practice 2"; FP3 -> "Practice 3"
        QUALI -> "Qualifying"; SPRINT_QUALI -> "Sprint Qualifying"; SPRINT -> "Sprint"; else -> "Race"
    }
}
