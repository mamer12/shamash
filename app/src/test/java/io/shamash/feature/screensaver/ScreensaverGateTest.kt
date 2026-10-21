package io.shamash.feature.screensaver

import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverGateTest {
    private fun item(id: String, rating: String?, art: String? = "https://x/$id.jpg") =
        MediaItem(id, "tmdb", id, Kind.MOVIE, OpenTarget.App("x"), rating = rating, backdropUrl = art)

    private val perProvider = listOf(
        listOf(item("r", "R"), item("g", "G"), item("none", null)),
        listOf(item("pg13", "PG-13"), item("g-again", "G", art = "https://x/g.jpg"), item("no-art", "G", art = null)),
    )

    @Test fun `PRF_12 screensaver slides pass the content gate`() {
        fun ids(level: ContentLevel) = ScreensaverSlides.pick(perProvider, Profile(contentLevel = level), 20).map { it.id }
        assertEquals(listOf("r", "g", "none", "pg13"), ids(ContentLevel.ALL))
        assertEquals(listOf("g", "pg13"), ids(ContentLevel.FAMILY))
        assertEquals(listOf("g"), ids(ContentLevel.KIDS))
        assertEquals(1, ScreensaverSlides.pick(perProvider, Profile(), 1).size)
    }
}
