package io.shamash.feature.tvcontrol

import io.shamash.data.Logger
import io.shamash.feature.tvcontrol.adb.AdbException
import io.shamash.feature.tvcontrol.adb.AdbShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Opens a session with the TV's ADB for [block] and closes it after. */
interface AdbConnector {
    suspend fun <T> withShell(waitForApproval: Boolean, onApproval: () -> Unit, block: suspend (AdbShell) -> T): T
}

/** Turns Shamash's HOME activity-alias on or off (in-app, so Shamash isn't killed mid-switch). */
fun interface HomeAlias {
    fun setEnabled(enabled: Boolean)
}

/** The TV running Shamash. [sdk] decides whether `cmd role` exists (Android 10+). */
data class Device(val brand: Brand, val sdk: Int) {
    val hasRoles get() = sdk >= ROLES_SDK
    val hasRemoteButtons get() = brand == Brand.TCL

    private companion object { const val ROLES_SDK = 29 }
}

/** What the TV reports before a switch; [wifiOwners] is null when it couldn't be read. */
private data class TvSnapshot(
    val installed: Set<String>,
    val disabled: Set<String>,
    val wifiOwners: Set<String>?,
    val currentHome: String?,
    val setupIsHome: Boolean,
    val screensaver: String?,
)

/**
 * Original ⇄ Shamash through the TV's own ADB, the way a person with a computer would. Every step is logged (by the
 * client) and reported through `progress`; one switch runs at a time. SPEC: TVC-02, TVC-03, TVC-05, TVC-08
 */
class Switcher(
    private val adb: AdbConnector,
    private val store: ModeStore,
    private val alias: HomeAlias,
    private val device: Device,
) {
    private val remote = RemoteButtonsSetting(store)

    /** Makes Shamash the home screen. Run again in Shamash mode, it re-applies what a TV update undid. */
    suspend fun switchToShamash(progress: (Step) -> Unit, waitForApproval: Boolean = true): SwitchResult =
        session(progress, waitForApproval) { shell -> applyShamash(shell, progress) }

    /** Original mode: everything Shamash changed goes back. */
    suspend fun restoreOriginal(progress: (Step) -> Unit, waitForApproval: Boolean = true): SwitchResult =
        session(progress, waitForApproval) { shell -> Restorer(shell, store, alias, device, remote).restore(progress) }

    private suspend fun session(
        progress: (Step) -> Unit,
        waitForApproval: Boolean,
        block: suspend (AdbShell) -> SwitchResult,
    ): SwitchResult = LOCK.withLock {
        try {
            progress(Step(Phase.CONNECTING))
            adb.withShell(waitForApproval, { progress(Step(Phase.WAITING_FOR_APPROVAL)) }, block)
        } catch (e: AdbException) {
            Logger.w("TV control stopped", e)
            SwitchResult.Failed(Failure.of(e.error))
        } catch (e: UnsafeInputException) {
            Logger.w("TV control refused a name", e)
            SwitchResult.Failed(Failure.UNSAFE_INPUT)
        } catch (e: IOException) {
            Logger.w("TV control lost the connection", e)
            SwitchResult.Failed(Failure.NO_RESPONSE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never crash mid-switch: what was done is on record, and Original (or a retry) picks it up from there.
            Logger.w("TV control stopped unexpectedly", e)
            SwitchResult.Failed(Failure.UNKNOWN)
        }
    }

    private suspend fun applyShamash(shell: AdbShell, progress: (Step) -> Unit): SwitchResult {
        progress(Step(Phase.READING_TV))
        val tv = read(shell)
        val wifiOwners = tv.wifiOwners ?: return SwitchResult.Failed(Failure.WIFI_UNREADABLE)
        val state = record(store.load(), tv)
        store.save(state)
        val plan = SwitchPlanner.plan(
            SwitchInput(device.brand, tv.installed, wifiOwners, state.originalHome, state.originalScreensaver, tv.setupIsHome),
        )
        if (plan.blockedBy.isNotEmpty()) return SwitchResult.WifiOwned(plan.blockedBy)

        progress(Step(Phase.SETTING_HOME))
        if (!setHome(shell, wasShamash = state.mode == Mode.SHAMASH)) return SwitchResult.Failed(Failure.HOME_REFUSED)
        store.save(state.copy(mode = Mode.SHAMASH))

        progress(Step(Phase.SETTING_SCREENSAVER))
        shell.run(Commands.putScreensaver(TvPackages.SCREENSAVER))
        progress(Step(Phase.GRANTING_PERMISSIONS))
        Commands.APP_OPS.forEach { shell.run(Commands.appOp(TvPackages.SHAMASH, it, allow = true)) }

        val refused = turnOff(shell, plan.turnOff, tv.disabled, progress)
        val remoteOn = device.hasRemoteButtons && run {
            progress(Step(Phase.ENABLING_REMOTE_BUTTONS))
            remote.turnOn(shell)
        }
        progress(Step(Phase.DONE))
        return SwitchResult.Switched(store.load().turnedOff.sorted(), refused, plan.keptOn, remoteOn)
    }

    private fun read(shell: AdbShell): TvSnapshot {
        val homes = ShellParsers.homeActivities(shell.run(Commands.QUERY_HOMES))
        val roleOutput = if (device.hasRoles) shell.run(Commands.HOME_ROLE_HOLDERS) else ""
        val holder = ShellParsers.homeHolder(roleOutput, shell.run(Commands.RESOLVE_HOME))
        return TvSnapshot(
            installed = ShellParsers.packages(shell.run(Commands.LIST_PACKAGES)),
            disabled = ShellParsers.packages(shell.run(Commands.LIST_DISABLED)),
            wifiOwners = ShellParsers.wifiOwners(shell.run(Commands.WIFI_OWNERS)),
            currentHome = ShellParsers.currentHome(holder, homes),
            setupIsHome = ShellParsers.setupIsHome(homes),
            screensaver = ShellParsers.screensaver(shell.run(Commands.GET_SCREENSAVER)),
        )
    }

    /**
     * Records the TV's own home screen and screensaver once, before Shamash replaces them. In Shamash mode (a
     * re-apply) the first record stays; a reading that is Shamash itself never overwrites it.
     */
    private fun record(state: TvState, tv: TvSnapshot): TvState {
        if (state.mode == Mode.SHAMASH && state.originalHome != null) return state
        val screensaver = tv.screensaver?.takeIf { !it.startsWith(TvPackages.SHAMASH + "/") }
        return state.copy(
            originalHome = tv.currentHome ?: state.originalHome,
            originalScreensaver = screensaver ?: state.originalScreensaver,
        )
    }

    /** HOME alias on, then Shamash as the home activity (and the HOME role where `cmd role` exists). */
    private fun setHome(shell: AdbShell, wasShamash: Boolean): Boolean {
        alias.setEnabled(true)
        if (!ShellParsers.success(shell.run(Commands.setHome(TvPackages.HOME_ALIAS)))) {
            if (!wasShamash) alias.setEnabled(false) // no stray HOME filter: the chooser must never show
            return false
        }
        if (device.hasRoles) shell.run(Commands.addHomeRole(TvPackages.SHAMASH)) // logged; some TVs answer "Unknown"
        return true
    }

    /**
     * Turns off [packages] one by one, recording each right away (a crash mid-way must not lose track), and returns
     * the ones Android refused. Apps already off are skipped: off before Shamash, they aren't Shamash's to turn on.
     * A protected package (TVC-04) is never run, whatever asked for it: it counts as refused.
     */
    private suspend fun turnOff(
        shell: AdbShell,
        packages: List<String>,
        alreadyOff: Set<String>,
        progress: (Step) -> Unit,
    ): List<String> {
        val (guarded, todo) = packages.filter { it !in alreadyOff }.partition { it in BrandProfiles.PROTECTED }
        if (guarded.isNotEmpty()) Logger.w("TV control kept protected apps on: $guarded")
        return guarded + todo.mapIndexedNotNull { i, pkg ->
            progress(Step(Phase.TURNING_OFF_APPS, i + 1, todo.size))
            if (ShellParsers.disabled(shell.run(Commands.disable(pkg)))) {
                val state = store.load()
                store.save(state.copy(turnedOff = state.turnedOff + pkg))
                null
            } else {
                pkg
            }
        }
    }

    private companion object {
        val LOCK = Mutex()
    }
}
