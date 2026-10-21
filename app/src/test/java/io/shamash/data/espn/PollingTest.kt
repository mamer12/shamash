package io.shamash.data.espn

import io.shamash.data.Refresh
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import io.shamash.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class PollingTest {
    private val now = 1_000_000_000L
    private fun m(state: MatchState, at: Long, sport: Sport = Sport.SOCCER, mine: Boolean = true) = Match(
        "i$at$state", "x", "X", Team("A", "A", ""), Team("B", "B", ""), at, state, mine = mine, sport = sport,
    )

    @Test
    fun MC_14_live_is_a_minute_and_an_f1_session_30_seconds() {
        assertEquals(Refresh.MINUTE, Polling.every(m(MatchState.LIVE, now), now))
        assertEquals(30_000L, Polling.every(m(MatchState.LIVE, now, Sport.F1), now))
    }

    @Test
    fun MC_14_a_follow_within_3_hours_is_looked_at_every_10_minutes_else_every_3_hours() {
        assertEquals(10 * Refresh.MINUTE, Polling.every(m(MatchState.PRE, now + 2 * Refresh.HOUR), now))
        assertEquals(3 * Refresh.HOUR, Polling.every(m(MatchState.PRE, now + 5 * Refresh.HOUR), now))
        assertEquals(Refresh.MINUTE, Polling.every(m(MatchState.PRE, now + 3 * Refresh.MINUTE), now))
    }

    @Test
    fun MC_14_only_the_users_matches_count_and_the_shortest_wait_wins() {
        val list = listOf(m(MatchState.LIVE, now, mine = false), m(MatchState.PRE, now + Refresh.HOUR), m(MatchState.LIVE, now, Sport.F1))
        assertEquals(30_000L, Polling.every(list, now))
        assertEquals(Polling.IDLE_MS, Polling.every(listOf(m(MatchState.LIVE, now, mine = false)), now))
    }
}
