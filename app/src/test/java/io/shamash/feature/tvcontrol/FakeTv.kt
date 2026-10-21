package io.shamash.feature.tvcontrol

import io.shamash.feature.tvcontrol.adb.AdbShell

/**
 * A Google TV on a TCL set, as its shell answers. Any command it doesn't know fails the test, so nothing outside the
 * vetted list (uninstall, clear…) can slip in.
 */
class FakeTv : AdbShell {
    val launcherx = "com.google.android.apps.tv.launcherx"
    val backdrop = "com.google.android.backdrop"

    val installed = mutableSetOf(
        launcherx, backdrop, TvPackages.GOOGLE_SETUP, TvPackages.SHAMASH,
        "com.tcl.tv", "com.tcl.exhibit", "com.tcl.logkit", "com.google.android.play.games", "com.user.disabled",
    )
    val disabled = mutableSetOf("com.user.disabled")
    var home = launcherx
    var screensaver: String? = "$backdrop/.Backdrop"
    var wifi = "Wi-Fi is enabled\n"
    var accessibilityServices = "null"
    var accessibilityEnabled = "0"
    var acceptHome = true
    val appOps = mutableMapOf<String, String>()
    val log = mutableListOf<String>()

    private val homes = listOf("$launcherx/.home.HomeActivity", "${TvPackages.GOOGLE_SETUP}/.RecoveryActivity", TvPackages.HOME_ALIAS)

    override fun run(command: String): String {
        log += command
        return fixed(command) ?: changing(command) ?: error("unexpected command: $command")
    }

    private fun fixed(command: String): String? = when (command) {
        Commands.LIST_PACKAGES -> installed.joinToString("\n") { "package:$it" }
        Commands.LIST_DISABLED -> disabled.joinToString("\n") { "package:$it" }
        Commands.WIFI_OWNERS -> wifi
        Commands.HOME_ROLE_HOLDERS -> "$home\n"
        Commands.RESOLVE_HOME -> "priority=0\n${homes.first { it.startsWith("$home/") }}\n"
        Commands.QUERY_HOMES -> homes.joinToString("\n")
        Commands.GET_SCREENSAVER -> screensaver ?: "null"
        Commands.DELETE_SCREENSAVER -> "".also { screensaver = null }
        Commands.GET_ACCESSIBILITY_SERVICES -> accessibilityServices
        Commands.DELETE_ACCESSIBILITY_SERVICES -> "".also { accessibilityServices = "null" }
        Commands.GET_ACCESSIBILITY_ENABLED -> accessibilityEnabled
        else -> null
    }

    private fun changing(command: String): String? {
        val arg = command.substringAfterLast(' ').removeSurrounding("'")
        return when {
            command.startsWith("pm disable-user --user 0 ") -> "Package $arg new state: disabled-user".also { disabled += arg }
            command.startsWith("pm enable ") -> "Package $arg new state: enabled".also { disabled -= arg }
            command.startsWith("cmd package set-home-activity ") -> setHome(arg)
            command.startsWith("cmd role add-role-holder") -> ""
            command.startsWith("cmd role remove-role-holder") -> ""
            command.startsWith("settings put secure screensaver_components ") -> "".also { screensaver = arg }
            command.startsWith("settings put secure enabled_accessibility_services ") ->
                "".also { accessibilityServices = arg.trim('\'') }
            command.startsWith("settings put secure accessibility_enabled ") -> "".also { accessibilityEnabled = arg }
            command.startsWith("appops set io.shamash ") -> "".also {
                val parts = command.split(' ')
                appOps[parts[3]] = parts[4]
            }
            else -> null
        }
    }

    private fun setHome(component: String): String {
        if (!acceptHome) return "Error: component not found"
        home = ShellParsers.packageOf(component)
        return "Success"
    }
}
