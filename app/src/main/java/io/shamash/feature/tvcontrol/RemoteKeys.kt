package io.shamash.feature.tvcontrol

import android.view.InputDevice
import io.shamash.data.Preferences

/**
 * The remote's app buttons on TCL TVs (keycodes read from a TCL's log; other brands may use them for other buttons,
 * so nothing here runs there). YOUTUBE is KEYCODE_BUTTON_3, which only an accessibility key filter sees. SPEC: TVC-07
 */
enum class RemoteButton(val keyCode: Int, val defaultApp: String) {
    NETFLIX(4062, "com.netflix.ninja"),
    YOUTUBE(190, "com.google.android.youtube.tv"),
    PRIME(4099, "com.amazon.amazonvideo.livingroom"),
    TCL_CHANNEL(4020, ""),
    ;

    companion object {
        fun of(keyCode: Int): RemoteButton? = entries.firstOrNull { it.keyCode == keyCode }
    }
}

object RemoteKeys {
    private const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
    private const val JOYSTICK = InputDevice.SOURCE_JOYSTICK

    fun secretKey(button: RemoteButton) = "remote_${button.keyCode}"

    /** BUTTON_3 is also a game controller's button: keys from a gamepad or joystick are left alone. */
    fun isFromController(source: Int): Boolean =
        (source and GAMEPAD) == GAMEPAD || (source and JOYSTICK) == JOYSTICK

    /** The app each button opens ("" = nothing). Unmapped buttons open their own app. */
    suspend fun mapping(prefs: Preferences): Map<RemoteButton, String> = RemoteButton.entries.associateWith { button ->
        prefs.secret(secretKey(button))?.takeIf { it.isEmpty() || ShellSafety.isPackage(it) } ?: button.defaultApp
    }

    /** [pkg]: a package, "" for nothing, null to go back to the button's own app. */
    suspend fun setMapping(prefs: Preferences, button: RemoteButton, pkg: String?) {
        require(pkg == null || pkg.isEmpty() || ShellSafety.isPackage(pkg)) { "not a package: $pkg" }
        prefs.setSecret(secretKey(button), pkg)
    }
}
