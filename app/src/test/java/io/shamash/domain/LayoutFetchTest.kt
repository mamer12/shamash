package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hidden rows are not fetched when nothing else needs them. SPEC: LAY-04, LAY-12 */
class LayoutFetchTest {
    private val rows = listOf("tod_matches", "tod_new")
    private val hidden = Layout(rows = rows.map { RowPref(it, hidden = true) }, mixOff = setOf("tod"))

    @Test fun `LAY_04 a provider whose rows are all hidden and whose billboard turn is off is not fetched`() {
        assertFalse(LayoutFetch.needed("tod", rows, hidden))
    }

    @Test fun `LAY_04 one shown row, the billboard, another tab or unknown rows still fetch`() {
        assertTrue(LayoutFetch.needed("tod", rows, hidden.copy(rows = listOf(RowPref("tod_matches", hidden = true)))))
        assertTrue(LayoutFetch.needed("tod", rows, hidden.copy(mixOff = emptySet())))     // LAY-12: carousel uses it
        assertTrue(LayoutFetch.needed("tod", rows, hidden, otherReader = true))
        assertTrue(LayoutFetch.needed("tod", emptyList(), hidden))
    }

    @Test fun `LAY_04 a preset that hides the rest skips rows it does not name, and a family pref shows them`() {
        val minimal = Layout(rows = listOf(RowPref("continue")), hideRest = true, mixOff = setOf("tod", "tmdb"))
        assertFalse(LayoutFetch.needed("tod", rows, minimal))
        val withFamily = minimal.copy(rows = minimal.rows + RowPref("tmdb_popular_*"))
        assertTrue(LayoutFetch.needed("tmdb", listOf("tmdb_top10", "tmdb_popular_netflix"), withFamily))
    }

    @Test fun `LAY_04 an add-on row hidden by a preset is off, so it is not fetched, and turning it on sticks`() {
        val s = RowSource("new", titleText = "New")
        val p = Profile(layout = Layout(hideRest = true))
        assertFalse(RowSources.isOn(p, "plex", s))
        val on = RowSources.withOn(p, "plex", s, true)
        assertTrue(RowSources.isOn(on, "plex", s))
        assertEquals(listOf(RowPref("plex:new")), on.layout.rows)
        assertTrue(RowSources.withOn(on, "plex", s, false).layout.rows.isEmpty())        // back to the preset's default
        val family = Profile(layout = Layout(rows = listOf(RowPref("plex:*", hidden = true))))
        assertFalse(RowSources.isOn(family, "plex", s))
    }
}
