package io.shamash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.shamash.app.Opener
import io.shamash.app.ServiceLocator
import io.shamash.app.ShamashApp
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

    /** Hands the intent's route to the screen once (a recreated activity mustn't show it again). */
    private fun deliverRoute(intent: Intent?) {
        val route = intent?.getStringExtra(Opener.EXTRA_ROUTE) ?: return
        intent.removeExtra(Opener.EXTRA_ROUTE)
        ServiceLocator.get(this).opener.arrived(route)
    }
}
