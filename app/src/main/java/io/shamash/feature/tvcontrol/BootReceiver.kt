package io.shamash.feature.tvcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After the TV boots (or Shamash is updated, which turns its accessibility service off): once the system settles,
 * check that Shamash mode is still whole (home, screensaver, apps off, remote buttons) and quietly re-apply it.
 * The job does nothing in Original mode. SPEC: TVC-02, TVC-07
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        // A way back the watchdog asked for and never finished comes first; the check then leaves Shamash mode alone.
        if (Watchdog.restorePending(context)) Watchdog.resumePendingRestore(context, SETTLE_MS)
        else TvControlJob.schedule(context, TvControlJob.Action.VERIFY, SETTLE_MS)
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
        const val SETTLE_MS = 45_000L
    }
}
