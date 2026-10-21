package io.shamash.data.espn

import io.shamash.domain.Match
import io.shamash.domain.Sport

/** Reads ESPN's NBA scoreboard: crests and the points ("112"). SPEC: MC-08 */
object EspnNba {
    fun scoreboard(text: String, league: String, isMine: (String) -> Boolean): List<Match> =
        EspnBoard.teamBoard(text, league, Sport.BASKETBALL, isMine) { it.score.orEmpty() }
}
