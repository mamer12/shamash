package io.shamash.data.tod

import io.shamash.data.Fixtures
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

class TodTest {
    private val sports = TodJson.slider(Fixtures.read("tod/sports.rsc"))
    private val movies = TodJson.slider(Fixtures.read("tod/movies.rsc"))

    @Test
    fun PRV_09_reads_the_featured_list_inside_the_page() {
        assertEquals(5, sports.size)
        val psg = sports.first()
        assertEquals("Paris Saint-Germain vs Barcelona", psg.title)
        assertEquals("GAME", psg.contentType)
        assertEquals("UEFA Champions League", psg.competition)
        assertEquals(epoch("2026-10-20T19:02:46+00:00"), psg.startsAt)
    }

    @Test
    fun PRV_09_image_links_follow_tods_resizer() {
        val url = sports.first().image.url(1280, 720)
        assertEquals(
            "https://img.tod.tv/resources/images/link/162921f2-b6fe-3bd1-bbfa-4aabca115075/" +
                "044db062-cf07-54cb-b147-16a2c4d033cd/639274828373630000/4:0:1912:1080/1280x720/" +
                "a51c7bc5-35f8-48df-8f09-d4a51a605f04_tile-169-en.jpg",
            url,
        )
    }

    @Test
    fun PRV_09_matches_started_over_2h_ago_are_dropped_soonest_first() {
        val now = epoch("2026-10-20T21:02:00+00:00")

        val matches = TodCatalog.matches(sports, now)

        assertEquals(
            listOf("Paris Saint-Germain vs Barcelona", "New York Knicks Vs Philadelphia 76ers", "Barcelona vs Real Madrid"),
            matches.map { it.title },
        )
        assertEquals(listOf(Kind.LIVE, Kind.MATCH, Kind.MATCH), matches.map { it.kind })
        assertEquals(epoch("2026-10-20T23:00:00+00:00"), matches[1].startsAt)
        assertTrue(matches.all { it.backdropUrl!!.contains("/1280x720/") && it.posterUrl!!.contains("/640x360/") })
    }

    @Test
    fun PRV_10_tod_titles_open_the_app() {
        val titles = TodCatalog.titles(movies, emptyList())
        assertEquals(listOf("GOAT", "Welad Rizk 3: El Qadia", "Good Fortune"), titles.map { it.title })
        assertTrue(titles.all { it.open == OpenTarget.App("com.todtv.tod") && it.kind == Kind.MOVIE })
    }

    @Test
    fun json_object_ends_at_its_own_brace_even_with_braces_in_strings() {
        val text = "x:{\"a\":\"}{\\\"\",\"b\":{\"c\":1}}\ny:{\"d\":2}"
        assertEquals("{\"a\":\"}{\\\"\",\"b\":{\"c\":1}}", TodJson.objectAt(text, 2))
    }

    @Test(expected = IOException::class)
    fun a_page_without_the_featured_list_is_an_error() {
        TodJson.slider("<html>Request Rejected</html>")
    }

    private fun epoch(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}
