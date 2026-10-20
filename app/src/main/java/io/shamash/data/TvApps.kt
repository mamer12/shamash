package io.shamash.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import io.shamash.domain.AppsSource
import io.shamash.domain.TvApp

/** Streaming services Shamash knows, in the order they're offered (package → name). */
val KNOWN_STREAMING = linkedMapOf(
    "com.netflix.ninja" to "Netflix",
    "com.google.android.youtube.tv" to "YouTube",
    "com.apple.atve.androidtv.appletv" to "Apple TV",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "com.disney.disneyplus" to "Disney+",
    "com.wbd.stream" to "Max",
    "com.todtv.tod" to "TOD",
    "com.osn.go" to "OSN+",
    "net.mbc.shahidTV" to "Shahid",
    "com.shabakaty.cbox.cinemana" to "Cinemana",
    "com.spotify.tv.android" to "Spotify",
)

/** The TV's launchable apps (known streaming first), their banners and the ones used lately. */
class TvApps(private val context: Context) : AppsSource {
    private val pm get() = context.packageManager

    override fun installed(): List<TvApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val order = KNOWN_STREAMING.keys.toList()
        return pm.queryIntentActivities(query, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { TvApp(it.packageName, KNOWN_STREAMING[it.packageName] ?: it.loadLabel(pm).toString(), it.packageName in KNOWN_STREAMING) }
            .sortedWith(compareBy({ !it.streaming }, { order.indexOf(it.packageName) }, { it.label.lowercase() }))
    }

    /** Used in the last two weeks, newest first (needs the usage app-op, granted during the switch). */
    override fun recent(limit: Int): List<TvApp> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching { usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 14 * DAY, now) }
            .getOrNull().orEmpty()
        val last = stats.filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, v) -> v.maxOf { it.lastTimeUsed } }
        val byPackage = installed().associateBy { it.packageName }
        return last.entries.sortedByDescending { it.value }.mapNotNull { byPackage[it.key] }.take(limit)
    }

    fun banner(packageName: String): Drawable? = runCatching {
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        val info = launch?.component?.let { pm.getActivityInfo(it, 0) }
        info?.loadBanner(pm) ?: pm.getApplicationBanner(packageName) ?: pm.getApplicationIcon(packageName)
    }.getOrNull()

    fun launchIntent(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)

    fun isInstalled(packageName: String) = launchIntent(packageName) != null

    private companion object { const val DAY = 86_400_000L }
}
