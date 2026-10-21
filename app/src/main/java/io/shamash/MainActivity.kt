package io.shamash

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.shamash.app.Opener
import io.shamash.app.ServiceLocator
import io.shamash.app.ShamashApp
import io.shamash.feature.search.VoiceQuery
import io.shamash.ui.Images
import io.shamash.ui.ShamashTheme

/**
 * Shamash: the first-run setup until it's done, then the home screen. One instance (singleTask); a route sent while
 * no screen was up (a phone link) comes as [Opener.EXTRA_ROUTE]. SPEC: ARCH-11
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locator = ServiceLocator.get(this)
        Images.install(locator.http.client) { packageName -> locator.apps.banner(packageName) }
        setContent { ShamashTheme { ShamashApp(locator) } }
        if (savedInstanceState == null) deliverRoute(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverRoute(intent)
    }

    /** The launcher's voice search ("search Shamash for …") or a shamash://search?q= link. SPEC: PLT-08, PLT-09 */
    private fun searchRoute(intent: Intent): String? {
        val query = when (intent.action) {
            Intent.ACTION_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "shamash" && it.host == "search" }?.getQueryParameter("q")
            else -> null
        }
        return VoiceQuery.toQuery(query)?.let(VoiceQuery::route)
    }

    /** Hands the intent's route to the screen once (a recreated activity mustn't show it again). */
    private fun deliverRoute(intent: Intent?) {
        if (intent == null) return
        val route = searchRoute(intent) ?: intent.getStringExtra(Opener.EXTRA_ROUTE) ?: return
        intent.removeExtra(Opener.EXTRA_ROUTE)
        if (intent.action == Intent.ACTION_SEARCH || intent.action == Intent.ACTION_VIEW) intent.action = null   // not again
        ServiceLocator.get(this).opener.arrived(route)
    }
}
