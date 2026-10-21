package io.shamash.data.justwatch

import io.shamash.data.Fixtures
import io.shamash.data.tmdb.StreamingService
import io.shamash.domain.Kind
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JustWatchJsonTest {
    private val answer = Fixtures.read("justwatch/popular_titles.json")

    @Test
    fun PRV_05_finds_the_apple_tv_page_by_tmdb_id() {
        val link = JustWatchJson.link(answer, 95396, Kind.SERIES, StreamingService.APPLE_TV)
        assertTrue(link!!.startsWith("https://tv.apple.com/"))
    }

    @Test
    fun PRV_05_needs_the_same_kind_of_title() {
        assertNull(JustWatchJson.link(answer, 95396, Kind.MOVIE, StreamingService.APPLE_TV))
    }

    @Test
    fun PRV_05_no_subscription_offer_on_that_service_means_no_link() {
        assertNull(JustWatchJson.link(answer, 95396, Kind.SERIES, StreamingService.NETFLIX))
        assertNull(JustWatchJson.link(answer, 549624, Kind.MOVIE, StreamingService.NETFLIX))
    }

    @Test
    fun PRV_05_unknown_title_has_no_link() {
        assertNull(JustWatchJson.link(answer, 1, Kind.MOVIE, StreamingService.APPLE_TV))
        assertNull(JustWatchJson.link("{\"data\":null}", 95396, Kind.SERIES, StreamingService.APPLE_TV))
    }
}
