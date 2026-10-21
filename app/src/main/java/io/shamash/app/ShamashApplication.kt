package io.shamash.app

import android.app.Application
import io.shamash.data.softly
import io.shamash.feature.companion.Companion
import io.shamash.feature.matchcentre.MatchWatch
import io.shamash.feature.tvcontrol.Watchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process start: the crash watchdog first (so even a crash at start counts), then, once setup is done, the phone
 * companion and the next match reminder. Nothing here blocks the first frame. SPEC: TVC-05, CMP-01, MC-03
 */
class ShamashApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Watchdog.install(this)
        Watchdog.resumePendingRestore(this)             // a way back cut short (process death, reboot) goes on
        if (Watchdog.crashedTooOften(this)) return      // the restore job runs; don't race it with more work
        val locator = ServiceLocator.get(this)
        scope.launch {
            val profile = softly("profile", null) { locator.prefs.current() }
            if (profile?.onboarded != true) return@launch // the companion waits for consent (ShamashApp starts it)
            Companion.start(locator)
            softly("match reminders", Unit) {
                MatchWatch.schedule(this@ShamashApplication, locator.matches.matches(profile))
            }
        }
    }
}
