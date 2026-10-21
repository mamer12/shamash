package io.shamash.feature.tvcontrol

import io.shamash.R

/** The words for each phase and failure (string resources, so they follow the TV's language). SPEC: TVC-08 */
object SwitchText {
    fun phase(phase: Phase): Int = when (phase) {
        Phase.CONNECTING -> R.string.phase_connecting
        Phase.WAITING_FOR_APPROVAL -> R.string.phase_approval
        Phase.READING_TV -> R.string.phase_reading
        Phase.SETTING_HOME -> R.string.phase_home
        Phase.SETTING_SCREENSAVER -> R.string.phase_screensaver
        Phase.GRANTING_PERMISSIONS -> R.string.phase_permissions
        Phase.TURNING_OFF_APPS -> R.string.phase_apps_off
        Phase.ENABLING_REMOTE_BUTTONS -> R.string.phase_buttons_on
        Phase.TURNING_ON_APPS -> R.string.phase_apps_on
        Phase.RESTORING_HOME -> R.string.phase_home_back
        Phase.RESTORING_SCREENSAVER -> R.string.phase_screensaver_back
        Phase.REVOKING_PERMISSIONS -> R.string.phase_permissions_back
        Phase.DISABLING_REMOTE_BUTTONS -> R.string.phase_buttons_off
        Phase.DONE -> R.string.phase_done
    }

    fun failure(failure: Failure): Int = when (failure) {
        Failure.DEBUGGING_OFF -> R.string.fail_debugging_off
        Failure.NO_RESPONSE -> R.string.fail_no_response
        Failure.NOT_APPROVED -> R.string.fail_not_approved
        Failure.KEY_REJECTED -> R.string.fail_key_rejected
        Failure.TLS_REQUIRED -> R.string.fail_tls
        Failure.PROTOCOL -> R.string.fail_protocol
        Failure.COMMAND_REFUSED -> R.string.fail_refused
        Failure.KEY_STORAGE -> R.string.fail_key_storage
        Failure.HOME_REFUSED -> R.string.fail_home_refused
        Failure.UNSAFE_INPUT -> R.string.fail_unsafe
        Failure.WIFI_UNREADABLE -> R.string.fail_wifi_unreadable
        Failure.UNKNOWN -> R.string.fail_unknown
    }
}
