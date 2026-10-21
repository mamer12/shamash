package io.shamash.feature.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shell-safety, brand lists, the watchdog's count and the remote's keycodes. */
class SafetyTest {

    @Test
    fun `TVC_02 only plain names reach the shell`() {
        listOf("com.x; reboot", "com.x && rm -rf /", "\$(id)", "a b", "com.x'", "com.x\nreboot", "").forEach {
            assertFalse(it, ShellSafety.isPackage(it))
            assertFalse(it, ShellSafety.isComponent("$it/.A"))
        }
        assertTrue(ShellSafety.isPackage("com.tcl.UpdatePeripheral"))
        assertTrue(ShellSafety.isComponent("io.shamash/.feature.screensaver.ShamashDream"))
        assertTrue(ShellSafety.isComponent("com.x/com.x.Outer\$Inner"))
    }

    @Test(expected = UnsafeInputException::class)
    fun `TVC_02 building a command with a bad name throws before anything runs`() {
        Commands.disable("com.x;reboot")
    }

    @Test
    fun `TVC_02 commands are the vetted forms`() {
        assertEquals("pm disable-user --user 0 com.tcl.exhibit", Commands.disable("com.tcl.exhibit"))
        assertEquals("appops set io.shamash GET_USAGE_STATS allow", Commands.appOp("io.shamash", "GET_USAGE_STATS", true))
        assertEquals("cmd package set-home-activity 'io.shamash/.HomeAlias'", Commands.setHome(TvPackages.HOME_ALIAS))
    }

    @Test
    fun `TVC_04 no brand lists a protected package, and all names are valid`() {
        Brand.entries.forEach { brand ->
            val promo = BrandProfiles.promo(brand)
            assertTrue(promo.none { it in BrandProfiles.PROTECTED })
            assertTrue(promo.all(ShellSafety::isPackage))
        }
        listOf("com.android.vending", "com.android.systemui", "com.tcl.tv", "com.tcl.ttvs", "com.tcl.tvinput",
            "com.android.tv.settings", TvPackages.SHAMASH).forEach { assertTrue(it, it in BrandProfiles.PROTECTED) }
    }

    @Test
    fun `TVC_06 TCL is recognised by maker or brand`() {
        assertEquals(Brand.TCL, BrandProfiles.brandOf("TCL", "google"))
        assertEquals(Brand.TCL, BrandProfiles.brandOf("unknown", "tcl"))
        assertEquals(Brand.OTHER, BrandProfiles.brandOf("Xiaomi", "Xiaomi"))
    }

    @Test
    fun `TVC_05 three crashes within five minutes trip the watchdog, older ones don't count`() {
        val now = 10 * Watchdog.WINDOW_MS
        val old = listOf(now - Watchdog.WINDOW_MS - 1, now - Watchdog.WINDOW_MS)
        assertFalse(Watchdog.tooMany(Watchdog.record(old, now)))
        val two = listOf(now - 60_000, now - 30_000)
        assertTrue(Watchdog.tooMany(Watchdog.record(two, now)))
        assertEquals(listOf(now), Watchdog.record(listOf(now + 1_000), now)) // a clock jump back drops the future ones
    }

    @Test
    fun `TVC_07 TCL's app-button keycodes map to their apps`() {
        assertEquals(RemoteButton.NETFLIX, RemoteButton.of(4062))
        assertEquals(RemoteButton.YOUTUBE, RemoteButton.of(190))
        assertEquals("com.amazon.amazonvideo.livingroom", RemoteButton.of(4099)?.defaultApp)
        assertEquals("", RemoteButton.TCL_CHANNEL.defaultApp)
        assertNull(RemoteButton.of(23))
        assertEquals("remote_4062", RemoteKeys.secretKey(RemoteButton.NETFLIX))
    }

    @Test
    fun `TVC_07 gamepad and joystick keys are left alone`() {
        assertTrue(RemoteKeys.isFromController(android.view.InputDevice.SOURCE_GAMEPAD))
        assertTrue(RemoteKeys.isFromController(android.view.InputDevice.SOURCE_JOYSTICK))
        assertFalse(RemoteKeys.isFromController(android.view.InputDevice.SOURCE_KEYBOARD))
    }
}
