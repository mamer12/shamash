package io.shamash.feature.tvcontrol

import io.shamash.feature.tvcontrol.adb.AdbShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityRegressionTest {
    private class MemoryStore : ModeStore {
        var state = TvState()
        override suspend fun load() = state
        override suspend fun save(state: TvState) { this.state = state }
    }

    private fun connector(shell: AdbShell) = object : AdbConnector {
        override suspend fun <T> withShell(waitForApproval: Boolean, onApproval: () -> Unit, block: suspend (AdbShell) -> T) = block(shell)
    }

    @Test fun `TVC_04 protected packages are rejected at command construction`() {
        BrandProfiles.PROTECTED.forEach { pkg ->
            assertThrows(UnsafeInputException::class.java) { Commands.disable(pkg) }
            if (pkg != TvPackages.SHAMASH) assertThrows(UnsafeInputException::class.java) { Commands.removeHomeRole(pkg) }
        }
        assertTrue(Commands.removeHomeRole(TvPackages.SHAMASH).endsWith(TvPackages.SHAMASH))
    }

    @Test fun `TVC_02 inner class dollars are literal shell arguments`() {
        val component = "com.example/.Outer\$Inner"
        assertEquals("cmd package set-home-activity '$component'", Commands.setHome(component))
        assertEquals("settings put secure screensaver_components '$component'", Commands.putScreensaver(component))
        assertEquals("settings put secure enabled_accessibility_services '$component'", Commands.putAccessibilityServices(listOf(component)))
    }

    @Test fun `TVC_03 failed malformed and empty dumps never mean no owners`() = runTest {
        listOf("", "permission denied", "Can't find service: wifi", "Wi-Fi is nonsense", "cname=", "cname=com.safe;bad", "cname=com.safe:bad").forEach { dump ->
            assertNull(dump, ShellParsers.wifiOwners(dump))
            val tv = FakeTv().apply { wifi = dump }
            val store = MemoryStore()
            var alias = false
            val switcher = Switcher(connector(tv), store, { alias = it }, Device(Brand.TCL, 34))
            assertEquals(SwitchResult.Failed(Failure.WIFI_UNREADABLE), switcher.switchToShamash({}))
            assertFalse(alias)
            assertEquals(TvState(), store.state)
            assertTrue(tv.log.none { it.startsWith("pm disable") || it.startsWith("settings put") || it.startsWith("appops") })
        }
        assertEquals(emptySet<String>(), ShellParsers.wifiOwners("Wi-Fi is disabled\n"))
    }

    @Test fun `TVC_08 unexpected session exceptions return a recoverable failure`() = runTest {
        val shell = object : AdbShell { override fun run(command: String): String = error("unexpected failure") }
        val switcher = Switcher(connector(shell), MemoryStore(), {}, Device(Brand.TCL, 34))
        assertEquals(SwitchResult.Failed(Failure.UNKNOWN), switcher.switchToShamash({}))
    }

    @Test fun `TVC_05 unexpected mid-switch errors retain enough state for Original`() = runTest {
        val tv = FakeTv()
        val store = MemoryStore()
        var alias = false
        val shell = object : AdbShell {
            override fun run(command: String): String {
                if (command == Commands.putScreensaver(TvPackages.SCREENSAVER)) error("unexpected failure")
                return tv.run(command)
            }
        }
        val switcher = Switcher(connector(shell), store, { alias = it }, Device(Brand.TCL, 34))
        assertEquals(SwitchResult.Failed(Failure.UNKNOWN), switcher.switchToShamash({}))
        assertEquals(Mode.SHAMASH, store.state.mode)
        assertEquals(TvPackages.GOOGLE_HOME, store.state.originalHome)
        assertTrue(alias)
        val result = switcher.restoreOriginal({}) as SwitchResult.Restored
        assertTrue(result.homeRestored)
        assertFalse(alias)
        assertEquals(tv.launcherx, tv.home)
        assertEquals(Mode.ORIGINAL, store.state.mode)
    }

    @Test fun `TVC_08 session cancellation remains cancellation`() = runTest {
        val shell = object : AdbShell { override fun run(command: String): String = throw CancellationException("stop") }
        val switcher = Switcher(connector(shell), MemoryStore(), {}, Device(Brand.TCL, 34))
        try {
            switcher.switchToShamash({})
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun `TVC_08 screen always finishes after an unexpected switch exception`() = runTest {
        val tv = object : ModeSwitcher {
            override suspend fun isShamashMode(): Boolean = error("store failure")
            override suspend fun switch(progress: (Step) -> Unit): SwitchResult = error("unexpected failure")
            override suspend fun restore(progress: (Step) -> Unit): SwitchResult = error("unexpected failure")
        }
        val model = SwitchModel(tv, this)
        model.switch()
        advanceUntilIdle()
        assertEquals(SwitchState.Finished(SwitchResult.Failed(Failure.UNKNOWN), false), model.state.value)
    }

    @Test fun `TVC_07 invalid remote service never partially applies accessibility settings`() = runTest {
        val tv = FakeTv()
        val store = MemoryStore()
        assertFalse(RemoteButtonsSetting(store, "invalid service").turnOn(tv))
        assertEquals("null", tv.accessibilityServices)
        assertEquals("0", tv.accessibilityEnabled)
        assertEquals(TvState(), store.state)
        assertTrue(tv.log.none { it.startsWith("settings put") })
    }

    @Test fun `TVC_05 failed and incomplete restores retry while completed restores stop`() {
        assertTrue(TvControlRetry.shouldRetry(SwitchResult.Failed(Failure.NO_RESPONSE)))
        assertTrue(TvControlRetry.shouldRetry(SwitchResult.Restored(emptyList(), listOf("com.promo"), true)))
        assertTrue(TvControlRetry.shouldRetry(SwitchResult.Restored(emptyList(), emptyList(), false)))
        assertFalse(TvControlRetry.shouldRetry(SwitchResult.Restored(listOf("com.promo"), emptyList(), true)))
        assertFalse(TvControlRetry.shouldRetry(SwitchResult.WifiOwned(listOf("com.setup"))))
        assertFalse(TvControlRetry.shouldRetry(null))
    }
}
