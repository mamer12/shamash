package io.shamash.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceKitTest {
    @Test
    fun interleave_takes_one_from_each_list_in_turn() {
        val mixed = interleave(listOf(listOf("a1", "a2", "a3"), listOf("b1")), max = 10)
        assertEquals(listOf("a1", "b1", "a2", "a3"), mixed)
    }

    @Test
    fun interleave_skips_repeats_and_stops_at_max() {
        val mixed = interleave(listOf(listOf("x", "a"), listOf("X", "b")), max = 2) { it.lowercase() }
        assertEquals(listOf("x", "a"), mixed)
    }

    @Test
    fun HOME_14_cache_serves_until_max_age_then_loads_again() = runTest {
        var now = 0L
        var loads = 0
        val cache = TimedCache<String, List<Int>>({ now }, Refresh.every(Refresh.HOUR))
        val load: suspend () -> List<Int> = { loads++; listOf(loads) }

        cache.get("k", load)
        now = Refresh.HOUR - 1
        assertEquals(listOf(1), cache.get("k", load))
        now = Refresh.HOUR
        assertEquals(listOf(2), cache.get("k", load))
    }

    @Test
    fun HOME_14_empty_answers_are_retried_sooner() = runTest {
        var now = 0L
        var loads = 0
        val cache = TimedCache<Unit, List<Int>>({ now }, Refresh.every(Refresh.HOUR))
        val load: suspend () -> List<Int> = { loads++; emptyList() }

        cache.get(Unit, load)
        now = Refresh.RETRY_EMPTY
        cache.get(Unit, load)
        assertEquals(2, loads)
    }

    @Test
    fun HOME_14_a_new_key_loads_again() = runTest {
        var loads = 0
        val cache = TimedCache<String, Int?>({ 0L }) { Refresh.HOUR }
        cache.get("a") { loads++; null }
        cache.get("a") { loads++; null }
        cache.get("b") { loads++; null }
        assertEquals(2, loads)
    }
}
