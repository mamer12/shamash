package io.shamash.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.shamash.MainActivity
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
    /** The screen's handler for internal routes, while it is shown (main thread only); true when it took the route. */
    private var ui: ((String) -> Boolean)? = null

    /** A route MainActivity was started with before the screen was up. */
    private var pending: String? = null

    /** The screen is up: internal routes go to [handler] from now on (and one that waited goes now). */
    fun attach(handler: (String) -> Boolean) {
        ui = handler
        pending?.let { route -> pending = null; handler(route) }
    }

    fun detach(handler: (String) -> Boolean) {
        if (ui === handler) ui = null
    }

    /**
     * Goes to an internal route ("search?q=…", "settings"): on screen when Shamash is up, else Shamash starts with
     * it. False when it couldn't. SPEC: ARCH-11
     */
    fun internal(route: String): Boolean = ui?.invoke(route) ?: launchShamash(route)

    /** A route that came with MainActivity's intent: shown now, or as soon as the screen is up. */
    fun arrived(route: String) {
        val handler = ui
        if (handler != null) handler(route) else pending = route
    }

    /** The package of the last app opened (closed when the user is back Home, SPEC: HOME-15). */
    var lastOpened: String? = null
        private set

    suspend fun open(item: MediaItem): Boolean {
        val opened = when (val t = item.open) {
            is OpenTarget.Video -> start(view("https://www.youtube.com/watch?v=${t.youtubeId}", YOUTUBE)) || app(YOUTUBE)
            is OpenTarget.AppPage -> if (t.appPackage == context.packageName) internal(t.uri)
                else start(view(t.uri, t.appPackage)) || app(t.appPackage)
            is OpenTarget.Lookup -> lookup(t)
            is OpenTarget.App -> app(t.appPackage)
            is OpenTarget.AppScreen -> start(screen(t)) || app(t.appPackage)
            is OpenTarget.Internal -> internal(t.route)
        }
        val selfPage = (item.open as? OpenTarget.AppPage)?.appPackage == context.packageName
        if (opened && !selfPage && item.providerId !in OWN_PROGRESS && item.open !is OpenTarget.Internal) history.record(item)
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

    private fun screen(t: OpenTarget.AppScreen): Intent? {
        val intent = Intent().setClassName(t.appPackage, t.className)
        t.extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun view(uri: String, packageName: String): Intent? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Shamash itself, brought up with [route] (not recorded as the last app: it must never be closed). */
    private fun launchShamash(route: String): Boolean = try {
        context.startActivity(
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        Logger.w("open Shamash", e); false
    } catch (e: SecurityException) {
        Logger.w("open Shamash", e); false
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

        /** MainActivity's extra: an internal route to show ("search?q=…"). */
        const val EXTRA_ROUTE = "io.shamash.extra.ROUTE"

        /** Providers left out of history: links sent from the phone, which have no real title. */
        val OWN_PROGRESS = setOf("phone")
    }
}
