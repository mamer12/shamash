package io.shamash.data.espn

import io.shamash.domain.Match
import io.shamash.domain.Sport

/** Reads ESPN's cricket scoreboard: crests and the innings as "186/4 (18.2)". SPEC: MC-08 */
object EspnCricket {
    fun scoreboard(text: String, league: String, isMine: (String) -> Boolean): List<Match> =
        EspnBoard.teamBoard(text, league, Sport.CRICKET, isMine, ::innings)

    private fun innings(c: EspnBoard.Competitor): String {
        val last = c.linescores.lastOrNull { it.runs != null }
        val runs = last?.runs ?: return c.score.orEmpty()
        val wickets = last.wickets?.toInt()
        val overs = last.overs?.let { " (${trim(it)})" }.orEmpty()
        return "${runs.toInt()}${wickets?.let { "/$it" }.orEmpty()}$overs"
    }

    private fun trim(d: Double) = if (d == d.toInt().toDouble()) d.toInt().toString() else d.toString()
}
