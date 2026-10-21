package io.shamash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.shamash.MainActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.shamash.feature.companion.Companion
import io.shamash.feature.companion.PhoneOnTv
import io.shamash.feature.home.HomeScreen
import io.shamash.feature.onboarding.Onboarding
import io.shamash.feature.profiles.ProfileGate
import io.shamash.feature.profiles.Restrictions
import io.shamash.feature.search.SearchScreen
import io.shamash.feature.settings.SettingsScreen
import io.shamash.ui.ProviderPage
import androidx.compose.runtime.rememberUpdatedState
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/** The screens Shamash moves between. */
sealed interface Route {
    data object Home : Route
    data class Search(val query: String?) : Route
    data object Settings : Route
    data class Addon(val uri: String) : Route

    companion object {
        /** "home", "search?q=…", "settings"; anything else is ignored. */
        fun parse(route: String): Route? {
            val uri = Uri.parse(if (route.startsWith("shamash://")) route else "shamash://$route")
            return when (uri.host) {
                "home" -> Home
                "search" -> Search(uri.getQueryParameter("q")?.takeIf { it.isNotBlank() })
                "settings" -> Settings
                else -> null
            }
        }
    }
}

/**
 * Setup until the profile is onboarded, then Home with Search and Settings on top of it. Internal links
 * (`OpenTarget.Internal`, the phone companion) arrive through the Opener. The phone companion starts once setup is
 * done. SPEC: ONB-01, ARCH-11, CMP-01
 */
@Composable
fun ShamashApp(locator: ServiceLocator) {
    val profile by locator.prefs.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentProfile by rememberUpdatedState(profile)
    DisposableEffect(locator, lifecycle, profile != null) {
        val handler: (String) -> Boolean = { link ->
            val route = Route.parse(link)?.takeIf { allowedRoute(locator.session.restrictions, it) } ?: currentProfile?.let { p ->
                locator.providers.filterIsInstance<ProviderPage>()
                    .firstOrNull { it.available(p) && it.accepts(link) }?.let { Route.Addon(link) }
            }
            if (route != null) {
                stack = push(stack, route)
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) bringToFront(context)
            }
            route != null
        }
        if (profile != null) locator.opener.attach(handler)
        onDispose { locator.opener.detach(handler) }
    }
    val onboarded = profile?.onboarded == true
    LaunchedEffect(onboarded) { if (onboarded) Companion.start(locator) }
    val p = profile
    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        when {
            p == null -> Unit
            !p.onboarded -> Onboarding(p) { answers -> scope.launch { locator.prefs.save(answers) } }
            else -> ProfileGate(locator.session, onAddProfile = { stack = push(stack, Route.Settings) }) {
                val pop = { stack = stack.dropLast(1) }
                BackHandler(enabled = stack.size > 1, onBack = pop)
                when (val top = stack.last()) {
                    Route.Home -> HomeScreen(locator)
                    is Route.Search -> SearchScreen(locator, top.query, pop)
                    Route.Settings -> SettingsScreen(locator, pop)
                    is Route.Addon -> {
                        val page = locator.providers.filterIsInstance<ProviderPage>()
                            .firstOrNull { it.available(p) && it.accepts(top.uri) }
                        if (page != null) page.Content(top.uri, p, { stack = push(stack, Route.Settings) }, pop)
                        else LaunchedEffect(top) { pop() }
                    }
                }
            }
        }
        if (onboarded) PhoneOnTv.Prompts(locator)   // backup questions from the phone. SPEC: CMP-13, CMP-15
    }
}

/** Kids and Guest never reach Settings, by any route (a link, the phone, a tile). SPEC: PRF-10, PRF-11 */
private fun allowedRoute(restrictions: Restrictions, route: Route): Boolean = route != Route.Settings || restrictions.settings

/** A link from outside (the phone) while another app is in front: Shamash comes forward to show it. */
private fun bringToFront(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    try {
        context.startActivity(intent)
    } catch (e: RuntimeException) {
        Log.w("Shamash", "bring to front: $e")
    }
}

/** Home clears the stack; the same screen again replaces itself (a new search query). */
private fun push(stack: List<Route>, route: Route): List<Route> = when {
    route == Route.Home -> listOf(Route.Home)
    stack.last()::class == route::class -> stack.dropLast(1) + route
    else -> stack + route
}
