package io.shamash.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.ActionTile
import io.shamash.ui.AppsRow
import io.shamash.ui.CardRow
import io.shamash.ui.Dimens
import io.shamash.ui.Icons
import io.shamash.ui.Palette
import io.shamash.ui.PreviewController
import io.shamash.ui.RowFrame
import io.shamash.ui.Type
import io.shamash.ui.rememberAlignStart

private const val APPS_PER_ROW = 5
private val ROW_ANCHOR = 40.dp

/** Discover: Top 10 and "Popular on" every service the TV has. SPEC: HOME-05 */
@Composable
fun DiscoverTab(ui: HomeUi, actions: HomeActions, previews: PreviewController) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowsHeight = maxHeight - Dimens.TopBarHeight - 12.dp
        Column {
            Spacer(Modifier.height(Dimens.TopBarHeight + 12.dp))
            if (ui.discover.isEmpty() && ui.ready) {
                Text(
                    stringResource(R.string.billboard_empty_body), style = Type.HeroBody,
                    modifier = Modifier.padding(horizontal = Dimens.Margin),
                )
            } else {
                RowsList(ui.discover, actions, previews, rowsHeight)
            }
        }
    }
}

/** Apps: recent apps, all apps, the TV's inputs, and settings. SPEC: HOME-05 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsTab(ui: HomeUi, actions: HomeActions) {
    val context = LocalContext.current
    val inputs by produceState(emptyList<TvInput>()) { value = TvInputs.list(context) }
    val allRows = remember(ui.allApps) { ui.allApps.chunked(APPS_PER_ROW) }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(Dimens.TopBarHeight + 12.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAlignStart(ROW_ANCHOR)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 240.dp)) {
                if (ui.recentApps.isNotEmpty()) {
                    item(key = "recent") { AppsRow(ui.recentApps, actions::openApp, title = stringResource(R.string.recent_apps)) }
                }
                allApps(allRows, actions)
                item(key = "inputs") { InputsRow(inputs, actions) }
                item(key = "settings") { SettingsRow(actions) }
            }
        }
    }
}

private fun LazyListScope.allApps(rows: List<List<io.shamash.domain.TvApp>>, actions: HomeActions) {
    itemsIndexed(rows, key = { i, _ -> "all:$i" }) { i, apps ->
        AppsRow(apps, actions::openApp, title = if (i == 0) stringResource(R.string.all_apps) else "")
    }
}

@Composable
private fun InputsRow(inputs: List<TvInput>, actions: HomeActions, first: FocusRequester? = null, onOpened: () -> Unit = {}) {
    RowFrame(stringResource(R.string.inputs)) {
        if (inputs.isEmpty()) {
            Text(stringResource(R.string.inputs_none), style = Type.HeroBody, modifier = Modifier.padding(horizontal = Dimens.Margin))
            return@RowFrame
        }
        CardRow {
            itemsIndexed(inputs, key = { _, it -> it.id }) { i, input ->
                val mod = if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier
                ActionTile(Icons.Input, input.label, { actions.openInput(input); onOpened() }, mod)
            }
        }
    }
}

@Composable
private fun SettingsRow(actions: HomeActions) {
    RowFrame(stringResource(R.string.settings)) {
        CardRow {
            item(key = "shamash") { ActionTile(Icons.Settings, stringResource(R.string.apps_settings_shamash), actions::settings) }
            item(key = "tv") { ActionTile(Icons.Tv, stringResource(R.string.apps_settings_tv), actions::tvSettings) }
            item(key = "phone") { ActionTile(Icons.Phone, stringResource(R.string.apps_settings_phone), actions::settings) }
            item(key = "youtube") { ActionTile(Icons.Account, stringResource(R.string.apps_settings_youtube), actions::settings) }
        }
    }
}

/** The Inputs button's panel: a row of the TV's inputs over the dimmed screen; Back closes it. SPEC: HOME-01 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InputsPanel(actions: HomeActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val inputs by produceState<List<TvInput>?>(null) { value = TvInputs.list(context) }
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(inputs) {
        if (inputs.isNullOrEmpty()) return@LaunchedEffect
        withFrameNanos { }
        first.tryFocus()
    }
    Box(Modifier.fillMaxSize().background(Palette.Night.copy(alpha = 0.88f))) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 72.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        ) {
            inputs?.let { InputsRow(it, actions, first, onClose) }
        }
    }
}
