package io.shamash.data.cinemana

import io.shamash.data.Http
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WatchmeHandoffTest {
    private val title = CinemanaTitle("42", "Some Film", Kind.MOVIE, "https://example.test/p", "", "tt0000042")
    private fun provider(watchme: Boolean) =
        CinemanaProvider(CinemanaApi(Http(null)), CinemanaTmdb(Http(null)), { it == CinemanaProvider.WATCHME && watchme })

    @Test fun `CIN-01 Watchme+ gets the title id and name, and no resume position by default`() {
        val target = provider(watchme = true).open(title) as OpenTarget.AppScreen
        assertEquals("io.watchmeplus", target.appPackage)
        assertEquals("42", target.extras["io.freemytv.cinemana.ID"])
        assertEquals("Some Film", target.extras["io.freemytv.cinemana.QUERY"])
        assertFalse(target.extras.containsKey("io.freemytv.cinemana.PLAY_AT"))
    }

    @Test fun `CIN-01 without Watchme+ the Cinemana app opens`() {
        assertEquals(OpenTarget.App(CinemanaProvider.CINEMANA_APP), provider(watchme = false).open(title))
    }
}
