package io.shamash.data.cinemana

import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CinemaRulesTest {
    private fun title(id: String = "1", genre: String = "Drama") = CinemanaTitle(id, "Title $id", Kind.MOVIE,
        "https://example.test/poster", "", "tt00000$id", genres = listOf(genre), rating = "PG", year = "2025")
    private fun progress(position: Long, duration: Long = 100_000) = CinemaProgress(title(), position, duration, 1)

    @Test fun CIN_04_history_begins_at_three_seconds() {
        assertFalse(progress(2_999).started)
        assertTrue(progress(3_000).started)
        assertEquals(0, progress(2_999).resumeMs)
        assertEquals(3_000, progress(3_000).resumeMs)
    }

    @Test fun CIN_04_finished_at_95_percent_resets_resume() {
        assertEquals(94_999, progress(94_999).resumeMs)
        assertFalse(progress(94_999).finished)
        assertTrue(progress(95_000).finished)
        assertEquals(0, progress(95_000).resumeMs)
        assertEquals(0f, progress(4_000, 0).fraction, 0f)
        assertEquals(1f, progress(110_000).fraction, 0f)
    }

    @Test fun CIN_07_latest_finished_episode_hides_older_unfinished_episode() {
        val first = title("1").copy(rootId = "99", kind = Kind.SERIES)
        val second = title("2").copy(rootId = "99", kind = Kind.SERIES)
        val old = CinemaProgress(first, 4_000, 100_000, 1)
        val latest = CinemaProgress(second, 95_000, 100_000, 2)
        assertTrue(CinemaProgressRules.continuing(listOf(old, latest)).isEmpty())
        assertEquals(listOf(latest.copy(positionMs = 5_000)), CinemaProgressRules.continuing(listOf(old, latest.copy(positionMs = 5_000))))
    }

    @Test fun CIN_07_unstarted_episode_does_not_replace_progress() {
        val old = progress(4_000)
        val peek = old.copy(title = title("2").copy(rootId = "1"), positionMs = 1_000, updatedAt = 2)
        assertEquals(listOf(old), CinemaProgressRules.continuing(listOf(peek, old)))
    }

    @Test fun CIN_04_series_resume_selects_next_episode_after_completion() {
        val episodes = listOf(title("1"), title("2"), title("3"))
        assertEquals(episodes[1], CinemaProgressRules.episode(episodes, listOf(progress(95_000))))
        assertEquals(episodes[0], CinemaProgressRules.episode(episodes, listOf(progress(4_000))))
        assertNull(CinemaProgressRules.episode(emptyList(), emptyList()))
    }

    @Test fun CIN_09_ratings_weight_genres_and_toggle_exclusively() = runTest {
        val store = CinemaStore()
        store.rate(title(), CinemaRating.LOVE)
        assertEquals(3.0, CinemaRecommendations.genres(store.snapshot(), emptySet())["drama"])
        store.rate(title(), CinemaRating.LIKE)
        assertEquals(1, store.snapshot().ratings.size)
        assertEquals(2.0, CinemaRecommendations.genres(store.snapshot(), emptySet())["drama"])
        store.rate(title(), CinemaRating.DISLIKE)
        assertEquals(-3.0, CinemaRecommendations.genres(store.snapshot(), emptySet())["drama"])
        store.rate(title(), CinemaRating.DISLIKE)
        assertTrue(store.snapshot().ratings.isEmpty())
    }

    @Test fun CIN_11_profile_includes_history_and_list_weights() {
        val saved = CinemaSaved(history = listOf(progress(95_000)), myList = listOf(title()))
        assertEquals(5.0, CinemaRecommendations.genres(saved, setOf("Drama"))["drama"])
    }

    @Test fun CIN_11_tmdb_boosts_are_four_and_two_point_five() {
        assertEquals(4.0, CinemaRating.LOVE.recommendationWeight, 0.0)
        assertEquals(2.5, CinemaRating.LIKE.recommendationWeight, 0.0)
        val saved = CinemaSaved(ratings = listOf(RatedTitle(title("4"), CinemaRating.DISLIKE)))
        val picks = CinemaRecommendations.picks(listOf(title("1"), title("2"), title("4")), saved,
            Profile(), mapOf("1" to 2.5, "2" to 4.0, "4" to 100.0))
        assertEquals(listOf("2", "1"), picks.map { it.id })
    }

    @Test fun CIN_10_because_seeds_loved_then_watched_then_liked_and_deduplicate_series() {
        val saved = CinemaSaved(history = listOf(progress(5_000)), ratings = listOf(
            RatedTitle(title("2"), CinemaRating.LIKE), RatedTitle(title("3"), CinemaRating.LOVE),
            RatedTitle(title("4").copy(rootId = "3"), CinemaRating.LOVE)))
        assertEquals(listOf("3", "1", "2"), CinemaRecommendations.seeds(saved).map { it.title.id })
        assertEquals(listOf("loved", "watched", "liked"), CinemaRecommendations.seeds(saved).map { it.reason })
    }

    @Test fun CIN_11_only_three_positive_seeds_and_loves_first() {
        val saved = CinemaSaved(ratings = (1..5).map { RatedTitle(title(it.toString()), if (it == 5) CinemaRating.LOVE else CinemaRating.LIKE) })
        assertEquals(listOf("5", "1", "2"), CinemaRecommendations.boostSeeds(saved).map { it.title.id })
    }

    @Test fun CIN_10_home_row_order_matches_spec() {
        val ids = listOf("rest", "because_2", "cinemana_my_list", "cinemana_top_series", "cinemana_picks", "because_1",
            "continue_watching", "cinemana_top_movies", "because_3", "because_4")
        val item = MediaItem("1", "cinemana", "Title", Kind.MOVIE, OpenTarget.App("io.shamash"))
        val rows = ids.map { Shelf(it, it, ShelfStyle.POSTER, listOf(item)) }
        assertEquals(listOf("continue_watching", "cinemana_picks", "because_2", "cinemana_top_movies", "cinemana_top_series",
            "because_1", "because_3", "cinemana_my_list", "rest"), CinemaRecommendations.order(rows).map { it.id })
    }

    @Test fun CIN_01_matching_normalizes_names_but_checks_year_and_kind() {
        val movie = title().copy(title = "Spider-Man", year = "2020")
        assertTrue(CinemaRecommendations.matches(movie, NameYear("spider man", "2021"), false))
        assertFalse(CinemaRecommendations.matches(movie, NameYear("spider man", "2018"), false))
        assertFalse(CinemaRecommendations.matches(movie, NameYear("spider man", "2020"), true))
    }

    @Test fun CIN_08_mapping_and_unknown_ratings_fail_closed() {
        assertEquals(listOf("0", "2", "3"), ContentLevel.entries.map(CinemanaLevel::of))
        assertFalse(CinemaFilter.allows(title().copy(rating = ""), ContentLevel.FAMILY))
        assertFalse(CinemaFilter.allows(title().copy(rating = "R"), ContentLevel.FAMILY))
        assertFalse(CinemaFilter.allows(title().copy(rating = "PG-13"), ContentLevel.KIDS))
        assertTrue(CinemaFilter.allows(title().copy(rating = "TV-14"), ContentLevel.FAMILY))
        assertTrue(CinemaFilter.allows(title().copy(rating = "TV-Y"), ContentLevel.KIDS))
    }

    @Test fun CIN_07_parallel_writes_do_not_drop_list_entries() = runTest {
        val store = CinemaStore()
        (1..20).map { id -> async { store.toggleList(title(id.toString())) } }.awaitAll()
        assertEquals(20, store.snapshot().myList.size)
        store.toggleList(title())
        assertEquals(19, store.snapshot().myList.size)
        store.progress(title(), 2_999, 100_000)
        assertTrue(store.snapshot().history.isEmpty())
        store.progress(title(), 3_000, 100_000)
        assertEquals(3_000, store.snapshot().history.single().positionMs)
    }
}
