package io.shamash.feature.home

import io.shamash.domain.LayoutOps
import io.shamash.domain.LayoutPresets
import io.shamash.domain.Layout
import io.shamash.domain.Look
import io.shamash.domain.MomentRule
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TempUnit
import io.shamash.domain.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HomeLayoutTest {
    private fun shelf(id: String) = Shelf(id, id, ShelfStyle.WIDE, emptyList())
    private val rows = listOf("matches", "moment", "continue", "yt_recs").map(::shelf)

    @Test fun LAY_01_rows_follow_the_layout_and_renames_show_as_given() {
        val ids = rows.map { it.id }
        val layout = LayoutOps.rename(ids, LayoutOps.move(ids, Layout(), "continue", -2), "continue", "My shows")
        val laid = HomeLayout.lay(rows, Profile(layout = layout), null)
        assertEquals(listOf("continue", "matches", "moment", "yt_recs"), laid.shown.map { it.id })
        assertEquals("My shows", laid.shown.first().title)
    }

    @Test fun LAY_02_the_editor_list_includes_hidden_rows() {
        val ids = rows.map { it.id }
        val layout = LayoutOps.setHidden(ids, Layout(), "matches", true)
        val laid = HomeLayout.lay(rows, Profile(layout = layout), null)
        assertTrue("matches" !in laid.shown.map { it.id })
        assertTrue("matches" in laid.editable.map { it.id })
    }

    @Test fun LAY_11_a_matching_rule_lifts_its_row_and_relabels_it() {
        val rule = MomentRule("r", setOf(1), 0, 60, "yt_recs", label = "Friday picks")
        val laid = HomeLayout.lay(rows, Profile(), rule)
        assertEquals("yt_recs", laid.shown.first().id)
        assertEquals("Friday picks", laid.shown.first().title)
    }

    @Test fun LAY_10_the_rule_is_looked_up_on_the_tv_clock() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 23, 20, 0, 0) }   // a Friday
        val rule = MomentRule("r", setOf(5), 19 * 60, 22 * 60, "matches")
        val p = Profile(layout = Layout(rules = listOf(rule)))
        assertEquals("r", HomeLayout.ruleAt(p, c.timeInMillis)?.id)
        c.set(Calendar.HOUR_OF_DAY, 9)
        assertNull(HomeLayout.ruleAt(p, c.timeInMillis))
        assertNull(HomeLayout.ruleAt(Profile(), c.timeInMillis))
    }

    @Test fun LAY_16_temperature_unit_and_hidden_weather() {
        val w = Weather(20.0, 0, true, 0, 0)
        fun p(look: Look, region: String = "IQ") = Profile(region = region, layout = Layout(look = look))
        assertEquals(20, HomeLayout.weather(w, p(Look()))?.degrees)
        assertEquals(68, HomeLayout.weather(w, p(Look(), "US"))?.degrees)
        assertEquals(68, HomeLayout.weather(w, p(Look(temp = TempUnit.F)))?.degrees)
        assertEquals(20, HomeLayout.weather(w, p(Look(temp = TempUnit.C), "US"))?.degrees)
        assertNull(HomeLayout.weather(w, p(Look(showWeather = false))))
    }

    @Test fun LAY_08_a_preset_applied_once_is_not_overwritten_by_later_edits() {
        val ids = rows.map { it.id }
        val applied = LayoutPresets.apply(LayoutPresets.SPORTS)
        val edited = LayoutOps.setHidden(ids, applied, "matches", true)
        assertTrue("matches" !in LayoutOps.visible(ids, edited))
    }
}
