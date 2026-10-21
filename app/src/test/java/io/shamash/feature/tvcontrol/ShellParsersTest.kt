package io.shamash.feature.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV shell's answers, as recorded on Google TV and TCL sets. */
class ShellParsersTest {

    private val homes = """
        com.google.android.apps.tv.launcherx/.home.HomeActivity
        com.google.android.tungsten.setupwraith/.RecoveryActivity
        io.shamash/.HomeAlias
    """.trimIndent()

    @Test
    fun `TVC_02 package lists are read and odd lines dropped`() {
        val out = "package:com.tcl.tv\npackage:com.google.android.gms\r\nWARNING: junk\npackage:bad name\n"
        assertEquals(setOf("com.tcl.tv", "com.google.android.gms"), ShellParsers.packages(out))
    }

    @Test
    fun `TVC_03 wifi owners are the creators and last updaters of saved networks`() {
        val out = "cname=com.google.android.tungsten.setupwraith\nlname=com.android.settings\ncname=android.uid.system:1000\n"
        assertEquals(
            setOf("com.google.android.tungsten.setupwraith", "com.android.settings", "android.uid.system"),
            ShellParsers.wifiOwners(out),
        )
    }

    @Test
    fun `TVC_02 the home holder comes from the role, else from what HOME resolves to`() {
        assertEquals("com.google.android.apps.tv.launcherx", ShellParsers.homeHolder("com.google.android.apps.tv.launcherx\n", ""))
        assertEquals(
            "com.tcl.launcher",
            ShellParsers.homeHolder("Unknown command: get-role-holders", "priority=0\ncom.tcl.launcher/.Home\n"),
        )
        assertNull(ShellParsers.homeHolder("", "android/com.android.internal.app.ResolverActivity"))
    }

    @Test
    fun `TVC_02 the current home is the holder's activity, and never Shamash`() {
        val list = ShellParsers.homeActivities(homes)
        assertEquals(
            "com.google.android.apps.tv.launcherx/.home.HomeActivity",
            ShellParsers.currentHome("com.google.android.apps.tv.launcherx", list),
        )
        assertNull(ShellParsers.currentHome("io.shamash", list))
        assertTrue(ShellParsers.setupIsHome(list))
    }

    @Test
    fun `TVC_02 Google TV's recovery home counts as launcherx`() {
        val list = ShellParsers.homeActivities(homes)
        assertEquals(TvPackages.GOOGLE_HOME, ShellParsers.currentHome(TvPackages.GOOGLE_SETUP, list))
    }

    @Test
    fun `TVC_02 screensaver unset reads as empty, junk as unreadable`() {
        assertEquals("", ShellParsers.screensaver("null\n"))
        assertEquals("com.google.android.backdrop/.Backdrop", ShellParsers.screensaver("com.google.android.backdrop/.Backdrop\n"))
        assertNull(ShellParsers.screensaver("a b; rm"))
    }

    @Test
    fun `TVC_02 command outcomes are read from pm and cmd answers`() {
        assertTrue(ShellParsers.disabled("Package com.tcl.exhibit new state: disabled-user\n"))
        assertTrue(ShellParsers.enabled("Package com.tcl.exhibit new state: enabled\n"))
        assertFalse(ShellParsers.disabled("Error: java.lang.SecurityException"))
        assertTrue(ShellParsers.success("Success\n"))
    }

    @Test
    fun `TVC_07 accessibility services keep everyone else's and add ours once`() {
        val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        assertEquals(listOf(talkback, TvPackages.REMOTE_SERVICE), ShellParsers.accessibilityServices(talkback, TvPackages.REMOTE_SERVICE, on = true))
        assertEquals(listOf(TvPackages.REMOTE_SERVICE), ShellParsers.accessibilityServices("null", TvPackages.REMOTE_SERVICE, on = true))
        val shortForm = "io.shamash/.feature.tvcontrol.RemoteKeyService"
        assertTrue(ShellParsers.hasAccessibilityService("$talkback:$shortForm", TvPackages.REMOTE_SERVICE))
        assertEquals(listOf(talkback), ShellParsers.accessibilityServices("$talkback:$shortForm", TvPackages.REMOTE_SERVICE, on = false))
    }

    @Test
    fun `TVC_07 an unexpected accessibility entry leaves the setting alone`() {
        assertNull(ShellParsers.accessibilityServices("weird entry';reboot", TvPackages.REMOTE_SERVICE, on = true))
    }
}
