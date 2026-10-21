package io.shamash.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.Greeting
import io.shamash.domain.Moment
import io.shamash.domain.Shelf
import io.shamash.ui.AppsRow
import io.shamash.ui.BillboardInfo
import io.shamash.ui.BillboardState
import io.shamash.ui.Dimens
import io.shamash.ui.EmptyBillboard
import io.shamash.ui.MediaRow
import io.shamash.ui.PreviewController
import io.shamash.ui.Type
import io.shamash.ui.rememberAlignStart

/** The apps row's height: banners, the room their focus growth needs, the gap below. */
private val APPS_ROW_H = 120.dp

/** Where a focused card's top sits in the rows list (its row title above it). */
private val ROW_ANCHOR = 40.dp

/**
 * The Home tab: greeting and billboard words over the photo, the apps row fixed low on the screen, and the rows
 * under it. Moving down into the rows slides everything up under the top bar (the photo dims); moving back up to the
 * apps row brings it back. SPEC: HOME-02..04, HOME-07
 */
@Composable
fun HomeTab(
    ui: HomeUi,
    billboard: BillboardState,
    away: State<Float>,
    focus: HomeFocus,
    actions: HomeActions,
    previews: PreviewController,
    active: Boolean,
    onRowsFocused: (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowsHeight = maxHeight - Dimens.TopBarHeight
        val shift = with(LocalDensity.current) { (Dimens.AppsTop + APPS_ROW_H - Dimens.TopBarHeight).toPx() }
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -away.value * shift },
        ) {
            Hero(ui, billboard, focus, actions, active, Modifier.graphicsLayer { alpha = 1f - away.value })
            AppsRow(ui.appsRow, actions::openApp, Modifier.height(APPS_ROW_H).focusRequester(focus.apps))
            RowsList(
                ui.home, actions, previews, rowsHeight,
                Modifier.onFocusChanged { onRowsFocused(it.hasFocus) },
            )
        }
    }
}

@Composable
private fun Hero(
    ui: HomeUi,
    billboard: BillboardState,
    focus: HomeFocus,
    actions: HomeActions,
    active: Boolean,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().height(Dimens.AppsTop)) {
        Text(
            greetingText(ui.greeting), style = Type.Greeting, maxLines = 1,
            modifier = Modifier.padding(start = Dimens.Margin, end = Dimens.Margin, top = 92.dp),
        )
        Box(Modifier.align(Alignment.BottomStart).padding(start = Dimens.Margin, bottom = 20.dp)) {
            when {
                ui.featured.isNotEmpty() -> BillboardInfo(billboard, active && ui.autoplay, actions::openFeatured, focus.button)
                ui.ready -> EmptyBillboard(actions::settings, focus.button)
            }
        }
    }
}

/**
 * A lazy list of rows; the focused row's cards sit just under the top. Menu on a row opens the row editor: a long OK
 * press there is already the card menu (PER-07), and row titles take no focus. SPEC: LAY-02
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowsList(
    rows: List<Shelf>,
    actions: HomeActions,
    previews: PreviewController?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAlignStart(ROW_ANCHOR)) {
        LazyColumn(
            modifier.fillMaxWidth().height(height).onKeyEvent { e ->
                if (e.key != Key.Menu) return@onKeyEvent false
                if (e.type == KeyEventType.KeyUp) actions.editRows()
                true
            },
            contentPadding = PaddingValues(top = 8.dp, bottom = height / 2),
        ) {
            items(rows, key = { it.id }) { shelf -> MediaRow(shelf, actions::open, previews = previews) }
        }
    }
}

/** "Good evening, Sara" (no name, no comma). SPEC: HOME-07 */
@Composable
private fun greetingText(g: Greeting): String {
    val name = g.name
    return when (g.moment) {
        Moment.MORNING -> if (name == null) stringResource(R.string.greeting_morning) else stringResource(R.string.greeting_morning_name, name)
        Moment.AFTERNOON -> if (name == null) stringResource(R.string.greeting_afternoon) else stringResource(R.string.greeting_afternoon_name, name)
        else -> if (name == null) stringResource(R.string.greeting_evening) else stringResource(R.string.greeting_evening_name, name)
    }
}
