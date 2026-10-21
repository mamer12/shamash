package io.shamash.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.ui.Dimens
import io.shamash.ui.FocusCard
import io.shamash.ui.Palette
import io.shamash.ui.Picture
import io.shamash.ui.Type
import io.shamash.ui.VideoCard
import io.shamash.ui.WideCard

private val WIDE_KINDS = setOf(Kind.VIDEO, Kind.LIVE, Kind.MATCH)

/** A row of results under its app's name; the first card of the first row can take the focus. SPEC: SRCH-01 */
@Composable
internal fun ResultRow(
    title: String,
    row: SearchRow,
    firstCardFocus: FocusRequester?,
    onOpen: (MediaItem) -> Unit,
) {
    val wide = row.items.count { it.kind in WIDE_KINDS || it.posterUrl == null } * 2 > row.items.size
    Column(Modifier.padding(bottom = Dimens.RowGap)) {
        Text(title, style = Type.RowTitle)
        Spacer(Modifier.height(Dimens.RowTitleGap))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            contentPadding = PaddingValues(vertical = Dimens.CardGap / 2),
        ) {
            itemsIndexed(row.items) { index, item ->
                val focus = if (index == 0 && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier
                ResultCard(item, wide, focus) { onOpen(item) }
            }
        }
    }
}

@Composable
private fun ResultCard(item: MediaItem, wide: Boolean, modifier: Modifier, onClick: () -> Unit) = when {
    !wide -> PosterCard(item, onClick, modifier)
    item.kind == Kind.VIDEO -> VideoCard(item, onClick, modifier)
    else -> WideCard(item, onClick, modifier)
}

/** A 2:3 poster with the title under it (movies and series). */
@Composable
private fun PosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier) {
    Column(Modifier.width(Dimens.PosterW)) {
        FocusCard(onClick, modifier.size(Dimens.PosterW, Dimens.PosterH)) {
            Box(Modifier.fillMaxSize().background(Palette.Card))
            Picture(item.posterUrl ?: item.backdropUrl, Dimens.PosterW, Dimens.PosterH, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(Dimens.RowTitleGap / 2))
        Text(item.title, style = Type.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotEmpty()) {
            Text(item.subtitle, style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
