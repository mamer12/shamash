package io.shamash.feature.matchcentre

import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Profile
import io.shamash.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** The "Always" star a match gets in Settings. SPEC: MC-12 */
class AlertStarsTest {
    private val profile = Profile(teams = setOf("Arsenal"))

    private fun match(id: String, state: MatchState, at: Long, home: String = "Arsenal", mine: Boolean = true) =
        Match(id, "eng.1", "Premier League", Team(home, home, ""), Team("Spurs", "Spurs", ""), at, state, mine = mine)

    @Test fun `MC_12 the follows' coming matches can be starred, live first, finished and others left out`() {
        val list = listOf(
            match("later", MatchState.PRE, 3_000), match("live", MatchState.LIVE, 1_000), match("done", MatchState.DONE, 500),
            match("other", MatchState.PRE, 2_000, home = "Chelsea", mine = false), match("soon", MatchState.PRE, 2_000),
        )
        assertEquals(listOf("live", "soon", "later"), AlertStars.candidates(list, profile, AlertSettings()).map { it.id })
        val off = AlertSettings().withLevel(io.shamash.domain.Follow("team", "Arsenal"), AlertLevel.OFF)
        assertTrue(AlertStars.candidates(list, profile, off).isEmpty())
    }

    @Test fun `MC_12 a star toggles, old stars go, and an unread schedule keeps them`() {
        val listed = listOf(match("a", MatchState.PRE, 1), match("b", MatchState.PRE, 2))
        val starred = AlertStars.toggled(AlertSettings(always = setOf("gone")), "a", listed)
        assertEquals(setOf("a"), starred.always)
        assertEquals(emptySet<String>(), AlertStars.toggled(starred, "a", listed).always)
        assertEquals(setOf("gone", "a"), AlertStars.toggled(AlertSettings(always = setOf("gone")), "a", emptyList()).always)
    }

    @Test fun `MC_12 a starred match shows in quiet hours`() {
        val m = match("a", MatchState.LIVE, 0)
        val quiet = AlertSettings(quiet = QuietHours(enabled = true, startMinute = 0, endMinute = 23 * 60 + 30))
        val starred = AlertStars.toggled(quiet, "a", listOf(m))
        val noon = 12 * 3_600_000L
        assertEquals(false, MatchAlerts.shows(OverlayCard.REMINDER, m, profile, quiet, noon, ZoneId.of("UTC")))
        assertEquals(true, MatchAlerts.shows(OverlayCard.REMINDER, m, profile, starred, noon, ZoneId.of("UTC")))
    }

    @Test fun `MC_15 the time reads in Western digits`() {
        assertEquals("Thu 20:00", AlertStars.whenText(20 * 3_600_000L, ZoneId.of("UTC"), Locale.ENGLISH))
        assertTrue(AlertStars.whenText(20 * 3_600_000L, ZoneId.of("UTC"), Locale.forLanguageTag("ar")).endsWith("20:00"))
    }
}
