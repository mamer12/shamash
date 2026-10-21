package io.shamash.feature.tvcontrol

import io.shamash.feature.tvcontrol.adb.AdbError
import io.shamash.feature.tvcontrol.adb.AdbException
import io.shamash.feature.tvcontrol.adb.AdbShell
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherTest {
    private val tv = FakeTv()
    private val store = MemoryStore()
    private var aliasOn = false
    private val steps = mutableListOf<Step>()
    private var connector: AdbConnector = object : AdbConnector {
        override suspend fun <T> withShell(waitForApproval: Boolean, onApproval: () -> Unit, block: suspend (AdbShell) -> T) = block(tv)
    }

    private fun switcher(brand: Brand = Brand.TCL) =
        Switcher(connector, store, { aliasOn = it }, Device(brand, sdk = 34))

    @Test
    fun `TVC_02 the switch sets home, screensaver, app-ops and turns the listed apps off`() = runTest {
        val result = switcher().switchToShamash({ steps += it }) as SwitchResult.Switched

        assertEquals(TvPackages.SHAMASH, tv.home)
        assertTrue(aliasOn)
        assertEquals(TvPackages.SCREENSAVER, tv.screensaver)
        assertEquals(mapOf("GET_USAGE_STATS" to "allow", "SYSTEM_ALERT_WINDOW" to "allow"), tv.appOps)
        val expectedOff = listOf(tv.launcherx, TvPackages.GOOGLE_SETUP, tv.backdrop, "com.google.android.play.games", "com.tcl.exhibit", "com.tcl.logkit")
        assertEquals(expectedOff.sorted(), result.turnedOff)
        assertTrue(tv.disabled.containsAll(expectedOff))
        assertFalse("com.tcl.tv" in tv.disabled)
    }

    @Test
    fun `TVC_02 the TV's own home and screensaver are recorded before they change`() = runTest {
        switcher().switchToShamash({})
        val state = store.load()
        assertEquals(Mode.SHAMASH, state.mode)
        assertEquals(TvPackages.GOOGLE_HOME, state.originalHome)
        assertEquals("${tv.backdrop}/.Backdrop", state.originalScreensaver)
        assertFalse("com.user.disabled" in state.turnedOff)
    }

    @Test
    fun `TVC_02 a re-apply keeps the first record and turns off what an update turned back on`() = runTest {
        switcher().switchToShamash({})
        tv.disabled -= "com.tcl.exhibit"
        switcher().switchToShamash({}, waitForApproval = false)
        assertEquals(TvPackages.GOOGLE_HOME, store.load().originalHome)
        assertTrue("com.tcl.exhibit" in tv.disabled)
    }

    @Test
    fun `TVC_03 Wi-Fi saved by setup stops the switch before any change`() = runTest {
        tv.wifi = "cname=${TvPackages.GOOGLE_SETUP}\n"
        val result = switcher().switchToShamash({})
        assertEquals(SwitchResult.WifiOwned(listOf(TvPackages.GOOGLE_SETUP)), result)
        assertEquals(tv.launcherx, tv.home)
        assertFalse(aliasOn)
        assertEquals(setOf("com.user.disabled"), tv.disabled)
        assertTrue(tv.log.none { it.startsWith("pm disable") || it.startsWith("settings put") || it.startsWith("appops") })
    }

    @Test
    fun `TVC_02 Android refusing the home stops everything and leaves no HOME filter on`() = runTest {
        tv.acceptHome = false
        assertEquals(SwitchResult.Failed(Failure.HOME_REFUSED), switcher().switchToShamash({}))
        assertFalse(aliasOn)
        assertEquals(setOf("com.user.disabled"), tv.disabled)
        assertEquals(Mode.ORIGINAL, store.load().mode)
    }

    @Test
    fun `TVC_05 Original undoes the switch exactly`() = runTest {
        switcher().switchToShamash({})
        val result = switcher().restoreOriginal({}) as SwitchResult.Restored

        assertTrue(result.homeRestored)
        assertTrue(result.stillOff.isEmpty())
        assertEquals(tv.launcherx, tv.home)
        assertFalse(aliasOn)
        assertEquals("${tv.backdrop}/.Backdrop", tv.screensaver)
        assertEquals(mapOf("GET_USAGE_STATS" to "default", "SYSTEM_ALERT_WINDOW" to "default"), tv.appOps)
        assertEquals(setOf("com.user.disabled"), tv.disabled)
        assertEquals("null", tv.accessibilityServices)
        assertEquals("0", tv.accessibilityEnabled)
        assertEquals(Mode.ORIGINAL, store.load().mode)
    }

    @Test
    fun `TVC_07 remote buttons go on with the switch on TCL only, next to other services`() = runTest {
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        tv.accessibilityServices = talkback
        tv.accessibilityEnabled = "1"
        assertTrue((switcher().switchToShamash({}) as SwitchResult.Switched).remoteButtons)
        assertEquals("$talkback:${TvPackages.REMOTE_SERVICE}", tv.accessibilityServices)
        switcher().restoreOriginal({})
        assertEquals(talkback, tv.accessibilityServices)
        assertEquals("1", tv.accessibilityEnabled)
    }

    @Test
    fun `TVC_07 other brands get no remote-buttons service`() = runTest {
        assertFalse((switcher(Brand.OTHER).switchToShamash({}) as SwitchResult.Switched).remoteButtons)
        assertEquals("null", tv.accessibilityServices)
    }

    @Test
    fun `TVC_04 nothing is ever uninstalled or cleared`() = runTest {
        switcher().switchToShamash({})
        switcher().restoreOriginal({})
        assertTrue(tv.log.none { "uninstall" in it || "clear" in it })
        assertTrue(tv.log.filter { it.startsWith("pm disable") }.all { it.startsWith("pm disable-user --user 0 ") })
    }

    @Test
    fun `TVC_08 progress goes phase by phase and counts the apps`() = runTest {
        switcher().switchToShamash({ steps += it })
        val phases = steps.map { it.phase }.distinct()
        assertEquals(
            listOf(
                Phase.CONNECTING, Phase.READING_TV, Phase.SETTING_HOME, Phase.SETTING_SCREENSAVER,
                Phase.GRANTING_PERMISSIONS, Phase.TURNING_OFF_APPS, Phase.ENABLING_REMOTE_BUTTONS, Phase.DONE,
            ),
            phases,
        )
        val counts = steps.filter { it.phase == Phase.TURNING_OFF_APPS }
        assertEquals((1..6).toList(), counts.map { it.done })
        assertTrue(counts.all { it.total == 6 })
    }

    @Test
    fun `TVC_01 ADB errors become failures the screen can explain`() = runTest {
        connector = object : AdbConnector {
            override suspend fun <T> withShell(waitForApproval: Boolean, onApproval: () -> Unit, block: suspend (AdbShell) -> T): T {
                onApproval()
                throw AdbException(AdbError.NOT_APPROVED)
            }
        }
        assertEquals(SwitchResult.Failed(Failure.NOT_APPROVED), switcher().switchToShamash({ steps += it }))
        assertEquals(listOf(Phase.CONNECTING, Phase.WAITING_FOR_APPROVAL), steps.map { it.phase })
    }

    private class MemoryStore : ModeStore {
        private var state = TvState()
        override suspend fun load() = state
        override suspend fun save(state: TvState) {
            this.state = state
        }
    }
}
