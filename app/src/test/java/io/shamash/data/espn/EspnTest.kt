package io.shamash.data.espn

import io.shamash.data.Fixtures
import io.shamash.data.Refresh
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Profile
import io.shamash.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class EspnTest {
    private val fans = Profile(teams = setOf("arsenal", " Dortmund "))

    @Test
    fun PRV_11_upcoming_matches_have_crests_kick_off_and_no_score() {
        val today = EspnJson.scoreboard(Fixtures.read("espn/scoreboard_ucl_today.json"), "uefa.champions", fans::isMyTeam)

        val bayern = today.first { it.id == "401915312" }
        assertEquals("UEFA Champions League", bayern.competitionName)
        assertEquals("uefa.champions", bayern.competition)
        assertEquals(Team("Bayern Munich", "Bayern", "https://a.espncdn.com/i/teamlogos/soccer/500/132.png", ""), bayern.home)
        assertEquals("Arsenal", bayern.away.name)
        assertEquals(OffsetDateTime.parse("2026-10-21T19:00Z").toInstant().toEpochMilli(), bayern.startsAt)
        assertEquals(MatchState.PRE, bayern.state)
        assertEquals("", bayern.clock)
        assertTrue(bayern.mine)
        assertFalse(today.first { it.id == "401915395" }.mine)
    }

    @Test
    fun PRV_11_finished_matches_keep_the_score_and_full_time() {
        val board = EspnJson.scoreboard(Fixtures.read("espn/scoreboard_ucl_yday.json"), "uefa.champions", fans::isMyTeam)

        val dortmund = board.first { it.id == "401915404" }
        assertEquals(MatchState.DONE, dortmund.state)
        assertEquals("FT", dortmund.clock)
        assertEquals("1", dortmund.home.score)
        assertEquals("2", dortmund.away.score)
        assertEquals("Dortmund", dortmund.away.shortName)
        assertTrue(dortmund.mine)
    }

    @Test
    fun PRV_11_live_matches_show_the_clock() {
        val live = Fixtures.read("espn/scoreboard_ucl_yday.json")
            .replace("\"state\": \"post\"", "\"state\": \"in\"")
            .replace("\"shortDetail\": \"FT\"", "\"shortDetail\": \"67'\"")

        val match = EspnJson.scoreboard(live, "uefa.champions", fans::isMyTeam).first { it.id == "401915402" }

        assertEquals(MatchState.LIVE, match.state)
        assertEquals("90'+2'", match.clock)
        assertEquals("4", match.home.score)
    }

    @Test
    fun MC_01_mine_first_then_by_kick_off_each_once() {
        val board = EspnJson.scoreboard(Fixtures.read("espn/scoreboard_ucl_today.json"), "uefa.champions", fans::isMyTeam)

        val ordered = MatchRules.order(board + board)

        assertEquals(board.size, ordered.size)
        assertEquals("401915312", ordered.first().id)
        assertEquals(ordered.drop(1).sortedBy { it.startsAt }, ordered.drop(1))
    }

    @Test
    fun MC_01_default_competitions_when_none_chosen() {
        assertEquals(listOf("uefa.champions", "eng.1", "esp.1"), MatchRules.leagues(Profile()))
        assertEquals(listOf("esp.1", "ksa.1"), MatchRules.leagues(Profile(competitions = setOf("ksa.1", "esp.1"))))
    }

    @Test
    fun HOME_14_matches_refresh_every_minute_while_mine_plays() {
        val now = 1_000_000_000L
        val other = match(MatchState.LIVE, now, mine = false)

        assertEquals(MatchRules.FULL_EVERY, MatchRules.checkEvery(listOf(other), now))
        assertEquals(Refresh.MINUTE, MatchRules.checkEvery(listOf(match(MatchState.LIVE, now - Refresh.HOUR, mine = true)), now))
        assertEquals(Refresh.MINUTE, MatchRules.checkEvery(listOf(match(MatchState.PRE, now + 4 * Refresh.MINUTE, mine = true)), now))
        assertEquals(MatchRules.FULL_EVERY, MatchRules.checkEvery(listOf(match(MatchState.PRE, now + Refresh.HOUR, mine = true)), now))
    }

    private fun match(state: MatchState, startsAt: Long, mine: Boolean) = Match(
        id = "$state$startsAt", competition = "eng.1", competitionName = "Premier League",
        home = Team("A", "A", ""), away = Team("B", "B", ""), startsAt = startsAt, state = state, mine = mine,
    )
}
