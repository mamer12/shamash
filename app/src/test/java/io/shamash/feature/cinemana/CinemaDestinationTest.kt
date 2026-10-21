package io.shamash.feature.cinemana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CinemaDestinationTest {
    @Test fun `CIN-07 title links with nonnegative at start playback including zero`() {
        assertEquals(CinemaDestination.Play("123", 0), CinemaDestination.parse("shamash://cinemana/title/123?at=0"))
        assertEquals(CinemaDestination.Play("123", 5000), CinemaDestination.parse("shamash://cinemana/title/123?at=5000"))
        assertEquals(CinemaDestination.Title("123"), CinemaDestination.parse("shamash://cinemana/title/123?at=-1"))
        assertEquals(CinemaDestination.Title("123"), CinemaDestination.parse("shamash://cinemana/title/123?at=oops"))
        assertEquals(CinemaDestination.Play("123", null), CinemaDestination.parse("shamash://cinemana/play/123"))
    }

    @Test fun `CIN-07 malformed destinations cannot enter an add-on`() {
        listOf("https://cinemana/title/123", "shamash://other/title/123", "shamash://cinemana/title/abc",
            "shamash://cinemana/title/-1", "shamash://cinemana/title/123/extra", "shamash://cinemana:80/title/123",
            "shamash://user@cinemana/title/123", "shamash://cinemana/title/999999999999999999999999")
            .forEach { assertNull(it, CinemaDestination.parse(it)) }
    }
}
