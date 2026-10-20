package io.shamash.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.shamash.data.Logger
import io.shamash.data.Preferences
import io.shamash.data.TvApps
import io.shamash.domain.LinkResolver
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.WatchHistory

/**
 * Opens anything: the title's own page when its app takes one, else the app. Remembers what was opened for Continue
 * watching (providers that keep their own progress say so with `providerId` in [OWN_PROGRESS]). SPEC: ARCH-12
 */
class Opener(
    private val context: Context,
    private val apps: TvApps,
    private val links: LinkResolver,
    private val history: WatchHistory,
    private val prefs: Preferences,
) {
    /** Where an [OpenTarget.Internal] goes (set by the activity: search, settings…). */
    var internal: (String) -> Unit = {}

    /** The package of the last app opened (closed when the user is back Home, SPEC: HOME-15). */
    var lastOpened: String? = null
        private set

    suspend fun open(item: MediaItem): Boolean {
        val opened = when (val t = item.open) {
            is OpenTarget.Video -> start(view("https://www.youtube.com/watch?v=${t.youtubeId}", YOUTUBE)) || app(YOUTUBE)
            is OpenTarget.AppPage -> start(view(t.uri, t.appPackage)) || app(t.appPackage)
            is OpenTarget.Lookup -> lookup(t)
            is OpenTarget.App -> app(t.appPackage)
            is OpenTarget.Internal -> { internal(t.route); true }
        }
        if (opened && item.providerId !in OWN_PROGRESS && item.open !is OpenTarget.Internal) history.record(item)
        return opened
    }

    fun openApp(packageName: String): Boolean = app(packageName)

    private suspend fun lookup(t: OpenTarget.Lookup): Boolean {
        val page = links.resolve(t, prefs.current().region)
        return (page != null && start(view(page, t.appPackage))) || app(t.appPackage)
    }

    private fun app(packageName: String): Boolean {
        val launch = apps.launchIntent(packageName) ?: return false
        return start(launch.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)).also { if (it) lastOpened = packageName }
    }

    private fun view(uri: String, packageName: String): Intent? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun start(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lastOpened = intent.`package` ?: intent.component?.packageName
            true
        } catch (e: ActivityNotFoundException) {
            Logger.w("open", e); false
        } catch (e: SecurityException) {
            Logger.w("open", e); false
        }
    }

    companion object {
        const val YOUTUBE = "com.google.android.youtube.tv"

        /** Providers whose app keeps its own progress (their Continue watching comes from them). */
        val OWN_PROGRESS = setOf("cinemana")
    }
}
