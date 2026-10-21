package io.shamash.data.cinemana

import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.domain.ContentLevel
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CinemaApiTest {
    @Test fun CIN_08_every_endpoint_carries_the_active_family_level() = runTest {
        MockWebServer().use { server ->
            val api = CinemanaApi(Http(null), baseUrl = server.url("/").toString())
            repeat(9) { server.enqueue(MockResponse().setBody("[]")) }
            api.banner(ContentLevel.KIDS)
            api.groups(ContentLevel.KIDS, true)
            api.newly(ContentLevel.KIDS, 12)
            api.latest(ContentLevel.KIDS, false)
            api.search(ContentLevel.KIDS, "test", true)
            api.details("12", ContentLevel.KIDS)
            api.episodes("12", ContentLevel.KIDS)
            api.files("12", ContentLevel.KIDS)
            api.subtitles("12", ContentLevel.KIDS)
            repeat(9) {
                val url = server.takeRequest().requestUrl
                assertTrue(url?.encodedPath?.contains("/level/3") == true || url?.queryParameter("level") == "3")
            }
        }
    }

    @Test fun CIN_08_switching_level_never_reuses_an_unfiltered_detail_response() = runTest {
        MockWebServer().use { server ->
            val api = CinemanaApi(Http(null), baseUrl = server.url("/").toString())
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            api.details("12", ContentLevel.ALL)
            api.details("12", ContentLevel.KIDS)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun CIN_03_detail_and_episode_fixtures_parse() {
        val movie = CinemanaJson.details(Fixtures.read("cinemana/info_movie.json"))
        val series = CinemanaJson.details(Fixtures.read("cinemana/info_series.json"))
        assertNotNull(movie)
        assertTrue(series?.title?.isSeries == true)
        val episodes = CinemanaJson.episodes(Fixtures.read("cinemana/season.json"), "123")
        assertTrue(episodes.isNotEmpty())
        assertEquals(episodes.sortedWith(compareBy({ it.season }, { it.episode })), episodes)
        assertTrue(episodes.all { it.rootId == "123" || it.id == "123" })
    }

    @Test fun CIN_04_file_and_subtitle_fixtures_parse() {
        val files = CinemanaJson.files(Fixtures.read("cinemana/files.json"))
        assertTrue(files.isNotEmpty())
        assertEquals(files.sortedBy { it.height }, files)
        val subs = CinemanaJson.subtitles(Fixtures.read("cinemana/translations.json"))
        assertNotNull(subs.arabic)
        assertNotNull(subs.english)
    }

    @Test fun CIN_07_addon_items_use_title_roots_and_play_keeps_episode_id() {
        val http = Http(null)
        val library = CinemaLibrary(CinemanaApi(http), CinemanaTmdb(http), http, CinemaStore())
        val episode = CinemanaJson.episodes(Fixtures.read("cinemana/season.json"), "123").first()
        val title = library.item(episode).open as OpenTarget.AppPage
        val play = library.item(episode, .4f, play = true)
        assertEquals("shamash://cinemana/title/123", title.uri)
        assertEquals("shamash://cinemana/play/${episode.id}", (play.open as OpenTarget.AppPage).uri)
        assertEquals(.4f, play.progress)
    }

    @Test fun CIN_07_disabled_addon_keeps_external_watchme_provider_available() {
        val http = Http(null)
        val provider = CinemanaProvider(CinemanaApi(http), CinemanaTmdb(http), { it == "io.watchmeplus" })
        assertTrue(provider.isEnabled(Profile()))
        val absent = CinemanaProvider(CinemanaApi(http), CinemanaTmdb(http), { false })
        assertFalse(absent.isEnabled(Profile()))
        assertTrue(absent.isEnabled(Profile(addons = setOf("cinemana"))))
    }
}
