package io.shamash.feature.matchcentre

import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Team
import io.shamash.feature.matchcentre.WatchMachine.Companion.FINAL_MS
import io.shamash.feature.matchcentre.WatchMachine.Companion.GOAL_MS
import io.shamash.feature.matchcentre.WatchMachine.Companion.REMINDER_MS
import io.shamash.feature.matchcentre.WatchMachine.Companion.TICK_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchMachineTest {
    private val kickOff = 1_700_000_000_000L
    private val minute = 60_000L

    private fun match(
        state: MatchState = MatchState.PRE,
        home: String = "",
        away: String = "",
        mine: Boolean = true,
        id: String = "m1",
        startsAt: Long = kickOff,
    ) = Match(
        id = id, competition = "esp.1", competitionName = "LaLiga",
        home = Team("Real Madrid", "RMA", "https://crest/rma.png", home),
        away = Team("Barcelona", "BAR", "https://crest/bar.png", away),
        startsAt = startsAt, state = state, clock = if (state == MatchState.LIVE) "12'" else "", mine = mine,
    )

    private fun card(step: WatchStep) = (step as? WatchStep.Show)?.card

    @Test
    fun `MC_04 nothing shows before the reminder, and it says when to look again`() {
        val step = WatchMachine().next(kickOff - 12 * minute, listOf(match()))

        assertEquals(WatchStep.Hide(2 * minute), step)
    }

    @Test
    fun `MC_04 the reminder shows 10 min before kick-off for 15 s`() {
        val machine = WatchMachine()
        val at = kickOff - 10 * minute

        assertEquals(WatchStep.Show(match(), OverlayCard.REMINDER, REMINDER_MS), machine.next(at, listOf(match())))
        assertEquals(OverlayCard.REMINDER, card(machine.next(at + REMINDER_MS - 1, listOf(match()))))
        assertEquals(WatchStep.Hide(TICK_MS), machine.next(at + REMINDER_MS, listOf(match())))
        assertEquals(WatchStep.Hide(TICK_MS), machine.next(at + 5 * minute, listOf(match())))
    }

    @Test
    fun `MC_04 other people's matches are not followed`() {
        assertEquals(WatchStep.Stop, WatchMachine().next(kickOff - 10 * minute, listOf(match(mine = false))))
    }

    @Test
    fun `MC_05 the live score is looked at every 60 s`() {
        val machine = WatchMachine()
        val live = match(MatchState.LIVE, "0", "0")

        assertEquals(WatchStep.Show(live, OverlayCard.LIVE, TICK_MS), machine.next(kickOff + minute, listOf(live)))
    }

    @Test
    fun `MC_05 a changed score flashes GOAL for 10 s`() {
        val machine = WatchMachine()
        machine.next(kickOff + minute, listOf(match(MatchState.LIVE, "0", "0")))
        val scored = match(MatchState.LIVE, "1", "0")
        val at = kickOff + 2 * minute

        assertEquals(WatchStep.Show(scored, OverlayCard.GOAL, GOAL_MS), machine.next(at, listOf(scored)))
        assertEquals(OverlayCard.GOAL, card(machine.next(at + GOAL_MS - 1, listOf(scored))))
        assertEquals(OverlayCard.LIVE, card(machine.next(at + GOAL_MS, listOf(scored))))
    }

    @Test
    fun `MC_05 the first score seen is not a goal, nor is one taken back`() {
        val machine = WatchMachine()

        assertEquals(OverlayCard.LIVE, card(machine.next(kickOff + 30 * minute, listOf(match(MatchState.LIVE, "2", "1")))))
        assertEquals(OverlayCard.LIVE, card(machine.next(kickOff + 31 * minute, listOf(match(MatchState.LIVE, "1", "1")))))
    }

    @Test
    fun `MC_05 full time shows the final score for 30 s, then stops`() {
        val machine = WatchMachine()
        machine.next(kickOff + 90 * minute, listOf(match(MatchState.LIVE, "2", "1")))
        val done = match(MatchState.DONE, "2", "2")
        val at = kickOff + 100 * minute

        assertEquals(WatchStep.Show(done, OverlayCard.FINAL, FINAL_MS), machine.next(at, listOf(done)))
        assertEquals(OverlayCard.FINAL, card(machine.next(at + FINAL_MS - 1, listOf(done))))
        assertEquals(WatchStep.Stop, machine.next(at + FINAL_MS, listOf(done)))
    }

    @Test
    fun `MC_05 a live match that leaves the list ends like full time`() {
        val machine = WatchMachine()
        machine.next(kickOff + 90 * minute, listOf(match(MatchState.LIVE, "1", "0")))

        val step = machine.next(kickOff + 95 * minute, emptyList())

        assertEquals(OverlayCard.FINAL, card(step))
        assertEquals("1", (step as WatchStep.Show).match.home.score)
    }

    @Test
    fun `MC_06 a match never seen live stops without a final score`() {
        val machine = WatchMachine()
        machine.next(kickOff - 10 * minute, listOf(match()))

        assertEquals(WatchStep.Stop, machine.next(kickOff + 4 * 60 * minute, listOf(match())))
    }

    @Test
    fun `MC_06 nothing to follow stops the service`() {
        assertEquals(WatchStep.Stop, WatchMachine().next(kickOff - 60 * minute, listOf(match())))
        assertEquals(WatchStep.Stop, WatchMachine().next(kickOff, emptyList()))
    }

    @Test
    fun `MC_06 the next match to watch is the user's, live first, then the soonest`() {
        val now = kickOff
        val later = match(id = "later", startsAt = now + 3 * 60 * minute)
        val sooner = match(id = "sooner", startsAt = now + 60 * minute)
        val theirs = match(id = "theirs", startsAt = now + 10 * minute, mine = false)
        val over = match(MatchState.DONE, id = "over", startsAt = now - 3 * 60 * minute)
        val live = match(MatchState.LIVE, "0", "0", id = "live", startsAt = now - 30 * minute)

        assertEquals("sooner", WatchMachine.nextToWatch(listOf(later, theirs, over, sooner), now)?.id)
        assertEquals("live", WatchMachine.nextToWatch(listOf(later, sooner, live), now)?.id)
        assertNull(WatchMachine.nextToWatch(listOf(later, sooner), now, window = 20 * minute))
    }
}
