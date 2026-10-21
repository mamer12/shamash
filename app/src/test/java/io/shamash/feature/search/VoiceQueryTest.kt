package io.shamash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceQueryTest {
    @Test
    fun `PLT_08 the recognizer's first non-blank text becomes the query`() {
        assertEquals("the dark knight", VoiceQuery.toQuery(VoiceQuery.best(listOf(" ", "The  Dark Knight "))))
        assertNull(VoiceQuery.toQuery(VoiceQuery.best(emptyList())))
        assertNull(VoiceQuery.toQuery(VoiceQuery.best(null)))
    }

    @Test
    fun `PLT_10 Arabic is folded like the search ranking does`() {
        // diacritics, hamza on alef, tatweel and alef wasla all fold to the plain letters
        assertEquals("الاهلي", VoiceQuery.toQuery("الأَهْلِـي"))
        assertEquals(VoiceQuery.toQuery("إسلام"), VoiceQuery.toQuery("اسلام"))
        assertEquals("ابن", VoiceQuery.toQuery("ٱبن"))
    }

    @Test
    fun `PLT_10 the recognizer gets the app language`() {
        assertEquals("ar", VoiceQuery.language("ar"))
        assertEquals("en-US", VoiceQuery.language("en"))
        assertEquals("en-US", VoiceQuery.language("de"))
    }

    @Test
    fun `PLT_09 a query becomes a search route`() {
        assertEquals("search?q=dark%20knight", VoiceQuery.route("dark knight"))
    }

    @Test
    fun `PLT_08 the remote's voice and search keys start voice search`() {
        assertTrue(VoiceQuery.isVoiceKey(android.view.KeyEvent.KEYCODE_SEARCH))
        assertTrue(VoiceQuery.isVoiceKey(android.view.KeyEvent.KEYCODE_VOICE_ASSIST))
        assertFalse(VoiceQuery.isVoiceKey(android.view.KeyEvent.KEYCODE_DPAD_UP))
    }
}
