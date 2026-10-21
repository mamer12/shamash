package io.shamash.feature.cinemana

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class PlayerSubRipTest {
    private val sample = "1\r\n00:00:01,000 --> 00:00:03,000\r\n<b>Hello</b>\r\nworld\r\n\r\n2\r\n00:00:04.000 --> 00:00:05.000\r\nLater"

    @Test fun CIN04_cueBoundariesAndSeeking() {
        val subs = SubRip.parse(sample)
        assertNull(subs.at(999)); assertEquals("Hello\nworld", subs.at(1000)); assertNull(subs.at(3000))
        assertEquals("Later", subs.at(4500)); assertEquals("Hello\nworld", subs.at(1500))
    }

    @Test fun CIN04_overlappingCuesDoNotHideLongerCue() {
        val subs = SubRip.parse("1\n00:00:01,000 --> 00:00:10,000\nLong\n\n2\n00:00:02,000 --> 00:00:03,000\nShort")
        assertEquals("Short", subs.at(2500)); assertEquals("Long", subs.at(5000))
    }

    @Test fun CIN04_arabicUtf8Utf16AndLegacy() {
        val arabic = "مرحباً بالعالم"
        assertEquals(arabic, SubRip.decode(arabic.toByteArray(Charsets.UTF_8)))
        assertEquals(arabic, SubRip.decode(arabic.toByteArray(Charsets.UTF_16)))
        assertEquals(arabic, SubRip.decode(arabic.toByteArray(Charset.forName("windows-1256"))))
    }

    @Test fun CIN04_malformedSubtitlesFailSoft() {
        assertNull(SubRip.parse("garbage").at(0))
        assertNull(SubRip.parse("1\n00:70:00,000 --> 00:80:00,000\nBad").at(5000000))
    }
}
