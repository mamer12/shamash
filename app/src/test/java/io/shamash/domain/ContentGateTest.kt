package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentGateTest {
    private fun item(id: String, rating: String?, kind: Kind = Kind.MOVIE) =
        MediaItem(id = id, providerId = "p", title = id, kind = kind, open = OpenTarget.App("x"), rating = rating)

    @Test fun PRF_12_gate_table_for_every_level_and_rating() {
        val table = mapOf(
            "G" to Triple(true, true, true), "PG" to Triple(true, true, true), "TV-Y7" to Triple(true, true, true),
            "PG-13" to Triple(true, true, false), "TV-14" to Triple(true, true, false), "TV-PG" to Triple(true, true, false),
            "R" to Triple(true, false, false), "TV-MA" to Triple(true, false, false), "NC-17" to Triple(true, false, false),
            "pg" to Triple(true, true, true), " tv-g " to Triple(true, true, true),
        )
        table.forEach { (rating, expected) ->
            val i = item(rating, rating)
            assertEquals(rating, expected.first, ContentGate.allows(i, ContentLevel.ALL))
            assertEquals(rating, expected.second, ContentGate.allows(i, ContentLevel.FAMILY))
            assertEquals(rating, expected.third, ContentGate.allows(i, ContentLevel.KIDS))
        }
    }

    @Test fun PRF_12_unknown_rating_is_hidden_unless_the_level_is_all() {
        listOf(null, "", "  ", "NR", "Unrated").forEach { rating ->
            val i = item("x", rating)
            assertTrue(ContentGate.allows(i, ContentLevel.ALL))
            assertFalse(ContentGate.allows(i, ContentLevel.FAMILY))
            assertFalse(ContentGate.allows(i, ContentLevel.KIDS))
        }
    }

    @Test fun PRF_12_matches_carry_no_rating_and_show_for_family_but_not_kids() {
        val match = item("m", null, Kind.MATCH)
        assertTrue(ContentGate.allows(match, ContentLevel.ALL))
        assertTrue(ContentGate.allows(match, ContentLevel.FAMILY))
        assertFalse(ContentGate.allows(match, ContentLevel.KIDS))
    }

    @Test fun PRF_12_filters_lists_and_shelves_and_leaves_ALL_untouched() {
        val items = listOf(item("a", "G"), item("b", "R"), item("c", null))
        assertEquals(listOf("a"), ContentGate.filter(items, ContentLevel.KIDS).map { it.id })
        assertEquals(listOf("a"), ContentGate.filter(items, Profile(contentLevel = ContentLevel.FAMILY)).map { it.id })
        assertSame(items, ContentGate.filter(items, ContentLevel.ALL))
        val shelf = Shelf("s", "k", ShelfStyle.POSTER, items)
        assertEquals(listOf("a"), ContentGate.filter(shelf, ContentLevel.KIDS).items.map { it.id })
    }

    @Test fun PRF_18_new_profile_fields_default_to_todays_behaviour() {
        val p = Profile()
        assertEquals(ContentLevel.ALL, p.contentLevel)
        assertEquals(ProfileKind.ADULT, p.kind)
        assertEquals(KeyMode.SHARED, p.keyMode)
        assertTrue(p.addons.isEmpty() && p.follows.isEmpty() && p.pinHash == null)
        assertEquals(LayoutPreset.CINEMA, p.layout.presetId)
        assertTrue(p.layout.rows.isEmpty())
        assertTrue(p.withAddon("cinemana", true).cinemanaAddon)
        assertFalse(p.withAddon("cinemana", true).withAddon("cinemana", false).cinemanaAddon)
    }
}
