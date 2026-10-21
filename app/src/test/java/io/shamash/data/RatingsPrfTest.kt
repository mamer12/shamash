package io.shamash.data

import io.shamash.data.tmdb.TmdbJson
import io.shamash.data.tod.TodCatalog
import io.shamash.data.tod.TodImage
import io.shamash.data.tod.TodTile
import io.shamash.data.youtube.YouTubeJson
import io.shamash.data.youtube.YouTubeMix.toItem
import io.shamash.domain.ContentGate
import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Providers set MediaItem.rating so the strict ContentGate keeps FAMILY and KIDS usable. SPEC: PRF-12 */
class RatingsPrfTest {
    private val movieDates = """{"id":1,"results":[
        {"iso_3166_1":"US","release_dates":[{"certification":"","type":1},{"certification":"PG-13","type":3}]},
        {"iso_3166_1":"GB","release_dates":[{"certification":"12A","type":3}]}]}"""
    private val tvRatings = """{"id":2,"results":[{"iso_3166_1":"US","rating":"TV-Y7"},{"iso_3166_1":"DE","rating":"6"}]}"""

    @Test fun PRF_12_tmdb_reads_the_movie_certification_skipping_blank_ones() {
        assertEquals("PG-13", TmdbJson.certification(movieDates, Kind.MOVIE, "US"))
    }

    @Test fun PRF_12_tmdb_prefers_the_region_then_the_us_and_maps_local_labels() {
        assertEquals("PG-13", TmdbJson.certification(movieDates, Kind.MOVIE, "gb"))   // 12A
        assertEquals("PG", TmdbJson.certification(tvRatings, Kind.SERIES, "de"))   // the region first
        assertEquals("PG", Certification.normalize("6"))
        assertEquals("TV-Y7", TmdbJson.certification(tvRatings, Kind.SERIES, "FR"))   // falls back to US
    }

    @Test fun PRF_12_tmdb_unknown_stays_null_so_it_is_hidden() {
        assertNull(TmdbJson.certification("""{"results":[]}""", Kind.MOVIE, "US"))
        assertNull(TmdbJson.certification("""{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"NR"}]}]}""", Kind.MOVIE, "US"))
        assertNull(Certification.normalize(null))
        assertNull(Certification.normalize("  "))
    }

    @Test fun PRF_12_label_mapping_rounds_age_bands_up() {
        assertEquals("G", Certification.normalize("U"))
        assertEquals("R", Certification.normalize("16"))
        assertEquals("tv-ma".uppercase(), Certification.normalize("tv-ma"))
    }

    private fun video(kids: Boolean = false, restricted: Boolean = false): String =
        """{"items":[{"id":"v","snippet":{"title":"t","channelTitle":"c","thumbnails":{}},
            "status":{"madeForKids":$kids},
            "contentDetails":{"contentRating":${if (restricted) """{"ytRating":"ytAgeRestricted"}""" else "{}"}}}]}"""

    @Test fun PRF_12_youtube_made_for_kids_is_tv_y_and_passes_every_level() {
        val item = YouTubeJson.videos(video(kids = true)).single().toItem()
        assertEquals("TV-Y", item.rating)
        assertTrue(ContentGate.allows(item, ContentLevel.KIDS))
    }

    @Test fun PRF_12_youtube_age_restricted_is_tv_ma_and_hidden_for_restricted_profiles() {
        val item = YouTubeJson.videos(video(restricted = true)).single().toItem(safeSearched = true)
        assertEquals("TV-MA", item.rating)
        assertFalse(ContentGate.allows(item, ContentLevel.FAMILY))
    }

    @Test fun PRF_12_youtube_safe_searched_is_family_only_and_unfiltered_is_unknown() {
        val v = YouTubeJson.videos(video()).single()
        val searched = v.toItem(safeSearched = true)
        assertTrue(ContentGate.allows(searched, ContentLevel.FAMILY))
        assertFalse(ContentGate.allows(searched, ContentLevel.KIDS))
        val unfiltered = v.toItem()
        assertNull(unfiltered.rating)
        assertFalse(ContentGate.allows(unfiltered, ContentLevel.FAMILY))
        assertTrue(ContentGate.allows(unfiltered, ContentLevel.ALL))
    }

    private fun tile(type: String, startsAt: Long?, rating: String?) = TodTile(
        "T", "t", type, startsAt, "League", TodImage("i", "m", "1", 0, 0, 1, 1, "f.jpg"), rating,
    )

    @Test fun PRF_12_tod_live_sport_shows_for_family_not_kids_and_titles_follow_their_data() {
        val live = TodCatalog.matches(listOf(tile("GAME", 1_000L, null)), now = 2_000L).single()
        assertEquals(Kind.LIVE, live.kind)
        assertTrue(ContentGate.allows(live, ContentLevel.FAMILY))
        assertFalse(ContentGate.allows(live, ContentLevel.KIDS))
        val titles = TodCatalog.titles(listOf(tile("MOVIE", null, "PG"), tile("MOVIE", null, null).copy(slug = "u")), emptyList())
        assertEquals("PG", titles.first().rating)
        assertNull("no data, unknown, hidden", titles.last().rating)
        assertFalse(ContentGate.allows(titles.last(), ContentLevel.FAMILY))
    }
}
