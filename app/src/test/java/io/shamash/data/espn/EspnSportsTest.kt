package io.shamash.data.espn

import io.shamash.data.Fixtures
import io.shamash.domain.Competitions
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EspnSportsTest {
    private val none = { _: String -> false }

    @Test
    fun MC_08_nba_shows_crests_and_points_and_skips_what_does_not_fit() {
        val m = EspnNba.scoreboard(Fixtures.read("espn/scoreboard_nba.json"), "nba") { it.contains("Lakers") }

        assertEquals(listOf("n1", "n2"), m.map { it.id })       // the broken event is skipped, not fatal
        val live = m.first()
        assertEquals(Sport.BASKETBALL, live.sport)
        assertEquals("National Basketball Association", live.competitionName)
        assertEquals("112", live.home.score); assertEquals("108", live.away.score)
        assertEquals("https://a.espncdn.com/lal.png", live.home.logoUrl)
        assertEquals(MatchState.LIVE, live.state); assertTrue(live.mine)
        assertEquals("", m[1].home.score)       // not started: no score
        assertFalse(m[1].mine)
    }

    @Test
    fun MC_08_cricket_scores_read_like_186_4_with_overs() {
        val m = EspnCricket.scoreboard(Fixtures.read("espn/scoreboard_cricket.json"), "cricket", none)

        assertEquals("186/4 (18.2)", m[0].home.score)
        assertEquals("205/7 (20)", m[0].away.score)
        assertEquals(Sport.CRICKET, m[0].sport)
        assertEquals("", m[1].home.score)
    }

    @Test
    fun MC_08_tennis_has_two_players_surname_seed_and_games_per_set() {
        val m = EspnTennis.scoreboard(Fixtures.read("espn/scoreboard_tennis.json"), "atp") { it.contains("Sinner") }

        assertEquals(listOf("tm1", "tm2"), m.map { it.id })     // doubles skipped
        val live = m[0]
        assertEquals("Paris Masters", live.competitionName)
        assertEquals("Alcaraz (1)", live.home.shortName)
        assertEquals("Sinner", live.away.shortName)
        assertEquals("6 3", live.home.score); assertEquals("4 2", live.away.score)
        assertEquals("https://flags/es.png", live.home.logoUrl)
        assertEquals("2nd Set", live.clock)
        assertTrue(live.mine); assertFalse(m[1].mine)
        assertEquals("Rune (7)", m[1].away.shortName)
    }

    @Test
    fun MC_08_f1_sessions_are_matches_of_the_weekend() {
        val m = EspnF1.scoreboard(Fixtures.read("espn/scoreboard_f1.json"), "f1", none)

        assertEquals(listOf("s1", "s2", "s3"), m.map { it.id })   // an unknown session is skipped
        assertEquals(listOf("FP1", "Q", "R"), m.map { it.session })
        assertEquals("Mexico City Grand Prix", m[0].home.name)
        assertEquals(MatchState.DONE, m[0].state); assertEquals("Norris", m[0].clock)    // the winner
        assertEquals("Leclerc", m[1].away.score)                                        // the live leader
        assertEquals("Race", m[2].away.name)
        assertEquals(Sport.F1, m[2].sport)
    }

    @Test
    fun MC_08_an_unknown_shape_gives_no_cards() {
        assertTrue(EspnNba.scoreboard("""{"events":[{"id":"x"}]}""", "nba", none).isEmpty())
        assertTrue(EspnTennis.scoreboard("""{"events":[]}""", "atp", none).isEmpty())
    }

    @Test
    fun MC_07_competitions_are_data_grouped_by_sport_and_old_codes_stay_soccer() {
        assertEquals("racing/f1", Competitions.find("f1")?.path)
        assertEquals(Sport.SOCCER, Competitions.resolve("ger.2").sport)
        assertEquals("soccer/ger.2", Competitions.resolve("ger.2").path)
        assertEquals(Sport.entries.toSet(), Competitions.grouped().keys)
    }

    @Test
    fun MC_07_day_calls_for_team_sports_and_one_range_for_race_weekends() {
        val day = LocalDate.of(2026, 10, 21)

        assertEquals(7, SportBoards.dates(Sport.BASKETBALL, day, 7).size)
        assertEquals("20261021", SportBoards.dates(Sport.SOCCER, day, 7).first())
        assertEquals(listOf("20261021-20261027"), SportBoards.dates(Sport.F1, day, 7))
    }
}
