package io.shamash.feature.matchcentre

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.data.espn.Polling
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the score overlay as a foreground service from 12 min before the user's match until the final score has been
 * shown, then sets the alarm for the next match and stops. SPEC: MC-04..06
 */
class MatchWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private lateinit var overlay: ScoreOverlay

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = ScoreOverlay(this)
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loop?.isActive != true) loop = scope.launch { follow() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        super.onDestroy()
    }

    private suspend fun follow() {
        val locator = ServiceLocator.get(this)
        val alerts = AlertStore(locator.prefs)
        var profile = locator.prefs.current()
        var settings = AlertSettings()
        val machine = WatchMachine { MatchAlerts.priority(it, profile, settings) }
        var matches = emptyList<Match>()
        var fetchedAt = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - fetchedAt >= fetchEvery(matches)) {
                profile = locator.prefs.current()
                settings = alerts.load()
                matches = fetch(locator) ?: matches
                fetchedAt = now
            }
            val eligible = MatchAlerts.eligible(matches, profile, settings)
            val wait = when (val step = machine.next(now, eligible)) {
                is WatchStep.Show -> step.recheckInMs.also {
                    if (MatchAlerts.shows(step.card, step.match, profile, settings, now)) overlay.show(step.match, step.card, now)
                    else overlay.hide()     // state keeps updating, nothing shows (MC-12)
                }
                is WatchStep.Hide -> step.recheckInMs.also { overlay.hide() }
                WatchStep.Stop -> break
            }
            delay(wait.coerceIn(MIN_WAIT_MS, WatchMachine.TICK_MS))
        }
        overlay.hide()
        MatchWatch.arm(this, MatchAlerts.eligible(matches, profile, settings))
        stopSelf()
    }

    /** A minute, half of it while an F1 session is on (MC-14). */
    private fun fetchEvery(matches: List<Match>): Long =
        matches.filter { it.mine && it.state == MatchState.LIVE }.minOfOrNull { Polling.live(it.sport) } ?: WatchMachine.TICK_MS

    /** The latest matches, or null when they couldn't be read (the last ones are kept). */
    private suspend fun fetch(locator: ServiceLocator): List<Match>? = withContext(Dispatchers.IO) {
        try {
            locator.matches.matches(locator.prefs.current())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("match watch", e)
            null
        }
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.match_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.match_following))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "match_centre"
        const val NOTIFICATION_ID = 7
        const val MIN_WAIT_MS = 1_000L
    }
}
