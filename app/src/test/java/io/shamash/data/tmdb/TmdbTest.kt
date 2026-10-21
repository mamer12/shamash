package io.shamash.data.tmdb

import io.shamash.data.Fixtures
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {
    private val movies = TmdbJson.titles(Fixtures.read("tmdb/discover_movie.json"), Kind.MOVIE)
    private val shows = TmdbJson.titles(Fixtures.read("tmdb/discover_tv.json"), Kind.SERIES)

    @Test
    fun PRV_01_services_map_by_name_without_channels_or_stores() {
        val ids = TmdbJson.serviceIds(Fixtures.read("tmdb/watch_providers_US.json"))

        assertEquals(listOf(350), ids[StreamingService.APPLE_TV])
        assertEquals(listOf(8, 175, 1796), ids[StreamingService.NETFLIX])
        assertEquals(listOf(337), ids[StreamingService.DISNEY_PLUS])
        assertEquals(listOf(1899), ids[StreamingService.MAX])
        assertEquals(listOf(1715), ids[StreamingService.SHAHID])
    }

    @Test
    fun PRV_01_regional_services_are_found() {
        val ids = TmdbJson.serviceIds(Fixtures.read("tmdb/watch_providers_IQ.json"))

        assertEquals(listOf(629), ids[StreamingService.OSN_PLUS])
        assertEquals(listOf(1750), ids[StreamingService.TOD])
        assertEquals(listOf(119), ids[StreamingService.PRIME_VIDEO])
    }

    @Test
    fun PRV_01_provider_names_match_whole_services() {
        assertNull(StreamingService.fromProviderName("Apple TV Store"))
        assertNull(StreamingService.fromProviderName("Starz Apple TV channel"))
        assertNull(StreamingService.fromProviderName("DisneyNOW"))
        assertNull(StreamingService.fromProviderName("Todoist TV"))
        assertEquals(StreamingService.APPLE_TV, StreamingService.fromProviderName("Apple TV+"))
        assertEquals(StreamingService.MAX, StreamingService.fromProviderName("Max"))
    }

    @Test
    fun PRV_02_popular_interleaves_movies_and_shows() {
        val row = TmdbCatalog.popular(ServiceTitles(StreamingService.APPLE_TV, movies, shows))

        assertEquals(listOf("Matchbox the Movie", "Slow Horses", "Tenzing", "Ted Lasso"), row.take(4).map { it.title })
        assertEquals(16, row.size)
        assertTrue(row.all { it.backdropUrl!!.startsWith("https://image.tmdb.org/t/p/w1280/") })
        assertTrue(row.all { it.subtitle == "Apple TV" })
    }

    @Test
    fun PRV_02_popular_keeps_at_most_20() {
        val many = (1..15).flatMap { n -> movies.map { it.copy(id = it.id * 100 + n, title = "${it.title} $n") } }
        assertEquals(20, TmdbCatalog.popular(ServiceTitles(StreamingService.NETFLIX, many, shows)).size)
    }

    @Test
    fun PRV_03_top10_is_by_popularity_across_services_and_distinct() {
        val apple = ServiceTitles(StreamingService.APPLE_TV, movies, shows)
        val netflix = ServiceTitles(StreamingService.NETFLIX, emptyList(), shows.take(2))

        val top = TmdbCatalog.topTen(listOf(apple, netflix))

        assertEquals(10, top.size)
        assertEquals(listOf("Matchbox the Movie", "Slow Horses", "Ted Lasso", "Silo"), top.take(4).map { it.title })
        assertEquals(top.size, top.map { it.title }.distinct().size)
        assertTrue(top.all { it.posterUrl!!.startsWith("https://image.tmdb.org/t/p/w342/") })
    }

    @Test
    fun PRV_04_v3_key_goes_as_api_key_and_jwt_as_bearer() {
        val v3 = TmdbAuth.of("0123456789abcdef0123456789abcdef")
        assertEquals(mapOf("api_key" to "0123456789abcdef0123456789abcdef"), v3.query)
        assertTrue(v3.headers.isEmpty())

        val v4 = TmdbAuth.of(" eyJhbGciOiJIUzI1NiJ9.e30.sig ")
        assertTrue(v4.query.isEmpty())
        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig", v4.headers["Authorization"])
    }

    @Test
    fun PRV_05_apple_tv_and_netflix_titles_look_up_their_page_others_open_the_app() {
        val severance = shows.first { it.title == "Severance" }

        val apple = with(TmdbCatalog) { severance.toItem(StreamingService.APPLE_TV) }
        val prime = with(TmdbCatalog) { severance.toItem(StreamingService.PRIME_VIDEO) }

        assertEquals(OpenTarget.Lookup(95396, Kind.SERIES, "Severance", StreamingService.APPLE_TV.appPackage), apple.open)
        assertEquals(OpenTarget.App(StreamingService.PRIME_VIDEO.appPackage), prime.open)
        assertEquals("tmdb:series:95396", apple.id)
    }

    @Test
    fun HOME_10_featured_puts_apple_tv_first_and_needs_wide_art() {
        val netflix = TmdbCatalog.popular(ServiceTitles(StreamingService.NETFLIX, movies, emptyList()))
        val apple = TmdbCatalog.popular(ServiceTitles(StreamingService.APPLE_TV, emptyList(), shows))

        val featured = TmdbCatalog.featured(linkedMapOf(StreamingService.NETFLIX to netflix, StreamingService.APPLE_TV to apple))

        assertEquals(8, featured.size)
        assertEquals(listOf("Slow Horses", "Matchbox the Movie"), featured.take(2).map { it.title })
    }

    @Test
    fun SRCH_01_search_keeps_titles_on_my_services_grouped_by_service() {
        val hits = TmdbJson.search(Fixtures.read("tmdb/search_multi.json"))
        val appleOffers = TmdbJson.flatrate(Fixtures.read("tmdb/tv_watch_providers.json"), "us")
        assertEquals(listOf(StreamingService.APPLE_TV), appleOffers)
        assertTrue(hits.none { it.title == "Joan Severance" })

        val offers = hits.map { hit ->
            hit to when (hit.id) {
                95396 -> appleOffers
                5072 -> listOf(StreamingService.PRIME_VIDEO)
                348669 -> listOf(StreamingService.NETFLIX)
                else -> emptyList()
            }
        }
        val mine = listOf(StreamingService.NETFLIX, StreamingService.APPLE_TV)

        val results = TmdbCatalog.bySearch(offers, mine)

        assertEquals(listOf("tmdb:movie:348669", "tmdb:series:95396"), results.map { it.id })
    }

    @Test
    fun flatrate_is_empty_for_a_region_without_offers() {
        assertTrue(TmdbJson.flatrate(Fixtures.read("tmdb/tv_watch_providers.json"), "IQ").isEmpty())
    }

    @Test
    fun PRV_13_find_by_imdb_gives_the_wide_picture() {
        val found = TmdbJson.found(Fixtures.read("tmdb/find_imdb.json"))
        assertEquals(278, found?.id)
        assertEquals("https://image.tmdb.org/t/p/w1280/pNjh59JSxChQktamG3LMp9ZoQzp.jpg", TmdbImages.wide(found?.backdropPath))
    }
}
