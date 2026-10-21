package io.shamash.feature.home

import io.shamash.data.json
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A preview's position goes along to where a title opens, without Home knowing any add-on. SPEC: HOME-20, PEX-31 */
class HomeResumeTest {
    private fun item(open: OpenTarget) = MediaItem("x:1", "x", "Film", Kind.MOVIE, open)

    @Test fun `HOME_20 an app screen gets the position in the extra its provider named`() {
        val screen = OpenTarget.AppScreen("app.pkg", "app.Screen", mapOf("ID" to "1"), resumeExtraKey = "app.AT")
        val out = HomeActions.resumedAt(item(screen), 42_000).open as OpenTarget.AppScreen
        assertEquals(mapOf("ID" to "1", "app.AT" to "42000"), out.extras)
    }

    @Test fun `HOME_20 an app screen without a resume key, or no position, opens as it is`() {
        val plain = item(OpenTarget.AppScreen("app.pkg", "app.Screen", mapOf("ID" to "1")))
        assertSame(plain, HomeActions.resumedAt(plain, 42_000))
        val keyed = item(OpenTarget.AppScreen("app.pkg", "app.Screen", emptyMap(), "app.AT"))
        assertSame(keyed, HomeActions.resumedAt(keyed, null))
    }

    @Test fun `HOME_20 own routes and shamash links get at=`() {
        assertEquals(OpenTarget.Internal("player?id=1&at=5"), HomeActions.resumedAt(item(OpenTarget.Internal("player?id=1")), 5).open)
        assertEquals(OpenTarget.Internal("player?at=5"), HomeActions.resumedAt(item(OpenTarget.Internal("player")), 5).open)
        val page = HomeActions.resumedAt(item(OpenTarget.AppPage("shamash://cinemana/1", "")), 7).open as OpenTarget.AppPage
        assertEquals("shamash://cinemana/1?at=7", page.uri)
        val other = item(OpenTarget.AppPage("https://example.org/1", "app"))
        assertSame(other, HomeActions.resumedAt(other, 7))
    }

    @Test fun `PRF_18 an app screen saved before resumeExtraKey existed still loads`() {
        val old = """{"type":"io.shamash.domain.OpenTarget.AppScreen","appPackage":"a","className":"b","extras":{}}"""
        val t = json.decodeFromString(OpenTarget.serializer(), old) as OpenTarget.AppScreen
        assertNull(t.resumeExtraKey)
    }
}
