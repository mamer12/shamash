package io.shamash.data.cinemana

import io.shamash.data.Fixtures
import io.shamash.domain.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemanaJsonTest {
    @Test
    fun PRV_13_new_titles_have_posters_and_imdb_ids() {
        val titles = CinemanaJson.titles(Fixtures.read("cinemana/newly.json"))

        assertEquals(listOf("Sacrifice", "Tenzing", "The Storm", "Trapezium"), titles.map { it.title })
        val first = titles.first()
        assertEquals("3150062", first.id)
        assertEquals(Kind.MOVIE, first.kind)
        assertEquals("tt32268992", first.imdbId)
        assertTrue(first.poster.endsWith("_poster_medium_thumb.jpg"))
        assertTrue(first.overview.isNotBlank())
    }

    @Test
    fun PRV_13_featured_groups_are_movies_and_series() {
        val groups = CinemanaJson.featuredGroups(Fixtures.read("cinemana/groups.json"))

        assertEquals(2, groups.size)
        assertEquals(listOf("Sacrifice", "Tenzing", "The Storm"), groups[0].map { it.title })
        assertTrue(groups[1].all { it.kind == Kind.SERIES })
    }

    @Test
    fun PRV_13_search_results_read_imdb_links_with_trailing_slash() {
        val results = CinemanaJson.titles(Fixtures.read("cinemana/search_movies.json"))
        assertEquals(listOf("tt0096895", "tt0118688", "tt1877830"), results.map { it.imdbId })
    }

    @Test
    fun PRV_13_null_words_and_missing_posters_are_skipped() {
        val text = """[{"nb":"1","en_title":"null","ar_title":"عنوان","imgObjUrl":"https://x/p.jpg","en_content":"null"},
            {"nb":"2","en_title":"No poster","imgMediumThumbObjUrl":""}]"""

        val titles = CinemanaJson.titles(text)

        assertEquals(1, titles.size)
        assertEquals("عنوان", titles[0].title)
        assertEquals("https://x/p.jpg", titles[0].poster)
        assertEquals("", titles[0].overview)
    }
}
