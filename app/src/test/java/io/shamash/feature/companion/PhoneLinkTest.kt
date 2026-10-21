package io.shamash.feature.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLinkTest {
    @Test
    fun `CMP_04 a YouTube watch link opens that video`() {
        assertEquals(
            PhoneMessage.YouTube("dQw4w9WgXcQ"),
            PhoneLink.classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"),
        )
    }

    @Test
    fun `CMP_04 a watch link with v after other parameters`() {
        assertEquals(
            PhoneMessage.YouTube("dQw4w9WgXcQ"),
            PhoneLink.classify("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `CMP_04 a youtu_be share link inside a sentence`() {
        assertEquals(
            PhoneMessage.YouTube("abc_DEF-123"),
            PhoneLink.classify("Look at this! https://youtu.be/abc_DEF-123?si=xyz"),
        )
    }

    @Test
    fun `CMP_04 shorts and live links`() {
        assertEquals(PhoneMessage.YouTube("Shorts12345"), PhoneLink.classify("https://youtube.com/shorts/Shorts12345"))
        assertEquals(PhoneMessage.YouTube("Live1234567"), PhoneLink.classify("https://www.youtube.com/live/Live1234567?feature=x"))
    }

    @Test
    fun `CMP_04 a TOD link opens TOD`() {
        val url = "https://www.tod.tv/en/sports/live/match-123"
        assertEquals(PhoneMessage.Tod(url), PhoneLink.classify("Watch: $url"))
        assertEquals(PhoneMessage.Tod("https://tod.tv/x"), PhoneLink.classify("https://tod.tv/x"))
    }

    @Test
    fun `CMP_04 a look-alike host is not TOD`() {
        assertEquals(PhoneMessage.Link("https://notod.tv/x"), PhoneLink.classify("https://notod.tv/x"))
    }

    @Test
    fun `CMP_04 other web links open with ACTION_VIEW`() {
        val url = "https://www.netflix.com/title/80057281"
        assertEquals(PhoneMessage.Link(url), PhoneLink.classify("  $url  "))
    }

    @Test
    fun `CMP_04 plain text goes to Search`() {
        assertEquals(PhoneMessage.Words("Breaking Bad"), PhoneLink.classify("  Breaking Bad \n"))
        assertEquals(PhoneMessage.Words("باب الحارة"), PhoneLink.classify("باب الحارة"))
    }

    @Test
    fun `CMP_04 nothing sent is nothing to do`() {
        assertEquals(PhoneMessage.Empty, PhoneLink.classify("   "))
    }
}
