package io.shamash.feature.tvcontrol

import android.content.Context
import io.shamash.data.Logger
import java.io.File
import java.io.IOException

/**
 * The safety net: every crash of Shamash is counted, and the 3rd within 5 minutes asks for Original mode in the
 * background (a persisted job, so it runs in a fresh process after this one dies, even after a reboot, and is retried
 * until it works). Files: files/crashes.txt, and files/restore_pending while the way back hasn't happened yet (a
 * plain file: it is written from the crashing thread, where the suspend preference store isn't safe). SPEC: TVC-05
 */
object Watchdog {
    const val LIMIT = 3
    const val WINDOW_MS = 5 * 60_000L
    private const val FILE = "crashes.txt"
    private const val PENDING_FILE = "restore_pending"
    private const val MAX_FILE_BYTES = 4_096L

    @Volatile private var installed = false

    /** Call once per process, first thing (MainActivity/Application onCreate). */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            onCrash(app)
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * True right after the 3rd crash: the home screen should then hold off heavy work (show a plain screen), so a
     * crash loop doesn't race the way back.
     */
    fun crashedTooOften(context: Context): Boolean =
        recent(read(file(context)), System.currentTimeMillis()).size >= LIMIT

    /** A fresh start after the user switches to Shamash again. */
    fun clear(context: Context) {
        val f = file(context)
        if (f.exists() && !f.delete()) Logger.w("watchdog: crash count not cleared")
        restoreDone(context)
    }

    /** Whether the way back was asked for and hasn't happened yet (then the boot check must not re-apply Shamash). */
    fun restorePending(context: Context): Boolean = pendingFile(context).exists()

    fun restoreDone(context: Context) {
        val f = pendingFile(context)
        if (f.exists() && !f.delete()) Logger.w("watchdog: pending restore not cleared")
    }

    /** At process start or boot: a way back that never finished is asked for again. */
    fun resumePendingRestore(context: Context, delayMs: Long = 0) {
        try {
            if (restorePending(context)) TvControlJob.ensure(context, TvControlJob.Action.RESTORE, delayMs)
        } catch (e: RuntimeException) {
            Logger.w("watchdog: pending restore not resumed", e)
        }
    }

    /** The crash times still inside the window, with [now] added. Pure. */
    fun record(previous: List<Long>, now: Long): List<Long> = recent(previous, now) + now

    fun recent(times: List<Long>, now: Long): List<Long> = times.filter { it in (now - WINDOW_MS + 1)..now }

    fun tooMany(times: List<Long>) = times.size >= LIMIT

    private fun onCrash(app: Context) {
        try {
            val f = file(app)
            val times = record(read(f), System.currentTimeMillis())
            f.writeText(times.joinToString("\n"))
            if (tooMany(times)) {
                pendingFile(app).writeText(times.last().toString())
                TvControlJob.schedule(app, TvControlJob.Action.RESTORE, delayMs = 0)
            }
        } catch (e: Exception) {
            // Never let the safety net hide the real crash.
            Logger.w("watchdog failed", e)
        }
    }

    private fun read(f: File): List<Long> = try {
        if (!f.exists() || f.length() > MAX_FILE_BYTES) emptyList()
        else f.readLines().mapNotNull { it.trim().toLongOrNull() }
    } catch (e: IOException) {
        Logger.w("watchdog file unreadable", e)
        emptyList()
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun pendingFile(context: Context) = File(context.filesDir, PENDING_FILE)
}
