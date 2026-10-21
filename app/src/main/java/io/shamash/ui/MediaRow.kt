package io.shamash.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import io.shamash.domain.MediaItem
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TvApp

/** A row's title over its cards, at the margin. */
@Composable
fun RowFrame(title: String, modifier: Modifier = Modifier, reason: String? = null, content: @Composable () -> Unit) {
    Column(modifier.padding(bottom = Dimens.RowGap)) {
        if (title.isNotEmpty()) {
            Text(
                title, style = Type.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Dimens.Margin, end = Dimens.Margin, bottom = Dimens.RowTitleGap)
                    .semantics { heading() },
            )
        }
        content()
    }
}

/**
 * One shelf as a row, its cards drawn by its style: WIDE and VIDEO 16:9, MATCH cards, APPS banners, POSTER and
 * RANKED as expanding tiles. Lazy, keyed by item id; the focused card stays at the margin.
 */
@Composable
fun MediaRow(
    shelf: Shelf,
    onOpen: OpenAt,
    modifier: Modifier = Modifier,
    previews: PreviewController? = null,
) {
    RowFrame(shelf.title ?: shelfTitle(shelf.titleKey, shelf.titleArg), modifier, reasonLine(shelf.reason)) {
        when (shelf.style) {
            ShelfStyle.POSTER, ShelfStyle.RANKED ->
                ExpandingRow(shelf.items, shelf.style == ShelfStyle.RANKED, onOpen, previews)
            else -> CardRow { items(shelf.items, key = { it.id }) { item -> StyledCard(shelf.style, item, onOpen) } }
        }
    }
}

@Composable
private fun StyledCard(style: ShelfStyle, item: MediaItem, onOpen: OpenAt) {
    val open = { onOpen(item, null) }
    when {
        item.match != null -> MatchCard(item, open)
        style == ShelfStyle.VIDEO -> VideoCard(item, open)
        style == ShelfStyle.APPS && item.appPackage != null -> AppTile(item.appPackage, open, label = item.title)
        else -> WideCard(item, open)
    }
}

/** The user's apps as banner tiles. */
@Composable
fun AppsRow(apps: List<TvApp>, onOpen: (TvApp) -> Unit, modifier: Modifier = Modifier, title: String = "") {
    RowFrame(title, modifier) {
        CardRow { items(apps, key = { it.packageName }) { app -> AppTile(app.packageName, { onOpen(app) }, label = app.label) } }
    }
}

/** A lazy row at the margin that remembers its focused card and keeps it at the margin. SPEC: HOME-17 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CardRow(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAlignStart(Dimens.Margin)) {
        LazyRow(
            modifier = modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = Dimens.Margin, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            content = content,
        )
    }
}
