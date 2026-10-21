package io.shamash.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.Moments
import io.shamash.feature.profiles.ProfileChip
import io.shamash.ui.BillboardBackdrop
import io.shamash.ui.Motion
import io.shamash.ui.ProviderPage
import androidx.compose.ui.focus.FocusRequester
import io.shamash.ui.PreviewController
import io.shamash.ui.PreviewFullScreen
import io.shamash.ui.TopActions
import io.shamash.ui.TopBar
import io.shamash.ui.rememberBillboardState

private const val TAB_HOME = 0
private const val TAB_DISCOVER = 1

/** How dark the picture gets once the rows are up, or on the other tabs. */
private const val AWAY_DIM = 0.78f

/**
 * The home screen: the billboard behind everything, the top bar, and the Home, Discover and Apps tabs. Back goes up to
 * the tabs, then to Home, then to the apps row. SPEC: HOME-01..05, HOME-09, HOME-12
 */
@Composable
fun HomeScreen(locator: ServiceLocator) {
    val context = LocalContext.current
    val model = remember(locator) { HomeModel.get(locator) }
    val ui by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val profile by locator.prefs.profile.collectAsState(initial = null)
    val pages = locator.providers.filterIsInstance<ProviderPage>().filter { page -> profile?.let(page::available) == true }
    val pageFocus = remember(pages) { pages.map { FocusRequester() } }
    val previews = remember(model) { PreviewController(context.applicationContext, model::preview) }
    val actions = remember(locator, previews) { HomeActions(locator, context, scope, previews) }
    LifecycleStartEffect(model) {
        model.resume()
        onStopOrDispose {
            model.pause()
            previews.stop(null)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var inRows by remember { mutableStateOf(false) }
    var tabsFocused by remember { mutableStateOf(false) }
    var inputsOpen by remember { mutableStateOf(false) }
    val focus = remember { HomeFocus() }
    val billboard = rememberBillboardState(ui.featured)
    val away = animateFloatAsState(if (tab != TAB_HOME || inRows) 1f else 0f, Motion.pace(), label = "away")
    val night = Moments.nightDim(ui.moment)

    BackHandler(enabled = !inputsOpen) {
        when {
            !tabsFocused -> focus.tabs[tab].tryFocus()
            tab != TAB_HOME -> { tab = TAB_HOME; focus.tabs[TAB_HOME].tryFocus() }
            else -> focus.apps.tryFocus()
        }
    }
    FirstFocus(ui, focus)

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { previews.onKey(down = it.type == KeyEventType.KeyDown) }) {
        BillboardBackdrop(billboard, dim = { 1f - (1f - AWAY_DIM * away.value) * (1f - night) })
        when (tab) {
            TAB_HOME -> HomeTab(
                ui, billboard, away, focus, actions, previews,
                active = !inRows && !inputsOpen, onRowsFocused = { inRows = it },
            )
            TAB_DISCOVER -> DiscoverTab(ui, actions, previews)
            else -> AppsTab(ui, actions)
        }
        TopBar(
            tabs = listOf(stringResource(R.string.tab_home), stringResource(R.string.tab_discover), stringResource(R.string.tab_apps)) + pages.map { stringResource(it.titleResource) },
            selected = tab,
            onSelect = { index ->
                if (index < focus.tabs.size) tab = index else locator.opener.internal(pages[index - focus.tabs.size].entryUri)
            },
            tabRequesters = focus.tabs + pageFocus,
            actions = remember(actions) { TopActions(actions::search, { if (actions.canUseInputs) inputsOpen = true }, actions::tvSettings) },
            leading = { ProfileChip(locator.session) },
            weather = ui.weather,
            city = ui.city,
            onTabsFocused = { tabsFocused = it },
        )
        if (inputsOpen) InputsPanel(actions, onClose = { inputsOpen = false; focus.tabs[tab].tryFocus() })
        PreviewFullScreen(previews)
    }
}

/** Focus starts on the billboard's button once there is one (a refresh never steals it back). */
@Composable
private fun FirstFocus(ui: HomeUi, focus: HomeFocus) {
    var placed by remember { mutableStateOf(false) }
    val hasButton = ui.featured.isNotEmpty() || ui.ready
    LaunchedEffect(hasButton) {
        if (placed || !hasButton) return@LaunchedEffect
        withFrameNanos { }
        placed = focus.button.tryFocus() || focus.apps.tryFocus()
    }
}
