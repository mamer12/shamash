package io.shamash.feature.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yTest {
    @Test
    fun `PLT_11 text scale steps multiply the system font scale`() {
        assertEquals(1f, A11ySettings().fontScale(1f), 0.001f)
        assertEquals(1.15f, A11ySettings(TextScale.LARGE).fontScale(1f), 0.001f)
        assertEquals(1.3f * 1.2f, A11ySettings(TextScale.LARGER).fontScale(1.2f), 0.001f)
    }

    @Test
    fun `PLT_12 high contrast follows the TV until the person chooses`() {
        val tvOn = SystemA11y(highContrast = true)
        assertTrue(EffectiveA11y.of(A11ySettings(), tvOn).highContrast)
        assertFalse(EffectiveA11y.of(A11ySettings(highContrast = false), tvOn).highContrast)
        assertTrue(EffectiveA11y.of(A11ySettings(highContrast = true), SystemA11y()).highContrast)
    }

    @Test
    fun `PLT_14 reduce motion follows animations off, and an explicit choice wins`() {
        val off = SystemA11y(animationsOff = true)
        assertTrue(EffectiveA11y.of(A11ySettings(), off).reduceMotion)
        assertFalse(EffectiveA11y.of(A11ySettings(reduceMotion = false), off).reduceMotion)
        assertNull(A11ySettings().reduceMotion)
    }

    @Test
    fun `PLT_13 a card is read as title, kind, provider, progress`() {
        assertEquals("Dune, Movie, TOD, 40% watched", CardLabel.join("Dune", "Movie", "TOD", "40% watched"))
        assertEquals("Dune, Movie", CardLabel.join("Dune", "Movie", null, ""))
        assertEquals(40, CardLabel.percent(0.4f))
        assertNull(CardLabel.percent(null))
        assertNull(CardLabel.percent(0f))
        assertEquals(1, CardLabel.percent(0.001f))
    }

    @Test
    fun `PLT_13 the carousel says its position`() {
        assertEquals("3 of 12", CardLabel.position(2, 12, "%1\$d of %2\$d"))
        assertEquals("", CardLabel.position(0, 0, "%1\$d of %2\$d"))
        assertEquals("", CardLabel.position(5, 3, "%1\$d of %2\$d"))
    }
}
