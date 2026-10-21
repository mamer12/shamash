package io.shamash.feature.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchPlannerTest {
    private val launcherx = "com.google.android.apps.tv.launcherx"
    private val backdrop = "com.google.android.backdrop"

    private fun input(
        brand: Brand = Brand.TCL,
        installed: Set<String> = setOf(launcherx, backdrop, TvPackages.GOOGLE_SETUP, "com.tcl.exhibit", "com.tcl.logkit", "com.tcl.tv"),
        wifiOwners: Set<String> = emptySet(),
        setupIsHome: Boolean = true,
    ) = SwitchInput(brand, installed, wifiOwners, TvPackages.GOOGLE_HOME, "$backdrop/.Backdrop", setupIsHome)

    @Test
    fun `TVC_02 the TV's home, its recovery home, its screensaver and the brand's promo apps go off`() {
        val plan = SwitchPlanner.plan(input())
        assertEquals(listOf(launcherx, TvPackages.GOOGLE_SETUP, backdrop, "com.tcl.exhibit", "com.tcl.logkit"), plan.turnOff)
        assertTrue(plan.blockedBy.isEmpty())
    }

    @Test
    fun `TVC_02 apps the TV doesn't have aren't planned`() {
        val plan = SwitchPlanner.plan(input(installed = setOf(launcherx)))
        assertEquals(listOf(launcherx), plan.turnOff)
    }

    @Test
    fun `TVC_03 setup owning the Wi-Fi stops the switch before anything changes`() {
        val plan = SwitchPlanner.plan(input(wifiOwners = setOf(TvPackages.GOOGLE_SETUP)))
        assertEquals(listOf(TvPackages.GOOGLE_SETUP), plan.blockedBy)
        assertTrue(plan.turnOff.isEmpty())
    }

    @Test
    fun `TVC_03 a promo app owning the Wi-Fi is kept on, the rest goes ahead`() {
        val plan = SwitchPlanner.plan(input(wifiOwners = setOf("com.tcl.exhibit")))
        assertFalse("com.tcl.exhibit" in plan.turnOff)
        assertEquals(listOf("com.tcl.exhibit"), plan.keptOn)
        assertTrue(plan.blockedBy.isEmpty())
    }

    @Test
    fun `TVC_04 protected packages never go off, even as the TV's own home`() {
        val tclHome = SwitchInput(Brand.TCL, setOf("com.tcl.tv", "com.tcl.exhibit"), emptySet(), "com.tcl.tv/.Home", null, false)
        val plan = SwitchPlanner.plan(tclHome)
        assertEquals(listOf("com.tcl.exhibit"), plan.turnOff)
        assertTrue(plan.turnOff.none { it in BrandProfiles.PROTECTED })
    }

    @Test
    fun `TVC_06 other brands get only Google's extras`() {
        val installed = setOf("com.tcl.exhibit", "com.google.android.play.games")
        val plan = SwitchPlanner.plan(SwitchInput(Brand.OTHER, installed, emptySet(), null, null, false))
        assertEquals(listOf("com.google.android.play.games"), plan.turnOff)
    }

    @Test
    fun `TVC_02 Shamash never plans to turn itself off`() {
        val plan = SwitchPlanner.plan(
            SwitchInput(Brand.OTHER, setOf(TvPackages.SHAMASH), emptySet(), TvPackages.HOME_ALIAS, TvPackages.SCREENSAVER, false),
        )
        assertTrue(plan.turnOff.isEmpty())
    }

    @Test
    fun `TVC_05 Original turns back on what was recorded plus the TV's home and setup, only if off now`() {
        val targets = SwitchPlanner.restoreTargets(
            recorded = listOf("com.tcl.exhibit", "com.tcl.logkit"),
            originalHome = TvPackages.GOOGLE_HOME,
            originalScreensaver = "$backdrop/.Backdrop",
            disabledNow = setOf("com.tcl.exhibit", launcherx, TvPackages.GOOGLE_SETUP, backdrop, "com.user.choice"),
        )
        assertEquals(listOf("com.tcl.exhibit", launcherx, TvPackages.GOOGLE_SETUP, backdrop), targets)
    }
}
