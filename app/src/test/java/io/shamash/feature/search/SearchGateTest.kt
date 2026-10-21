package io.shamash.feature.search

import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchGateTest {
    private fun item(id: String, rating: String?) =
        MediaItem(id, "tmdb", id, Kind.MOVIE, OpenTarget.App("x"), appPackage = "x", rating = rating)

    private val provider = object : Provider {
        override val id = "tmdb"
        override fun isEnabled(profile: Profile) = true
        override suspend fun shelves(profile: Profile) = emptyList<Shelf>()
        override suspend fun featured(profile: Profile) = emptyList<MediaItem>()
        override suspend fun search(query: String, profile: Profile) =
            listOf(item("kid movie", "G"), item("adult movie", "R"), item("unrated movie", null))
    }

    private fun titles(level: ContentLevel) = runTest {
        val model = SearchModel(listOf(provider), { Profile(contentLevel = level) }, this)
        model.submit("movie")
        advanceUntilIdle()
        result = model.state.value.rows.flatMap { r -> r.items.map { it.id } }.sorted()
    }

    private var result: List<String> = emptyList()

    @Test fun PRF_12_search_results_pass_the_gate() {
        titles(ContentLevel.ALL)
        assertEquals(listOf("adult movie", "kid movie", "unrated movie"), result)
        titles(ContentLevel.FAMILY)
        assertEquals(listOf("kid movie"), result)
        titles(ContentLevel.KIDS)
        assertEquals(listOf("kid movie"), result)
    }
}
