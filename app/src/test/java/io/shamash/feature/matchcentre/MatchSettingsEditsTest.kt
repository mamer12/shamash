package io.shamash.feature.matchcentre

import io.shamash.domain.Competitions
import io.shamash.domain.Follow
import io.shamash.domain.MatchFollows
import io.shamash.domain.Profile
import io.shamash.domain.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSettingsEditsTest {
    @Test fun MC_07_chips_are_grouped_by_sport_in_order_with_every_catalog_entry() {
        val groups = MatchSettingsEdits.groups()
        assertEquals(Sport.entries, groups.map { it.first })
        assertEquals(Competitions.catalog.size, groups.sumOf { it.second.size })
        assertTrue(groups.all { (sport, defs) -> defs.all { it.sport == sport } })
    }

    @Test fun MC_07_changing_one_sports_chips_keeps_the_others_and_legacy_codes() {
        val soccer = MatchSettingsEdits.groups().first { it.first == Sport.SOCCER }.second
        val picked = setOf("eng.1", "nba", "old.code")
        assertEquals(setOf("esp.1", "nba", "old.code"), MatchSettingsEdits.withGroup(picked, soccer, setOf("esp.1")))
    }

    @Test fun MC_09_typed_names_become_teams_and_known_players_stay_players() {
        val p = Profile(teams = setOf("Arsenal"), follows = setOf(Follow(MatchFollows.PLAYER, "Sinner"), Follow(MatchFollows.SPORT, "F1")))
        assertEquals("Arsenal, Sinner", MatchSettingsEdits.followsText(p))
        val next = MatchSettingsEdits.withFollowsText(p, " Al Hilal , sinner,, Al Hilal")
        assertEquals(setOf("Al Hilal"), next.teams)
        assertEquals(setOf(Follow(MatchFollows.PLAYER, "Sinner"), Follow(MatchFollows.SPORT, "F1")), next.follows)
    }

    @Test fun MC_09_following_a_sport_is_read_back_and_unfollowing_removes_every_spelling() {
        val p = Profile(follows = setOf(Follow(MatchFollows.SPORT, "Formula 1")))
        assertTrue(MatchSettingsEdits.followsSport(p, Sport.F1))
        assertFalse(MatchSettingsEdits.followsSport(MatchSettingsEdits.withSport(p, Sport.F1, false), Sport.F1))
        val tennis = MatchSettingsEdits.withSport(p, Sport.TENNIS, true)
        assertTrue(MatchFollows.sportFollowed(tennis, Sport.TENNIS))
        assertTrue(MatchFollows.sportFollowed(tennis, Sport.F1))
    }

    @Test fun MC_11_every_follow_gets_one_level_row_sports_first() {
        val p = Profile(teams = setOf("arsenal"), follows = setOf(Follow(MatchFollows.TEAM, "Arsenal"), Follow(MatchFollows.SPORT, "F1")))
        val rows = MatchSettingsEdits.alertFollows(p)
        assertEquals(2, rows.size)
        assertEquals(MatchFollows.SPORT, rows.first().kind)
        val set = AlertSettings().withLevel(rows.last(), AlertLevel.ALL)
        assertEquals(AlertLevel.ALL, set.levelOf(rows.last()))
        assertEquals(AlertLevel.KICK_OFF, set.levelOf(rows.first()))
    }

    @Test fun MC_12_quiet_hour_edges_wrap_around_midnight() {
        assertEquals(0, MatchSettingsEdits.shift(23 * 60 + 30, 1))
        assertEquals(23 * 60 + 30, MatchSettingsEdits.shift(0, -1))
        assertEquals("07:30", MatchSettingsEdits.clock(450))
    }

    @Test fun MC_12_days_toggle_on_and_off() {
        val on = MatchSettingsEdits.toggleDay(emptySet(), 5)
        assertEquals(setOf(5), on)
        assertEquals(emptySet<Int>(), MatchSettingsEdits.toggleDay(on, 5))
    }
}
