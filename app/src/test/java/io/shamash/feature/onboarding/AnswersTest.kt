package io.shamash.feature.onboarding

import io.shamash.data.weather.Place
import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {
    private val start = Profile(region = "US")

    @Test
    fun `ONB_02 the city's first match gives coordinates and the region`() {
        val a = Answers.of(start).copy(city = "Baghdad").located(Place("Baghdad", "IQ", 33.34, 44.40))
        val p = a.into(start)
        assertEquals(33.34, p.latitude!!, 0.0)
        assertEquals("IQ", p.region)
    }

    @Test
    fun `ONB_02 an unknown city clears old coordinates and keeps the region`() {
        val a = Answers.of(start.copy(latitude = 1.0, longitude = 2.0)).located(null)
        assertNull(a.into(start).latitude)
        assertEquals("US", a.into(start).region)
    }

    @Test
    fun `ONB_04 teams are split on commas and trimmed`() {
        val p = Answers.of(start).copy(teams = " Barcelona, Al Hilal ,, ").into(start)
        assertEquals(setOf("Barcelona", "Al Hilal"), p.teams)
    }

    @Test
    fun `ONB_06 Done marks the profile onboarded and trims keys`() {
        val a = Answers.of(start).let { it.copy(keys = it.keys.copy(tmdb = "  abc  ")) }
        val p = a.into(start)
        assertTrue(p.onboarded)
        assertEquals("abc", p.tmdbKey)
    }

    @Test
    fun `ONB_06 the answers start from the saved profile`() {
        val saved = start.copy(name = "Sam", teams = setOf("Barcelona"), genres = setOf("Drama"))
        assertEquals(saved.copy(onboarded = true), Answers.of(saved).into(saved))
    }
}
