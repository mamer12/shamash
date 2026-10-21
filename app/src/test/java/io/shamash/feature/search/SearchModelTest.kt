package io.shamash.feature.search

import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchModelTest {
    private class FakeProvider(
        override val id: String,
        private val enabled: Boolean = true,
        private val latency: Long = 0,
        private val answer: (String) -> List<MediaItem> = { q -> listOf(item("$id $q", id)) },
    ) : Provider {
        val queries = mutableListOf<String>()
        override fun isEnabled(profile: Profile) = enabled
        override suspend fun shelves(profile: Profile) = emptyList<Shelf>()
        override suspend fun featured(profile: Profile) = emptyList<MediaItem>()
        override suspend fun search(query: String, profile: Profile): List<MediaItem> {
            queries += query
            delay(latency)
            return answer(query)
        }
    }

    private fun TestScope.model(vararg providers: Provider) = SearchModel(providers.toList(), { Profile() }, this)

    @Test
    fun `SRCH_02 searches 900 ms after typing stops`() = runTest {
        val provider = FakeProvider("tmdb")
        val model = model(provider)

        model.type("du")
        advanceTimeBy(500)
        model.type("dune")
        advanceTimeBy(899)
        runCurrent()
        assertTrue(provider.queries.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("dune"), provider.queries)
    }

    @Test
    fun `SRCH_02 Enter searches at once and focuses the first result`() = runTest {
        val provider = FakeProvider("tmdb")
        val model = model(provider)

        model.submit("dune")
        runCurrent()

        assertEquals(listOf("dune"), provider.queries)
        assertTrue(model.state.value.focusResults)
        assertEquals("tmdb dune", model.state.value.rows.single().items.single().title)
    }

    @Test
    fun `SRCH_02 a newer search cancels the older one`() = runTest {
        val slow = FakeProvider("tmdb", latency = 5_000)
        val model = model(slow)

        model.submit("first")
        advanceTimeBy(1_000)
        model.submit("second")
        advanceUntilIdle()

        assertEquals(listOf("tmdb second"), model.state.value.rows.flatMap { row -> row.items.map { it.title } })
        assertFalse(model.state.value.searching)
    }

    @Test
    fun `SRCH_01 one row per provider and app, in the providers' order, disabled ones skipped`() = runTest {
        val tmdb = FakeProvider("tmdb") { q -> listOf(item("$q A", "tmdb", "netflix"), item("$q B", "tmdb", "shahid"), item("$q C", "tmdb", "netflix")) }
        val youtube = FakeProvider("youtube", latency = 100)
        val off = FakeProvider("cinemana", enabled = false)
        val model = model(youtube, off, tmdb)

        model.submit("dune")
        advanceUntilIdle()

        val rows = model.state.value.rows
        assertEquals(listOf("youtube:", "tmdb:netflix", "tmdb:shahid"), rows.map { it.key })
        assertEquals(listOf("dune A", "dune C"), rows[1].items.map { it.title })
        assertTrue(off.queries.isEmpty())
        assertTrue(model.state.value.searched)
    }

    @Test
    fun `SRCH_01 a failing provider leaves the others' rows`() = runTest {
        val broken = FakeProvider("youtube") { error("offline") }
        val model = model(broken, FakeProvider("tmdb"))

        model.submit("dune")
        advanceUntilIdle()

        assertEquals(listOf("tmdb:"), model.state.value.rows.map { it.key })
    }

    @Test
    fun `SRCH_03 rows are ranked by the query`() = runTest {
        val tmdb = FakeProvider("tmdb") { listOf(item("The Dune Story", "tmdb"), item("Dune", "tmdb")) }
        val model = model(tmdb)

        model.submit("dune")
        advanceUntilIdle()

        assertEquals(listOf("Dune", "The Dune Story"), model.state.value.rows.single().items.map { it.title })
    }

    @Test
    fun `SRCH_02 a query shorter than two letters clears the results`() = runTest {
        val provider = FakeProvider("tmdb")
        val model = model(provider)
        model.submit("dune")
        advanceUntilIdle()

        model.type("d")
        advanceUntilIdle()

        assertTrue(model.state.value.rows.isEmpty())
        assertEquals(listOf("dune"), provider.queries)
    }

    @Test
    fun `SRCH_04 the focus request is taken once`() = runTest {
        val model = model(FakeProvider("tmdb"))
        model.submit("dune")
        advanceUntilIdle()

        model.focusTaken()

        assertFalse(model.state.value.focusResults)
    }

    private companion object {
        fun item(title: String, provider: String, app: String? = null) = MediaItem(
            id = title, providerId = provider, title = title, kind = Kind.MOVIE,
            open = OpenTarget.App(app ?: provider), appPackage = app,
        )
    }
}
