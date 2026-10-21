package io.shamash.feature.matchcentre

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.shamash.data.Logger
import io.shamash.domain.Match
import io.shamash.domain.MatchState

/**
 * Wakes the score overlay for the user's next match: an alarm 12 min before kick-off, or the overlay at once when the
 * match is live or about to start. Call it whenever fresh matches arrive. Needs "display over other apps". SPEC: MC-06
 */
object MatchWatch {
    private const val INEXACT_LEAD_MS = 5 * 60_000L     // an inexact alarm may come late: ask a little earlier

    fun schedule(context: Context, matches: List<Match>) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val wake = wakeIntent(app)
        alarms.cancel(wake)
        if (!Settings.canDrawOverlays(app)) return
        val now = System.currentTimeMillis()
        val next = WatchMachine.nextToWatch(matches, now) ?: return
        val at = next.startsAt - WatchMachine.WAKE_BEFORE_MS
        if (next.state == MatchState.LIVE || at <= now) start(app) else setAlarm(alarms, at, wake)
    }

    internal fun start(context: Context) {
        try {
            context.startForegroundService(Intent(context, MatchWatchService::class.java))
        } catch (e: IllegalStateException) {     // not allowed from the background right now: the alarm comes back
            Logger.w("match watch", e)
        } catch (e: SecurityException) {
            Logger.w("match watch", e)
        }
    }

    private fun setAlarm(alarms: AlarmManager, at: Long, wake: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
                return
            }
        } catch (e: SecurityException) {
            Logger.w("exact alarm", e)
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - INEXACT_LEAD_MS, wake)
    }

    private fun wakeIntent(context: Context): PendingIntent = PendingIntent.getForegroundService(
        context,
        0,
        Intent(context, MatchWatchService::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
