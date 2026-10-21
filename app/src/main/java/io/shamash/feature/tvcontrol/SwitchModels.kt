package io.shamash.feature.tvcontrol

import io.shamash.feature.tvcontrol.adb.AdbError

/** Where a switch is; the Settings screen shows each phase in the user's language. SPEC: TVC-08 */
enum class Phase {
    CONNECTING,
    /** The TV shows "Allow debugging?": tell the user to accept and tick "Always allow". */
    WAITING_FOR_APPROVAL,
    READING_TV,
    SETTING_HOME,
    SETTING_SCREENSAVER,
    GRANTING_PERMISSIONS,
    TURNING_OFF_APPS,
    ENABLING_REMOTE_BUTTONS,
    TURNING_ON_APPS,
    RESTORING_HOME,
    RESTORING_SCREENSAVER,
    REVOKING_PERMISSIONS,
    DISABLING_REMOTE_BUTTONS,
    DONE,
}

/** One progress update: [done] of [total] for phases that go app by app (0 of 0 otherwise). */
data class Step(val phase: Phase, val done: Int = 0, val total: Int = 0)

/** Why a switch stopped. The first eight come from the ADB connection. */
enum class Failure {
    DEBUGGING_OFF, NO_RESPONSE, NOT_APPROVED, KEY_REJECTED, TLS_REQUIRED, PROTOCOL, COMMAND_REFUSED, KEY_STORAGE,
    /** Android refused Shamash as the home screen (nothing else was changed). */
    HOME_REFUSED,
    /** A package or component name failed the shell-safety check (nothing was run with it). */
    UNSAFE_INPUT,
    /** The TV's saved Wi-Fi networks couldn't be read, so nothing was changed (TVC-03 fails closed). */
    WIFI_UNREADABLE,
    /** Something unexpected went wrong; the step it stopped at is in the log. */
    UNKNOWN,
    ;

    companion object {
        fun of(error: AdbError): Failure = when (error) {
            AdbError.DEBUGGING_OFF -> DEBUGGING_OFF
            AdbError.NO_RESPONSE -> NO_RESPONSE
            AdbError.NOT_APPROVED -> NOT_APPROVED
            AdbError.KEY_REJECTED -> KEY_REJECTED
            AdbError.TLS_REQUIRED -> TLS_REQUIRED
            AdbError.PROTOCOL -> PROTOCOL
            AdbError.COMMAND_REFUSED -> COMMAND_REFUSED
            AdbError.KEY_STORAGE -> KEY_STORAGE
        }
    }
}

/** How a switch ended: what changed, or why nothing did. SPEC: TVC-08 */
sealed interface SwitchResult {
    /** Shamash is home. [turnedOff]: all apps off because of Shamash; [notTurnedOff]: Android refused these. */
    data class Switched(
        val turnedOff: List<String>,
        val notTurnedOff: List<String>,
        val keptOnForWifi: List<String>,
        val remoteButtons: Boolean,
    ) : SwitchResult

    /** Original mode. [stillOff]: apps that didn't come back (kept on record, so a retry tries again). */
    data class Restored(val turnedOn: List<String>, val stillOff: List<String>, val homeRestored: Boolean) : SwitchResult

    /**
     * Stopped before changing anything: [packages] saved the Wi-Fi network, and turning them off would make Android
     * erase it. The user must forget the network and connect again (Settings › Network & Internet). SPEC: TVC-03
     */
    data class WifiOwned(val packages: List<String>) : SwitchResult

    data class Failed(val failure: Failure) : SwitchResult
}
