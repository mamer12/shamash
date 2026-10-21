package io.shamash.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val ids = listOf("matches", "moment", "continue", "yt_recs", "tmdb_top10", "tmdb_popular_netflix")
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun LAY_03_no_prefs_keeps_the_default_order() {
        assertEquals(ids, LayoutOps.visible(ids, Layout()))
    }

    @Test fun LAY_03_pinned_first_then_by_order_then_unsaved_rows_in_default_order() {
        val layout = Layout(rows = listOf(
            RowPref("continue", order = 0),
            RowPref("tmdb_top10", pinned = true, order = 5),
            RowPref("matches", order = 1),
        ))
        assertEquals(
            listOf("tmdb_top10", "continue", "matches", "moment", "yt_recs", "tmdb_popular_netflix"),
            LayoutOps.visible(ids, layout),
        )
    }

    @Test fun LAY_03_a_new_row_goes_to_the_end_without_reordering_the_others() {
        val saved = LayoutOps.materialize(ids, Layout())
        val moved = LayoutOps.move(ids, saved, "continue", -2)
        val withNew = ids + "tod_new"
        assertEquals(LayoutOps.visible(ids, moved) + "tod_new", LayoutOps.visible(withNew, moved))
    }

    @Test fun LAY_04_hidden_rows_are_not_drawn() {
        val layout = LayoutOps.setHidden(ids, Layout(), "moment", true)
        assertFalse("moment" in LayoutOps.visible(ids, layout))
        assertTrue(LayoutOps.arrange(ids, layout).any { it.id == "moment" && it.hidden })
    }

    @Test fun LAY_09_missing_rows_in_prefs_are_ignored_and_kept() {
        val layout = Layout(rows = listOf(RowPref("plex:movies", order = 0), RowPref("continue", order = 1)))
        assertEquals("continue", LayoutOps.visible(ids, layout).first())
        val edited = LayoutOps.setHidden(ids, layout, "matches", true)
        assertTrue(edited.rows.any { it.rowId == "plex:movies" })
    }

    @Test fun LAY_09_wildcard_preset_rows_expand_to_what_exists() {
        val layout = Layout(rows = listOf(RowPref("tmdb_popular_*", order = 0), RowPref("cinemana_*", order = 1)))
        assertEquals("tmdb_popular_netflix", LayoutOps.visible(ids, layout).first())
    }

    @Test fun LAY_02_move_swaps_neighbours_and_stops_at_the_edges() {
        val up = LayoutOps.move(ids, Layout(), "continue", -1)
        assertEquals(listOf("matches", "continue", "moment"), LayoutOps.visible(ids, up).take(3))
        val top = LayoutOps.move(ids, Layout(), "matches", -1)
        assertEquals(ids, LayoutOps.visible(ids, top))
    }

    @Test fun LAY_02_pin_puts_the_row_first() {
        val pinned = LayoutOps.setPinned(ids, Layout(), "yt_recs", true)
        assertEquals("yt_recs", LayoutOps.visible(ids, pinned).first())
        val unpinned = LayoutOps.setPinned(ids, pinned, "yt_recs", false)
        assertFalse(LayoutOps.arrange(ids, unpinned).first { it.id == "yt_recs" }.pinned)
    }

    @Test fun LAY_02_LAY_06_rename_is_trimmed_capped_at_40_and_empty_restores() {
        val long = "x".repeat(60)
        val named = LayoutOps.rename(ids, Layout(), "continue", "  $long ")
        assertEquals(40, LayoutOps.prefFor("continue", named.rows)?.title?.length)
        val restored = LayoutOps.rename(ids, named, "continue", "   ")
        assertNull(LayoutOps.prefFor("continue", restored.rows)?.title)
    }

    @Test fun LAY_05_reset_restores_the_preset_and_keeps_rules_and_look() {
        val rule = MomentRule("a", setOf(5), 18 * 60, 23 * 60, "matches")
        val edited = LayoutPresets.apply(LayoutPresets.SPORTS).copy(rules = listOf(rule), look = Look(accent = Accents.ALL[2]))
        val changed = LayoutOps.setHidden(ids, edited, "matches", true)
        val reset = LayoutOps.reset(changed)
        assertEquals(LayoutPreset.SPORTS, reset.presetId)
        assertEquals(listOf(rule), reset.rules)
        assertEquals(Accents.ALL[2], reset.look.accent)
        assertEquals("matches", LayoutOps.visible(ids, reset).first())
    }

    @Test fun LAY_07_presets_are_data_and_minimal_and_kids_hide_the_rest() {
        assertEquals(setOf("cinema", "sports", "kids", "minimal"), LayoutPresets.all.map { it.id }.toSet())
        assertEquals(listOf("continue", "moment"), LayoutOps.visible(ids, LayoutPresets.apply(LayoutPresets.MINIMAL)))
        val kids = LayoutPresets.apply(LayoutPresets.KIDS)
        assertEquals(CardSize.L, kids.cardSize)
        assertFalse("matches" in LayoutOps.visible(ids, kids))
        val shown = LayoutOps.visible(listOf("matches", "tod_matches", "tmdb_top10", "yt_recs", "cinemana_kids", "myfeed:f1", "podcasts:new"), kids)
        assertEquals(listOf("cinemana_kids", "yt_recs", "myfeed:f1"), shown)                 // kids rows only
        assertEquals(listOf("cinemana", "youtube"), kids.mix)
        assertEquals("sports", LayoutPresets.byId("sports").id)
        assertEquals("cinema", LayoutPresets.byId("nope").id)
    }

    @Test fun LAY_07_preset_json_fixture_decodes_with_unknown_fields() {
        val text = javaClass.getResource("/layout/preset_custom.json")!!.readText()
        val preset = json.decodeFromString(LayoutPreset.serializer(), text)
        assertEquals(CardSize.L, preset.cardSize)
        assertTrue(preset.hideRest)
        assertEquals(listOf("matches"), LayoutOps.visible(listOf("matches", "continue", "x"), LayoutPresets.apply(preset)))
    }

    @Test fun LAY_19_layout_round_trips_and_old_json_without_new_fields_loads() {
        val layout = LayoutPresets.apply(LayoutPresets.SPORTS).copy(look = Look(clock = ClockFormat.H24))
        assertEquals(layout, json.decodeFromString(Layout.serializer(), json.encodeToString(Layout.serializer(), layout)))
        val old = json.decodeFromString(Layout.serializer(), """{"presetId":"cinema","rows":[{"rowId":"matches"}]}""")
        assertEquals(CardSize.M, old.cardSize)
        assertTrue(old.rules.isEmpty())
    }

    @Test fun LAY_11_emphasis_moves_the_row_to_the_top_when_it_is_shown() {
        assertEquals(listOf("yt_recs", "matches", "moment"), LayoutOps.emphasize(listOf("matches", "moment", "yt_recs"), "yt_recs"))
        assertEquals(listOf("a", "b"), LayoutOps.emphasize(listOf("a", "b"), "gone"))
    }

    @Test fun LAY_16_digits_stay_western_unless_arabic_indic_is_picked() {
        assertEquals("21:30", Digits.localize("21:30", false))
        assertEquals("٢١:٣٠", Digits.localize("21:30", true))
    }

    @Test fun LAY_17_mirrored_order_and_direction() {
        assertEquals(listOf(3, 2, 1), Digits.visualOrder(listOf(1, 2, 3), rtl = true))
        assertEquals(listOf(1, 2, 3), Digits.visualOrder(listOf(1, 2, 3), rtl = false))
        assertTrue(Digits.mirrored("ar", Look()))
        assertTrue(Digits.mirrored("en", Look(arabicLayout = true)))
        assertFalse(Digits.mirrored("en", Look()))
    }

    @Test fun LAY_14_text_on_every_accent_has_contrast_4_5_and_accent_text_is_readable() {
        assertEquals(8, Accents.ALL.size)
        Accents.ALL.forEach { accent ->
            assertTrue(Accents.contrast(accent, Accents.textOn(accent)) >= Accents.MIN_CONTRAST)
            assertTrue(Accents.contrast(Accents.readable(accent), 0xFF0A0C1E.toInt()) >= Accents.MIN_CONTRAST)
        }
        val dark = 0xFF202080.toInt()
        assertTrue(Accents.contrast(Accents.readable(dark), 0xFF0A0C1E.toInt()) >= Accents.MIN_CONTRAST)
    }

    @Test fun LAY_15_card_sizes_scale_085_1_12() {
        assertEquals(0.85f, CardSize.S.factor)
        assertEquals(1f, CardSize.M.factor)
        assertEquals(1.2f, CardSize.L.factor)
    }

    @Test fun LAY_20_a_layout_holds_no_secrets() {
        val text = json.encodeToString(Layout.serializer(), LayoutPresets.apply(LayoutPresets.CINEMA))
        assertFalse(text.contains("key", ignoreCase = true))
        assertNotNull(text)
    }

    @Test fun LAY_07_minimal_turns_billboard_autoplay_off_and_others_keep_it() {
        assertFalse(LayoutPresets.apply(LayoutPresets.MINIMAL).autoplay)
        assertTrue(LayoutPresets.apply(LayoutPresets.CINEMA, LayoutPresets.apply(LayoutPresets.MINIMAL)).autoplay)
        assertTrue(json.decodeFromString(Layout.serializer(), """{"presetId":"minimal"}""").autoplay)   // old stores
        assertFalse(LayoutOps.reset(LayoutPresets.apply(LayoutPresets.MINIMAL).copy(autoplay = true)).autoplay)
    }
}
