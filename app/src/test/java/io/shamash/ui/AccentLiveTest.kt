package io.shamash.ui

import androidx.compose.ui.graphics.Color
import io.shamash.domain.Accents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** A new accent shows without a restart: text styles read it when drawn. SPEC: LAY-14 */
class AccentLiveTest {
    @After fun reset() = Palette.setAccent(Accents.GOLD)

    @Test fun `LAY_14 the billboard label follows the accent at once`() {
        Palette.setAccent(Accents.ALL[1])
        assertEquals(Color(Accents.ALL[1]), Type.HeroLabel.color)
        Palette.setAccent(Accents.ALL[2])
        assertEquals(Color(Accents.ALL[2]), Type.HeroLabel.color)
    }
}
