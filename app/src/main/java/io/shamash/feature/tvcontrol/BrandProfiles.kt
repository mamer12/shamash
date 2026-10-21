package io.shamash.feature.tvcontrol

/** A TV maker whose promo apps Shamash knows. */
enum class Brand { TCL, OTHER }

/**
 * What Shamash turns off in Shamash mode, per brand, and what it never touches. Add a brand: read what the TV has
 * (`pm list packages -s`), list only promo, demo, store-mode and telemetry apps, one reason each. SPEC: TVC-06, TVC-04
 */
object BrandProfiles {

    /** Google TV / Android TV extras, on every brand. */
    private val GOOGLE_TV = listOf(
        "com.google.android.play.games",          // Play Games: a store front on the home screen
        "com.google.android.youtube.tvmusic",     // YouTube Music: preinstalled promo (reinstallable from Play)
    )

    private val TCL = listOf(
        "com.tcl.magiconnectfree",                // TCL's phone-mirroring promo app
        "com.tcl.miracast",                       // TCL's screen-mirroring app (Cast keeps working)
        "com.tcl.waterfall.overseas",             // TCL Channel: ad-supported channels pushed on the home screen
        "com.tcl.channelplus",                    // TCL Channel's live TV feed
        "com.tcl.messagebox",                     // TCL marketing messages
        "com.tcl.usercenter",                     // TCL account promo
        "com.tcl.exhibit",                        // store demo mode
        "com.tcl.esticker",                       // store demo stickers
        "com.tcl.hotelmenu",                      // hotel menu
        "com.tcl.repairguide",                    // repair guide promo
        "com.tcl.ocean.instructions",             // on-screen manual
        "com.tcl.logkit",                         // log collection (telemetry)
    )

    /** Promo and telemetry packages to turn off on [brand] (Google's extras first). */
    fun promo(brand: Brand): List<String> = when (brand) {
        Brand.TCL -> GOOGLE_TV + TCL
        Brand.OTHER -> GOOGLE_TV
    }

    fun brandOf(manufacturer: String, brand: String): Brand =
        if (manufacturer.equals("TCL", ignoreCase = true) || brand.equals("TCL", ignoreCase = true)) Brand.TCL
        else Brand.OTHER

    /**
     * Never turned off, whatever a list says: picture, sound, inputs, tuner and live TV, HDMI-CEC, remote and
     * Bluetooth, setup, updates, system UI, Play Store and Google services. Checked before every disable.
     */
    val PROTECTED: Set<String> = setOf(
        // Android and Google
        "android", "com.android.systemui", "com.android.tv.settings", "com.android.vending",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.apps.mediashell",
        "com.google.android.tv.remote.service", "com.google.android.inputmethod.latin", "com.google.android.webview",
        "com.android.providers.tv", "com.android.bluetooth", "com.android.tv", "com.google.android.tv",
        "com.android.providers.settings", "com.android.shell", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.externalstorage",
        // TCL: picture, sound, inputs, tuner, CEC, remote, setup, updates, system
        "com.tcl.tv", "com.tcl.ttvs", "com.tcl.tvinput", "com.tvos", "com.tcl.systemserver",
        "com.tcl.systemui.plugin", "com.tcl.hearaid", "com.dolby.android.audio.service", "com.tcl.globalkeyoverlay",
        "com.tcl.autopair", "com.tcl.UpdatePeripheral", "com.tcl.dashboard", "com.tcl.suspension",
        "com.tcl.providers.config", "com.tcl.gamebar", "com.tcl.airplay2", "com.mirakulo.astrotv.service",
        "com.tcl.initsetup", "com.tcl.useragreement", "com.tcl.factory.view", "com.tcl.guard", "com.tcl.t_solo",
        // MediaTek and Philips (TP Vision): live TV, inputs, setup, factory menu, Ambilight
        "com.mediatek.tv.service", "com.mediatek.tvinput", "com.mediatek.network", "com.mediatek.wwtv.tvcenter",
        "com.mediatek.wwtv.setupwizard", "com.mediatek.tv.factory", "mediatek.factorymenu.ui",
        "org.droidtv", "org.droidtv.ambisvc", "org.droidtv.ui.strings", "org.droidtv.tv.packageinfo",
        "org.droidtv.icp", "org.droidtv.gel", "org.droidtv.eum", "com.tpv.xmic.ots.cms.client",
        // Shamash itself
        TvPackages.SHAMASH,
    )
}

/** Packages and components the switch needs to know by name. */
object TvPackages {
    const val SHAMASH = "io.shamash"
    const val HOME_ALIAS_CLASS = "io.shamash.HomeAlias"
    const val HOME_ALIAS = "$SHAMASH/.HomeAlias"
    const val SCREENSAVER = "$SHAMASH/.feature.screensaver.ShamashDream"
    const val REMOTE_SERVICE = "$SHAMASH/io.shamash.feature.tvcontrol.RemoteKeyService"

    /** Google TV's home screen: where Original goes back when the TV's own wasn't recorded. */
    const val GOOGLE_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity"

    /**
     * Google TV's setup app also registers a RecoveryActivity for HOME that takes over once the launcher is off, so it
     * goes off too when it is one of the TV's home screens. It often saved the Wi-Fi during first setup (TVC-03).
     */
    const val GOOGLE_SETUP = "com.google.android.tungsten.setupwraith"

    /** Classic Android TV's launcher; its recommendations service crashes on the screensaver while it is off. */
    const val TV_LAUNCHER = "com.google.android.tvlauncher"
    const val TV_RECOMMENDATIONS = "com.google.android.tvrecommendations"
}
