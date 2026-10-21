package io.shamash.feature.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Back Home from an app: closes that app's process to free memory, unless the user keeps it running, and only while
 * Shamash is the home screen (as a plain app it leaves other apps alone). SPEC: HOME-15
 */
class AppCloser(private val context: Context) {
    fun closeIfAway(packageName: String?, keepRunning: Set<String>) {
        if (packageName == null || packageName == context.packageName || packageName in keepRunning) return
        if (!isHomeScreen()) return
        try {
            context.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(packageName)
        } catch (e: SecurityException) {
            Log.w("Shamash", "close $packageName: $e")
        }
    }

    private fun isHomeScreen(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
