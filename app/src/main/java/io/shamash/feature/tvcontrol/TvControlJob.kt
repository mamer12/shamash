package io.shamash.feature.tvcontrol

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import io.shamash.data.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * TV control work that must outlive the moment that asked for it: the watchdog's restore (the asking process is
 * crashing) and the boot check (a receiver has only seconds). Jobs survive a reboot, need no network (the TV's ADB
 * is local), and one that fails is retried with growing waits. SPEC: TVC-05
 */
class TvControlJob : JobService() {
    enum class Action(val jobId: Int) { RESTORE(7101), VERIFY(7102) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val action = Action.entries.firstOrNull { it.name == params.extras.getString(EXTRA_ACTION) } ?: return false
        val control = TvControl.get(this)
        scope.launch {
            var retry = true
            try {
                val result = when (action) {
                    Action.RESTORE -> control.restoreAfterCrashes()
                    Action.VERIFY -> control.verify()
                }
                retry = TvControlRetry.shouldRetry(result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.w("TV control job $action failed", e)
            } finally {
                jobFinished(params, retry)
            }
        }
        return true
    }

    /** Stopped mid-way (the system needed the resources): try again later. */
    override fun onStopJob(params: JobParameters) = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val DEADLINE_SLACK_MS = 60_000L
        private const val BACKOFF_MS = 30_000L

        /** Schedules [action] unless it already is (scheduling again would stop one that is running). */
        fun ensure(context: Context, action: Action, delayMs: Long) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(action.jobId) == null) schedule(context, action, delayMs)
        }

        fun schedule(context: Context, action: Action, delayMs: Long) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(action.jobId, ComponentName(context, TvControlJob::class.java))
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + DEADLINE_SLACK_MS)
                .setPersisted(true) // RECEIVE_BOOT_COMPLETED is held
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(PersistableBundle().apply { putString(EXTRA_ACTION, action.name) })
                .build()
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) Logger.w("TV control job $action not scheduled")
        }
    }
}

/** Whether a TV control job should run again. Pure. SPEC: TVC-05 */
object TvControlRetry {
    /**
     * Failures and incomplete restores retry (ADB not up yet, a dropped connection, or packages still off). Done, or
     * a stop that needs the user (Wi-Fi owned by an app) is final.
     */
    fun shouldRetry(result: SwitchResult?): Boolean = when (result) {
        is SwitchResult.Failed -> true
        is SwitchResult.Restored -> !result.homeRestored || result.stillOff.isNotEmpty()
        else -> false
    }
}
