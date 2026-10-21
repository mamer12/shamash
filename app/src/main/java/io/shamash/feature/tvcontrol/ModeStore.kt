package io.shamash.feature.tvcontrol

import io.shamash.data.Preferences

enum class Mode { ORIGINAL, SHAMASH }

/**
 * What TV control remembers between switches: the mode, the TV's own home screen and screensaver (recorded before
 * Shamash replaced them; "" = none was set), and every app Shamash turned off. SPEC: TVC-02, TVC-05
 */
data class TvState(
    val mode: Mode = Mode.ORIGINAL,
    val originalHome: String? = null,
    val originalScreensaver: String? = null,
    val turnedOff: Set<String> = emptySet(),
    val accessibilityWasOff: Boolean = false,
    val watchdogRestoredAt: Long? = null,
)

interface ModeStore {
    suspend fun load(): TvState
    suspend fun save(state: TvState)
}

/** [TvState] in the profile store's secrets (on the TV only). */
class PreferencesModeStore(private val prefs: Preferences) : ModeStore {

    override suspend fun load() = TvState(
        mode = if (prefs.secret(MODE) == Mode.SHAMASH.name) Mode.SHAMASH else Mode.ORIGINAL,
        originalHome = prefs.secret(HOME)?.takeIf(ShellSafety::isComponent),
        originalScreensaver = prefs.secret(SCREENSAVER),
        turnedOff = prefs.secret(TURNED_OFF).orEmpty().split(',').filter(ShellSafety::isPackage).toSet(),
        accessibilityWasOff = prefs.secret(ACCESSIBILITY_WAS_OFF) == "1",
        watchdogRestoredAt = prefs.secret(WATCHDOG_RESTORED_AT)?.toLongOrNull(),
    )

    override suspend fun save(state: TvState) {
        prefs.setSecret(MODE, state.mode.name)
        prefs.setSecret(HOME, state.originalHome)
        prefs.setSecret(SCREENSAVER, state.originalScreensaver)
        prefs.setSecret(TURNED_OFF, state.turnedOff.sorted().joinToString(","))
        prefs.setSecret(ACCESSIBILITY_WAS_OFF, if (state.accessibilityWasOff) "1" else "0")
        prefs.setSecret(WATCHDOG_RESTORED_AT, state.watchdogRestoredAt?.toString())
    }

    private companion object {
        const val MODE = "tvc_mode"
        const val HOME = "tvc_original_home"
        const val SCREENSAVER = "tvc_original_screensaver"
        const val TURNED_OFF = "tvc_turned_off"
        const val ACCESSIBILITY_WAS_OFF = "tvc_accessibility_was_off"
        const val WATCHDOG_RESTORED_AT = "tvc_watchdog_restored_at"
    }
}
