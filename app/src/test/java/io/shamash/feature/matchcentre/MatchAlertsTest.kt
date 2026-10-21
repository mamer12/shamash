package io.shamash.feature.matchcentre

import io.shamash.domain.Follow
import io.shamash.domain.Match
import io.shamash.domain.MatchFollows
import io.shamash.domain.MatchState
import io.shamash.domain.Profile
import io.shamash.domain.Sport
import io.shamash.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchAlertsTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(2026, 10, day, h, min, 0, 0, utc).toInstant().toEpochMilli()      // 2026-10-19 is a Monday

    private fun match(id: String, home: String, away: String, state: MatchState = MatchState.LIVE, sport: Sport = Sport.SOCCER) =
        Match(id, "x", "X", Team(home, home, ""), Team(away, away, ""), 0, state, mine = true, sport = sport)

    private val profile = Profile(
        teams = setOf("Arsenal"),
        follows = setOf(Follow(MatchFollows.TEAM, "Lakers"), Follow(MatchFollows.SPORT, "F1"), Follow(MatchFollows.PLAYER, "Sinner")),
    )

    @Test
    fun MC_09_follows_cover_teams_players_and_sports_and_keep_the_old_teams() {
        assertEquals(Follow("team", "Arsenal"), MatchFollows.all(profile).first { it.name == "Arsenal" })
        assertTrue(MatchFollows.mine(profile)("Jannik Sinner"))
        assertFalse(MatchFollows.mine(profile)("Rafael Nadal"))
        assertTrue(MatchFollows.sportFollowed(profile, Sport.F1))
        assertFalse(MatchFollows.sportFollowed(profile, Sport.TENNIS))
    }

    @Test
    fun MC_11_default_is_kick_off_so_only_the_reminder_shows() {
        val m = match("a", "Arsenal", "Spurs")
        val s = AlertSettings()
        val shows = { c: OverlayCard -> MatchAlerts.shows(c, m, profile, s, 0, utc) }

        assertTrue(shows(OverlayCard.REMINDER))
        assertFalse(shows(OverlayCard.LIVE)); assertFalse(shows(OverlayCard.GOAL))
    }

    @Test
    fun MC_11_levels_off_goals_all() {
        val m = match("a", "Arsenal", "Spurs")
        val follow = Follow("team", "Arsenal")
        fun shows(level: AlertLevel, c: OverlayCard) =
            MatchAlerts.shows(c, m, profile, AlertSettings().withLevel(follow, level), 0, utc)

        assertFalse(shows(AlertLevel.OFF, OverlayCard.REMINDER))
        assertTrue(shows(AlertLevel.GOALS, OverlayCard.GOAL)); assertTrue(shows(AlertLevel.GOALS, OverlayCard.FINAL))
        assertFalse(shows(AlertLevel.GOALS, OverlayCard.REMINDER)); assertFalse(shows(AlertLevel.GOALS, OverlayCard.LIVE))
        OverlayCard.entries.forEach { assertTrue(shows(AlertLevel.ALL, it)) }
    }

    @Test
    fun MC_11_alerts_of_other_follows_never_show() {
        val other = match("z", "Chelsea", "Spurs")

        assertEquals(AlertLevel.OFF, MatchAlerts.levelFor(other, profile, AlertSettings()))
        assertTrue(MatchAlerts.eligible(listOf(other), profile, AlertSettings()).isEmpty())
        assertFalse(MatchAlerts.shows(OverlayCard.REMINDER, other, profile, AlertSettings(), 0, utc))
    }

    @Test
    fun MC_11_a_live_all_match_replaces_the_bubble_of_a_lower_priority_one() {
        val arsenal = match("a", "Arsenal", "Spurs")
        val lakers = match("l", "Lakers", "Celtics", sport = Sport.BASKETBALL)
        val s = AlertSettings().withLevel(Follow("team", "Lakers"), AlertLevel.ALL)
        val machine = WatchMachine { MatchAlerts.priority(it, profile, s) }
        val t = 1_000_000L

        assertEquals(arsenal.id, (machine.next(t, listOf(arsenal)) as WatchStep.Show).match.id)
        val next = machine.next(t + 60_000, listOf(arsenal, lakers)) as WatchStep.Show

        assertEquals("l", next.match.id)        // one bubble at a time
        assertEquals(OverlayCard.LIVE, next.card)
        // and the lower one does not take it back
        assertEquals("l", (machine.next(t + 120_000, listOf(arsenal, lakers)) as WatchStep.Show).match.id)
    }

    @Test
    fun MC_12_quiet_hours_cross_midnight() {
        val q = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60)

        assertTrue(q.suppresses(at(21, 23, 30), utc))
        assertTrue(q.suppresses(at(22, 2), utc))
        assertTrue(q.suppresses(at(22, 6, 59), utc))
        assertFalse(q.suppresses(at(22, 7), utc))
        assertFalse(q.suppresses(at(21, 22, 59), utc))
        assertFalse(QuietHours(enabled = false).suppresses(at(22, 2), utc))
    }

    @Test
    fun MC_12_quiet_days_count_from_the_day_the_range_started() {
        val mondayNight = QuietHours(true, 23 * 60, 7 * 60, days = setOf(1))     // Monday

        assertTrue(mondayNight.suppresses(at(19, 23, 30), utc))     // Monday 23:30
        assertTrue(mondayNight.suppresses(at(20, 3), utc))          // Tuesday 03:00 belongs to Monday night
        assertFalse(mondayNight.suppresses(at(21, 3), utc))         // Wednesday 03:00 does not
        assertFalse(mondayNight.suppresses(at(20, 23, 30), utc))    // Tuesday night is not chosen
    }

    @Test
    fun MC_12_an_always_match_ignores_quiet_hours_and_others_are_held_back() {
        val m = match("a", "Arsenal", "Spurs")
        val quiet = QuietHours(true, 0, 23 * 60 + 59)
        val s = AlertSettings(quiet = quiet)

        assertFalse(MatchAlerts.shows(OverlayCard.REMINDER, m, profile, s, at(21, 12), utc))
        assertTrue(MatchAlerts.shows(OverlayCard.REMINDER, m, profile, s.copy(always = setOf("a")), at(21, 12), utc))
    }

    @Test
    fun MC_13_goal_only_hides_the_bubble_but_keeps_goal_and_final() {
        val m = match("a", "Arsenal", "Spurs")
        val s = AlertSettings(goalOnly = true).withLevel(Follow("team", "Arsenal"), AlertLevel.ALL)

        assertFalse(MatchAlerts.shows(OverlayCard.LIVE, m, profile, s, 0, utc))
        assertTrue(MatchAlerts.shows(OverlayCard.GOAL, m, profile, s, 0, utc))
        assertTrue(MatchAlerts.shows(OverlayCard.FINAL, m, profile, s, 0, utc))
    }

    @Test
    fun MC_13_change_detection_per_sport() {
        fun h(s: Sport, b: Pair<String, String>?, n: Pair<String, String>) = KeyMoments.happened(s, b, n)

        assertTrue(h(Sport.SOCCER, "0" to "0", "1" to "0"))
        assertFalse(h(Sport.SOCCER, "1" to "0", "0" to "0"))        // taken back by VAR
        assertFalse(h(Sport.SOCCER, null, "1" to "0"))              // first look
        assertTrue(h(Sport.BASKETBALL, "50" to "52", "55" to "52")) // lead changed hands
        assertFalse(h(Sport.BASKETBALL, "50" to "52", "50" to "54"))
        assertTrue(h(Sport.CRICKET, "100/3 (12)" to "", "101/4 (12.1)" to ""))
        assertFalse(h(Sport.CRICKET, "100/3 (12)" to "", "104/3 (12.2)" to ""))
        assertTrue(h(Sport.TENNIS, "6 1" to "4 0", "6 3 0" to "4 6 0"))     // a set finished
        assertFalse(h(Sport.TENNIS, "6 1" to "4 0", "6 2" to "4 0"))
        assertTrue(h(Sport.F1, "" to "Norris", "" to "Piastri"))
        assertFalse(h(Sport.F1, "" to "Norris", "" to "Norris"))
    }

    @Test
    fun MC_13_a_basketball_basket_is_not_a_goal_card_but_a_lead_change_is() {
        val machine = WatchMachine()
        fun nba(h: String, a: String) = match("n", "Lakers", "Celtics", sport = Sport.BASKETBALL)
            .copy(home = Team("Lakers", "Lakers", "", h), away = Team("Celtics", "Celtics", "", a))
        val t = 1L

        machine.next(t, listOf(nba("50", "52")))
        assertEquals(OverlayCard.LIVE, (machine.next(t + 1, listOf(nba("50", "54"))) as WatchStep.Show).card)
        assertEquals(OverlayCard.GOAL, (machine.next(t + 2, listOf(nba("58", "54"))) as WatchStep.Show).card)
    }

    @Test
    fun MC_14_an_f1_session_is_looked_at_every_30_seconds() {
        val live = match("f", "Mexico GP", "Race", sport = Sport.F1)
        val step = WatchMachine().next(1L, listOf(live)) as WatchStep.Show

        assertEquals(30_000L, step.recheckInMs)
    }

    @Test
    fun MC_15_in_right_to_left_the_sides_swap() {
        val m = match("a", "Arsenal", "Spurs")

        assertEquals("Spurs", MatchSides.forDirection(m, rtl = true).home.name)
        assertEquals("Arsenal", MatchSides.forDirection(m, rtl = false).home.name)
    }

    @Test
    fun MC_10_picks_the_first_subscribed_installed_app_else_the_first_installed_else_none() {
        val map = BroadcastMap.parse(
            """{"default":{"*":["tod"]},"competitions":{"eng.1":{"SA":["bein","shahid"],"*":["tod","prime"]}}}""",
        )
        val m = match("a", "A", "B").copy(competition = "eng.1")

        assertEquals("shahid", map.choose(m, "SA", setOf("shahid"), setOf("tod", "shahid")))
        assertEquals("bein", map.choose(m, "SA", emptySet(), setOf("shahid", "bein")))        // not subscribed: first installed
        assertEquals("prime", map.choose(m, "US", setOf("prime"), setOf("tod", "prime")))
        assertNull(map.choose(m, "SA", setOf("bein"), setOf("netflix")))
        assertEquals(listOf("tod"), map.appsFor("unknown", "SA"))
    }

    @Test
    fun MC_10_a_user_file_overrides_a_competition_and_a_bad_file_is_ignored() {
        val shipped = BroadcastMap.parse("""{"competitions":{"f1":{"*":["f1tv"]},"nba":{"*":["nbaapp"]}}}""")
        val own = BroadcastMap.parse("""{"competitions":{"f1":{"*":["tod"]}}}""")

        val merged = shipped.overriddenBy(own)
        assertEquals(listOf("tod"), merged.appsFor("f1", "SA"))
        assertEquals(listOf("nbaapp"), merged.appsFor("nba", "SA"))
        assertEquals(BroadcastMap(), BroadcastMap.parse("{ not json"))
    }

    @Test
    fun MC_16_alert_settings_survive_a_round_trip_as_plain_json() {
        val s = AlertSettings(goalOnly = true, quiet = QuietHours(true, 60, 120, setOf(1, 7)), always = setOf("m1"))
            .withLevel(Follow("team", "Arsenal"), AlertLevel.ALL)
        val text = io.shamash.data.json.encodeToString(AlertSettings.serializer(), s)

        assertEquals(s, io.shamash.data.json.decodeFromString(AlertSettings.serializer(), text))
    }
}
