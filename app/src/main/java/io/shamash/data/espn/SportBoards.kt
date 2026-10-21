package io.shamash.data.espn

import io.shamash.domain.CompetitionDef
import io.shamash.domain.Match
import io.shamash.domain.Sport
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Which parser and which `dates=` values each sport's scoreboard takes. Unknown shapes give no cards. SPEC: MC-07, MC-08 */
object SportBoards {
    private val DAY = DateTimeFormatter.BASIC_ISO_DATE

    fun parse(def: CompetitionDef, text: String, isMine: (String) -> Boolean): List<Match> = when (def.sport) {
        Sport.SOCCER -> EspnJson.scoreboard(text, def.id, isMine)
        Sport.F1 -> EspnF1.scoreboard(text, def.id, isMine)
        Sport.TENNIS -> EspnTennis.scoreboard(text, def.id, isMine)
        Sport.BASKETBALL -> EspnNba.scoreboard(text, def.id, isMine)
        Sport.CRICKET -> EspnCricket.scoreboard(text, def.id, isMine)
    }

    /** One `dates` value per call: a day each for team sports, one range for race weekends and tournaments. */
    fun dates(sport: Sport, today: LocalDate, days: Int): List<String> = when (sport) {
        Sport.F1, Sport.TENNIS -> listOf("${today.format(DAY)}-${today.plusDays(days - 1L).format(DAY)}")
        else -> (0 until days).map { today.plusDays(it.toLong()).format(DAY) }
    }
}
