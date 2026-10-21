package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentRulesTest {
    private fun rule(id: String, days: Set<Int>, from: String, to: String, row: String = "matches") =
        MomentRule(id, days, MomentRules.parse(from)!!, MomentRules.parse(to)!!, row)

    @Test fun LAY_10_matches_inside_the_range_and_not_at_its_end() {
        val r = rule("a", setOf(5), "18:00", "23:00")
        assertTrue(MomentRules.matches(r, 5, 18 * 60))
        assertTrue(MomentRules.matches(r, 5, 22 * 60 + 59))
        assertFalse(MomentRules.matches(r, 5, 23 * 60))
        assertFalse(MomentRules.matches(r, 4, 20 * 60))
    }

    @Test fun LAY_10_a_range_may_cross_midnight() {
        val r = rule("a", setOf(5), "23:00", "05:00")           // Friday night
        assertTrue(MomentRules.matches(r, 5, 23 * 60 + 30))
        assertTrue(MomentRules.matches(r, 6, 2 * 60))            // Saturday 02:00 still belongs to Friday's rule
        assertFalse(MomentRules.matches(r, 5, 2 * 60))
        assertFalse(MomentRules.matches(r, 6, 23 * 60 + 30))
    }

    @Test fun LAY_10_sunday_night_rolls_into_monday() {
        val r = rule("a", setOf(7), "22:00", "02:00")
        assertTrue(MomentRules.matches(r, 1, 60))
    }

    @Test fun LAY_10_ties_go_to_the_first_rule_in_list_order() {
        val first = rule("first", setOf(5), "18:00", "23:00", "matches")
        val second = rule("second", setOf(5), "19:00", "21:00", "yt_recs")
        assertEquals("first", MomentRules.firstMatch(listOf(first, second), 5, 20 * 60)?.id)
        assertEquals("second", MomentRules.firstMatch(listOf(second, first), 5, 20 * 60)?.id)
        assertNull(MomentRules.firstMatch(listOf(first, second), 5, 10 * 60))
    }

    @Test fun LAY_13_invalid_rules_are_rejected() {
        assertEquals(RuleError.NO_DAYS, MomentRules.validate(rule("a", emptySet(), "18:00", "20:00")))
        assertEquals(RuleError.SAME_TIME, MomentRules.validate(rule("a", setOf(1), "18:00", "18:00")))
        assertEquals(RuleError.NO_DAYS, MomentRules.validate(rule("a", setOf(8), "18:00", "20:00")))
        assertNull(MomentRules.add(emptyList(), rule("a", emptySet(), "18:00", "20:00")))
        assertFalse(MomentRules.matches(rule("a", setOf(1), "18:00", "18:00"), 1, 18 * 60))
    }

    @Test fun LAY_11_at_most_eight_rules() {
        var rules = emptyList<MomentRule>()
        repeat(MomentRules.MAX) { rules = MomentRules.add(rules, rule("r$it", setOf(1), "08:00", "09:00"))!! }
        assertNull(MomentRules.add(rules, rule("one more", setOf(1), "08:00", "09:00")))
    }

    @Test fun LAY_10_time_parsing_and_iso_days() {
        assertEquals(23 * 60 + 5, MomentRules.parse("23:05"))
        assertNull(MomentRules.parse("24:00"))
        assertNull(MomentRules.parse("nope"))
        assertEquals("07:05", MomentRules.format(7 * 60 + 5))
        assertEquals(7, MomentRules.isoDay(java.util.Calendar.SUNDAY))
        assertEquals(1, MomentRules.isoDay(java.util.Calendar.MONDAY))
    }
}
