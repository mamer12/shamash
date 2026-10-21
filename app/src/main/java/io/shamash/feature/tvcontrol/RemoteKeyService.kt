package io.shamash.feature.tvcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sees the remote's app buttons before any app does (so they work from inside Netflix and the rest) and opens the
 * app the user mapped. It only filters those keys: no window content, no text. On only in Shamash mode. SPEC: TVC-07
 */
class RemoteKeyService : AccessibilityService() {
    private val scope = MainScope()
    private val locator by lazy { ServiceLocator.get(this) }

    /** Read on the key path, so kept in memory and refreshed whenever the preferences change. */
    @Volatile private var apps: Map<RemoteButton, String> = RemoteButton.entries.associateWith { it.defaultApp }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            locator.prefs.profile.collect { apps = RemoteKeys.mapping(locator.prefs) }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val button = RemoteButton.of(event.keyCode) ?: return false
        if (!currentDevice().hasRemoteButtons || RemoteKeys.isFromController(event.source)) return false
        val pkg = apps[button].orEmpty()
        if (pkg.isEmpty()) return false // "Nothing": the key goes on as if Shamash weren't here
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) open(button, pkg)
        return true
    }

    private fun open(button: RemoteButton, pkg: String) {
        val intent = locator.apps.launchIntent(pkg)
        Logger.w("remote ${button.name} -> $pkg${if (intent == null) " (not installed)" else ""}")
        intent?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
