package io.shamash.data.cinemana

import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.domain.ContentGate
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemanaGateTest {
    private fun search(level: ContentLevel, body: String) = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setBody("[]"))
            val http = Http(null)
            val provider = CinemanaProvider(CinemanaApi(http, baseUrl = server.url("/").toString()), CinemanaTmdb(http), { false })
            found = provider.search("x", Profile(contentLevel = level))
        }
    }

    private var found = emptyList<io.shamash.domain.MediaItem>()

    @Test fun PRF_12_cinemana_search_is_gated_even_if_the_server_ignores_the_level() {
        val body = Fixtures.read("cinemana/search_movies.json")
        val titles = CinemanaJson.titles(body)
        assertTrue(titles.isNotEmpty())
        ContentLevel.entries.forEach { level ->
            search(level, body)
            val expected = titles.count { ContentGate.allowsRating(it.rating, level) }
            assertEquals(level.name, expected, found.size)
            assertTrue(found.all { ContentGate.allows(it, level) })
        }
    }

    @Test fun PRF_12_cinemana_items_carry_their_rating() {
        search(ContentLevel.ALL, Fixtures.read("cinemana/search_movies.json"))
        val titles = CinemanaJson.titles(Fixtures.read("cinemana/search_movies.json")).associateBy { "cinemana:${it.pageId}" }
        found.forEach { assertEquals(titles.getValue(it.id).rating.ifBlank { null }, it.rating) }
    }
}
