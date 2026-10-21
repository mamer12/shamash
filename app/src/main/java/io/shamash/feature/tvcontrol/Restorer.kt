package io.shamash.feature.tvcontrol

import io.shamash.data.Logger
import io.shamash.feature.tvcontrol.adb.AdbShell

/**
 * Original mode: turns back on what Shamash turned off, gives HOME back to the recorded launcher, puts back the
 * screensaver, takes back the app-ops and the remote-buttons service, and turns the HOME alias off. SPEC: TVC-05
 */
internal class Restorer(
    private val shell: AdbShell,
    private val store: ModeStore,
    private val alias: HomeAlias,
    private val device: Device,
    private val remote: RemoteButtonsSetting,
) {
    suspend fun restore(progress: (Step) -> Unit): SwitchResult {
        progress(Step(Phase.READING_TV))
        val state = store.load()
        val installed = ShellParsers.packages(shell.run(Commands.LIST_PACKAGES))
        val disabledNow = ShellParsers.packages(shell.run(Commands.LIST_DISABLED))
        val targets = SwitchPlanner.restoreTargets(
            state.turnedOff, state.originalHome, state.originalScreensaver, disabledNow,
        )
        val stillOff = turnOn(targets, progress)

        progress(Step(Phase.RESTORING_HOME))
        val homeRestored = restoreHome(state.originalHome, installed)
        progress(Step(Phase.RESTORING_SCREENSAVER))
        restoreScreensaver(state.originalScreensaver)
        progress(Step(Phase.REVOKING_PERMISSIONS))
        Commands.APP_OPS.forEach { shell.run(Commands.appOp(TvPackages.SHAMASH, it, allow = false)) }
        progress(Step(Phase.DISABLING_REMOTE_BUTTONS))
        remote.turnOff(shell)

        store.save(store.load().copy(mode = Mode.ORIGINAL, turnedOff = stillOff.toSet()))
        progress(Step(Phase.DONE))
        return SwitchResult.Restored(targets - stillOff.toSet(), stillOff, homeRestored)
    }

    /** Turns each app back on; returns the ones that stayed off. */
    private fun turnOn(targets: List<String>, progress: (Step) -> Unit): List<String> =
        targets.mapIndexedNotNull { i, pkg ->
            progress(Step(Phase.TURNING_ON_APPS, i + 1, targets.size))
            pkg.takeUnless { ShellParsers.enabled(shell.run(Commands.enable(pkg))) }
        }

    /**
     * The recorded launcher (or Google TV's when none was recorded) becomes home again; then the HOME alias goes
     * off, so even if that failed the TV can only pick one of its own home screens.
     */
    private fun restoreHome(originalHome: String?, installed: Set<String>): Boolean {
        val home = originalHome?.takeIf { it.isNotEmpty() }
            ?: TvPackages.GOOGLE_HOME.takeIf { ShellParsers.packageOf(it) in installed }
        val set = home != null && ShellParsers.success(shell.run(Commands.setHome(home)))
        alias.setEnabled(false)
        if (set) return true
        if (device.hasRoles) shell.run(Commands.removeHomeRole(TvPackages.SHAMASH))
        val roles = if (device.hasRoles) shell.run(Commands.HOME_ROLE_HOLDERS) else ""
        val holder = ShellParsers.homeHolder(roles, shell.run(Commands.RESOLVE_HOME))
        return holder != null && holder != TvPackages.SHAMASH
    }

    /** Only while the screensaver is still Shamash's: a screensaver the user picked since then stays. */
    private fun restoreScreensaver(original: String?) {
        val current = ShellParsers.screensaver(shell.run(Commands.GET_SCREENSAVER)) ?: return
        if (!current.startsWith(TvPackages.SHAMASH + "/")) return
        val command = if (original.isNullOrEmpty()) Commands.DELETE_SCREENSAVER else Commands.putScreensaver(original)
        shell.run(command)
    }
}

/**
 * Shamash's remote-buttons accessibility service in the enabled list, next to anyone else's (TalkBack…). On only in
 * Shamash mode. A name that fails the shell-safety check leaves the setting exactly as it was: the buttons are an
 * extra, never a reason to fail a switch half-way. SPEC: TVC-07
 */
internal class RemoteButtonsSetting(
    private val store: ModeStore,
    private val service: String = TvPackages.REMOTE_SERVICE,
) {

    /** True when the service is on (or already was); false when it was left alone. */
    suspend fun turnOn(shell: AdbShell): Boolean {
        val current = shell.run(Commands.GET_ACCESSIBILITY_SERVICES)
        if (ShellParsers.hasAccessibilityService(current, service)) return true
        val wanted = ShellParsers.accessibilityServices(current, service, on = true) ?: return false
        val command = safely { Commands.putAccessibilityServices(wanted) } ?: return false // built before any change
        val wasOff = shell.run(Commands.GET_ACCESSIBILITY_ENABLED).trim() != "1"
        store.save(store.load().copy(accessibilityWasOff = wasOff))
        shell.run(command)
        shell.run(Commands.accessibilityEnabled(true))
        return true
    }

    suspend fun turnOff(shell: AdbShell) {
        val current = shell.run(Commands.GET_ACCESSIBILITY_SERVICES)
        if (!ShellParsers.hasAccessibilityService(current, service)) return
        val wanted = ShellParsers.accessibilityServices(current, service, on = false) ?: return
        if (wanted.isNotEmpty()) {
            safely { Commands.putAccessibilityServices(wanted) }?.let(shell::run)
            return
        }
        shell.run(Commands.DELETE_ACCESSIBILITY_SERVICES)
        if (store.load().accessibilityWasOff) shell.run(Commands.accessibilityEnabled(false))
    }

    private fun safely(build: () -> String): String? = try {
        build()
    } catch (e: UnsafeInputException) {
        Logger.w("remote buttons: setting left alone", e)
        null
    }
}
