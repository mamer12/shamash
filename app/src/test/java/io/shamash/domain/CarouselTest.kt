package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun item(provider: String, n: Int, wide: Boolean = true) = MediaItem(
        id = "$provider$n", providerId = provider, title = "$provider $n", kind = Kind.MOVIE,
        open = OpenTarget.App("a"), backdropUrl = if (wide) "https://img/$provider$n.jpg" else null,
    )

    private fun match(id: String, mine: Boolean, state: MatchState, startsAt: Long, home: String = "Home") = Match(
        id = id, competition = "esp.1", competitionName = "LaLiga",
        home = Team(home, "H", ""), away = Team("Away", "A", ""), startsAt = startsAt, state = state, mine = mine,
    )

    @Test fun HOME_10_providers_take_turns_in_order() {
        val featured = mapOf(
            "tod" to listOf(item("tod", 1), item("tod", 2)),
            "tmdb" to listOf(item("tmdb", 1)),
            "youtube" to listOf(item("youtube", 1), item("youtube", 2)),
            "cinemana" to listOf(item("cinemana", 1)),
        )
        val ids = Carousel.mix(null, featured).map { it.id }
        assertEquals(listOf("cinemana1", "youtube1", "tmdb1", "tod1", "youtube2", "tod2"), ids)
    }

    @Test fun HOME_10_match_leads() {
        val lead = Carousel.matchItem(match("m", true, MatchState.PRE, now + hour))
        val mixed = Carousel.mix(lead, mapOf("tmdb" to listOf(item("tmdb", 1))))
        assertEquals("match:m", mixed.first().id)
    }

    @Test fun HOME_10_only_wide_art() {
        val mixed = Carousel.mix(null, mapOf("tmdb" to listOf(item("tmdb", 1, wide = false), item("tmdb", 2))))
        assertEquals(listOf("tmdb2"), mixed.map { it.id })
    }

    @Test fun HOME_10_at_most_16() {
        val many = mapOf("tmdb" to (1..30).map { item("tmdb", it) }, "youtube" to (1..30).map { item("youtube", it) })
        assertEquals(16, Carousel.mix(null, many).size)
    }

    @Test fun HOME_10_unknown_providers_come_after_known_ones() {
        val mixed = Carousel.mix(null, mapOf("other" to listOf(item("other", 1)), "tod" to listOf(item("tod", 1))))
        assertEquals(listOf("tod1", "other1"), mixed.map { it.id })
    }

    @Test fun MC_03_match_day_prefers_live_then_next_kickoff() {
        val matches = listOf(
            match("soon", true, MatchState.PRE, now + hour),
            match("live", true, MatchState.LIVE, now - hour),
        )
        assertEquals("live", Carousel.matchDay(matches, now)?.id)
    }

    @Test fun MC_03_no_match_day_beyond_14_hours() {
        assertNull(Carousel.matchDay(listOf(match("far", true, MatchState.PRE, now + 20 * hour)), now))
    }

    @Test fun MC_03_match_billboard_opens_tod_by_default() {
        val item = Carousel.matchItem(match("m", true, MatchState.PRE, now))
        assertEquals(OpenTarget.App(Carousel.MATCH_APP), item.open)
        assertEquals(Kind.MATCH, item.kind)
    }

    @Test fun MC_01_matches_row_puts_mine_first_then_by_kickoff() {
        val matches = listOf(
            match("a", false, MatchState.PRE, now + hour),
            match("b", true, MatchState.PRE, now + 3 * hour),
            match("c", false, MatchState.PRE, now),
        )
        assertEquals(listOf("b", "c", "a"), Carousel.matchRow(matches).map { it.id })
    }

    @Test fun MC_01_mine_follows_the_profile_teams() {
        val profile = Profile(teams = setOf("barcelona"))
        val marked = Carousel.markMine(listOf(match("x", false, MatchState.PRE, now, home = "FC Barcelona")), profile)
        assertTrue(marked.single().mine)
    }
}
