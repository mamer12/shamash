package io.shamash.feature.tvcontrol

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.data.Preferences
import io.shamash.feature.tvcontrol.adb.AdbClient
import io.shamash.feature.tvcontrol.adb.AdbKey
import io.shamash.feature.tvcontrol.adb.AdbShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * What the Settings screen calls to make Shamash the home screen and to go back. Results and progress are enums the
 * screen localizes. SPEC: TVC-02, TVC-05, TVC-07, TVC-08
 */
class TvControl private constructor(private val context: Context, private val prefs: Preferences) {
    private val store = PreferencesModeStore(prefs)
    private val switcher = Switcher(DeviceAdb(context.filesDir), store, PackageManagerAlias(context), currentDevice())

    suspend fun isShamashMode(): Boolean = store.load().mode == Mode.SHAMASH

    /** Makes Shamash the home screen. The TV may ask "Allow debugging?" once ([Phase.WAITING_FOR_APPROVAL]). */
    suspend fun switch(progress: (Step) -> Unit): SwitchResult =
        switcher.switchToShamash(progress).also { if (it is SwitchResult.Switched) Watchdog.clear(context) }

    /** Original mode: the TV's own home screen, screensaver and apps come back. */
    suspend fun restore(progress: (Step) -> Unit): SwitchResult = switcher.restoreOriginal(progress)

    /** Whether the TV's ADB is on and listening (network/USB debugging enabled in Developer options). */
    suspend fun adbAvailable(): Boolean = withContext(Dispatchers.IO) {
        val enabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        enabled && portOpen()
    }

    /** When the watchdog last went back to Original by itself (to tell the user once), or null. */
    suspend fun watchdogRestoredAt(): Long? = store.load().watchdogRestoredAt

    suspend fun clearWatchdogNotice() = store.save(store.load().copy(watchdogRestoredAt = null))

    /** Remote buttons can be mapped only on TVs whose keycodes Shamash knows (TCL). */
    val remoteButtonsSupported: Boolean get() = currentDevice().hasRemoteButtons

    /** Each app button and the package it opens ("" = nothing: the key works as before). */
    suspend fun remoteButtons(): Map<RemoteButton, String> = RemoteKeys.mapping(prefs)

    /** Maps [button] to [pkg] ("" = nothing; null = the button's own app). */
    suspend fun setRemoteButton(button: RemoteButton, pkg: String?) = RemoteKeys.setMapping(prefs, button, pkg)

    /**
     * After boot or an update: re-applies Shamash mode quietly (never waits for the TV's prompt). Null when there was
     * nothing to do: Original mode, or the watchdog's way back still pending (that one wins).
     */
    internal suspend fun verify(): SwitchResult? {
        if (!isShamashMode() || Watchdog.restorePending(context)) return null
        return switcher.switchToShamash(progress = {}, waitForApproval = false)
            .also { Logger.w("TV control boot check: $it") }
    }

    /** The watchdog's way back; partial restores keep retrying even after Original mode was recorded. */
    internal suspend fun restoreAfterCrashes(): SwitchResult? {
        if (!isShamashMode() && !Watchdog.restorePending(context)) {
            Watchdog.restoreDone(context)
            return null
        }
        val result = switcher.restoreOriginal(progress = {}, waitForApproval = false)
        Logger.w("TV control watchdog restore: $result")
        if (result is SwitchResult.Restored && !TvControlRetry.shouldRetry(result)) {
            store.save(store.load().copy(watchdogRestoredAt = System.currentTimeMillis()))
            Watchdog.restoreDone(context)
        }
        return result
    }

    private fun portOpen(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(AdbClient.HOST, AdbClient.PORT), PROBE_MS) }
        true
    } catch (e: IOException) {
        false
    }

    companion object {
        private const val PROBE_MS = 1_000

        @Volatile private var instance: TvControl? = null

        fun get(context: Context): TvControl = instance ?: synchronized(this) {
            instance ?: TvControl(context.applicationContext, ServiceLocator.get(context).prefs).also { instance = it }
        }
    }
}

internal fun currentDevice() = Device(BrandProfiles.brandOf(Build.MANUFACTURER, Build.BRAND), Build.VERSION.SDK_INT)

/** ADB sessions with the TV itself, keyed with Shamash's own key in [keyDir]. */
private class DeviceAdb(private val keyDir: File) : AdbConnector {
    override suspend fun <T> withShell(
        waitForApproval: Boolean,
        onApproval: () -> Unit,
        block: suspend (AdbShell) -> T,
    ): T = withContext(Dispatchers.IO) {
        AdbClient.connect(AdbKey.load(keyDir), waitForApproval, onApproval).use { block(it) }
    }
}

/** The manifest's HOME activity-alias, toggled in-app with DONT_KILL_APP (`pm enable` would kill Shamash). */
private class PackageManagerAlias(private val context: Context) : HomeAlias {
    override fun setEnabled(enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, TvPackages.HOME_ALIAS_CLASS), state, PackageManager.DONT_KILL_APP,
        )
        Logger.w("HOME alias ${if (enabled) "on" else "off"}")
    }
}
