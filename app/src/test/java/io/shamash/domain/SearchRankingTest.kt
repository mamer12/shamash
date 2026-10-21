package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankingTest {
    private fun item(title: String, kind: Kind = Kind.MOVIE) =
        MediaItem(id = title, providerId = "tmdb", title = title, kind = kind, open = OpenTarget.App("app"))

    private fun titles(items: List<MediaItem>) = items.map { it.title }

    @Test
    fun `SRCH_03 exact title first, then starts-with, then provider order`() {
        val found = listOf(item("The Dark Knight"), item("Dark Waters"), item("Dark"), item("Into the Dark"))

        val ranked = SearchRanking.rank(found, "dark")

        assertEquals(listOf("Dark", "Dark Waters", "The Dark Knight", "Into the Dark"), titles(ranked))
    }

    @Test
    fun `SRCH_03 ties keep the provider's order`() {
        val found = listOf(item("Lost B"), item("Lost A"), item("Other 2"), item("Other 1"))

        assertEquals(listOf("Lost B", "Lost A", "Other 2", "Other 1"), titles(SearchRanking.rank(found, "lost")))
    }

    @Test
    fun `SRCH_03 case and accents don't matter`() {
        val found = listOf(item("Le Fabuleux Destin d'Amélie Poulain"), item("AMÉLIE"))

        assertEquals("AMÉLIE", SearchRanking.rank(found, "amelie").first().title)
    }

    @Test
    fun `SRCH_03 Arabic diacritics and hamza forms don't matter`() {
        val found = listOf(item("مسلسل آخر"), item("أَحْمَد"))

        assertEquals("أَحْمَد", SearchRanking.rank(found, "احمد").first().title)
    }

    @Test
    fun `SRCH_03 movies and series ranked together`() {
        val found = listOf(item("Dune: Part Two", Kind.MOVIE), item("Dune", Kind.SERIES), item("Dune Prophecy", Kind.SERIES))

        assertEquals(listOf("Dune", "Dune: Part Two", "Dune Prophecy"), titles(SearchRanking.rank(found, "  DUNE ")))
    }

    @Test
    fun `SRCH_03 a blank query keeps the order`() {
        val found = listOf(item("B"), item("A"))

        assertEquals(found, SearchRanking.rank(found, " "))
    }
}
