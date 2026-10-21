package io.shamash.feature.settings

import io.shamash.feature.tvcontrol.RemoteButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonChoicesTest {
    private val netflix = RemoteButton.NETFLIX
    private val choices = ButtonChoices.of(netflix, listOf("com.netflix.ninja", "com.todtv.tod"))

    @Test
    fun `TVC_07 own app, nothing, then streaming apps, each once`() {
        assertEquals(listOf("com.netflix.ninja", "", "com.todtv.tod"), choices)
    }

    @Test
    fun `TVC_07 OK steps through and wraps back to the own app`() {
        assertEquals("", ButtonChoices.next(choices, "com.netflix.ninja", netflix.defaultApp))
        assertEquals("com.todtv.tod", ButtonChoices.next(choices, "", netflix.defaultApp))
        assertNull(ButtonChoices.next(choices, "com.todtv.tod", netflix.defaultApp))
    }

    @Test
    fun `TVC_07 a button without its own app starts at nothing`() {
        val channel = ButtonChoices.of(RemoteButton.TCL_CHANNEL, listOf("com.todtv.tod"))
        assertEquals(listOf("", "com.todtv.tod"), channel)
        assertNull(ButtonChoices.next(channel, "com.todtv.tod", ""))
    }
}
