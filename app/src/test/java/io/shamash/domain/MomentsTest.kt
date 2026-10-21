package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentsTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun item(title: String, kind: Kind = Kind.MOVIE, progress: Float? = null) =
        MediaItem(id = title, providerId = "p", title = title, kind = kind, open = OpenTarget.App("a"), progress = progress)

    private fun match(id: String, mine: Boolean, state: MatchState, startsAt: Long) = Match(
        id = id, competition = "esp.1", competitionName = "LaLiga",
        home = Team("Home $id", "H", ""), away = Team("Away $id", "A", ""),
        startsAt = startsAt, state = state, mine = mine,
    )

    @Test fun HOME_06_morning_is_5_to_12() {
        (5..11).forEach { assertEquals(Moment.MORNING, Moment.at(it)) }
    }

    @Test fun HOME_06_afternoon_is_12_to_17() {
        (12..16).forEach { assertEquals(Moment.AFTERNOON, Moment.at(it)) }
    }

    @Test fun HOME_06_evening_is_17_to_23() {
        (17..22).forEach { assertEquals(Moment.EVENING, Moment.at(it)) }
    }

    @Test fun HOME_06_night_is_23_to_5() {
        listOf(23, 0, 1, 2, 3, 4).forEach { assertEquals(Moment.NIGHT, Moment.at(it)) }
    }

    @Test fun HOME_07_greeting_carries_the_name() {
        assertEquals(Greeting(Moment.MORNING, "Sara"), Moments.greeting(Moment.MORNING, " Sara "))
    }

    @Test fun HOME_07_no_name_means_no_name_part() {
        assertNull(Moments.greeting(Moment.AFTERNOON, "  ").name)
    }

    @Test fun HOME_07_late_night_says_good_evening() {
        assertEquals(Moment.EVENING, Moments.greeting(Moment.NIGHT, "").moment)
    }

    @Test fun HOME_09_night_dims_35_percent_more() {
        assertEquals(0.35f, Moments.nightDim(Moment.NIGHT))
        assertEquals(0f, Moments.nightDim(Moment.EVENING))
    }

    @Test fun HOME_08_matches_within_14_hours_and_live_only_mine() {
        val matches = listOf(
            match("later", mine = true, state = MatchState.PRE, startsAt = now + 15 * hour),
            match("soon", mine = true, state = MatchState.PRE, startsAt = now + 2 * hour),
            match("live", mine = true, state = MatchState.LIVE, startsAt = now - hour),
            match("other", mine = false, state = MatchState.PRE, startsAt = now + hour),
            match("done", mine = true, state = MatchState.DONE, startsAt = now - 3 * hour),
        )
        assertEquals(listOf("live", "soon"), Moments.mineSoon(matches, now).map { it.id })
    }

    @Test fun HOME_08_first_unfinished_skips_videos_and_finished() {
        val watching = listOf(
            item("clip", Kind.VIDEO),
            item("done", progress = 0.99f),
            item("show", Kind.SERIES, progress = 0.4f),
        )
        assertEquals("show", Moments.firstUnfinished(watching)?.title)
    }

    @Test fun HOME_08_order_matches_unfinished_personal_youtube() {
        val row = Moments.row(Moment.EVENING, listOf(item("Match")), item("Show"), item("Pick"), item("Clip", Kind.VIDEO))
        assertEquals(listOf("Match", "Show", "Pick", "Clip"), row.map { it.title })
    }

    @Test fun HOME_08_morning_puts_youtube_first() {
        val row = Moments.row(Moment.MORNING, emptyList(), item("Show"), item("Pick"), item("Clip", Kind.VIDEO))
        assertEquals(listOf("Clip", "Show", "Pick"), row.map { it.title })
    }

    @Test fun HOME_08_each_title_once_and_missing_picks_skipped() {
        val row = Moments.row(Moment.AFTERNOON, emptyList(), item("Dune"), item("dune"), null)
        assertEquals(listOf("Dune"), row.map { it.title })
    }
}
