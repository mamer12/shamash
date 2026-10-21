package io.shamash.feature.onboarding

import io.shamash.domain.CardSize
import io.shamash.domain.Layout
import io.shamash.domain.LayoutPreset
import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetStepTest {
    @Test fun LAY_08_choosing_a_preset_applies_it_to_the_profile_once() {
        val start = Profile()
        val answers = Answers.of(start).copy(preset = LayoutPreset.KIDS)
        val done = answers.into(start)
        assertEquals(LayoutPreset.KIDS, done.layout.presetId)
        assertEquals(CardSize.L, done.layout.cardSize)
    }

    @Test fun LAY_08_skipping_keeps_the_layout_the_profile_had() {
        val start = Profile(layout = Layout(presetId = LayoutPreset.SPORTS))
        assertEquals(start.layout, Answers.of(start).into(start).layout)
    }
}
