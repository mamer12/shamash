package io.shamash.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lite drawing is picked by itself on weak TVs, and Settings can force it either way. SPEC: PERF-06 */
class LiteRuleTest {
    private val gb = 1_073_741_824L

    @Test fun `PERF_06 a 2 GB Android 11 TV is weak`() = assertTrue(LiteRule.weak(false, 2 * gb, 30))

    @Test fun `PERF_06 a low-RAM flag alone makes it weak`() = assertTrue(LiteRule.weak(true, 4 * gb, 34))

    @Test fun `PERF_06 Android 11 with plenty of RAM is still lite`() = assertTrue(LiteRule.weak(false, 4 * gb, 30))

    @Test fun `PERF_06 a 3 GB Android 12 TV keeps the full look`() = assertFalse(LiteRule.weak(false, 3 * gb, 31))

    @Test fun `PERF_06 unknown memory is not taken as weak`() = assertFalse(LiteRule.weak(false, 0, 34))

    @Test fun `PERF_06 the Settings choice wins over the probe`() {
        assertTrue(LiteRule.on(LiteChoice.AUTO, weak = true))
        assertFalse(LiteRule.on(LiteChoice.AUTO, weak = false))
        assertTrue(LiteRule.on(LiteChoice.ON, weak = false))
        assertFalse(LiteRule.on(LiteChoice.OFF, weak = true))
    }
}
