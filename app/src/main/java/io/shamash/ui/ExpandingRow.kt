package io.shamash.ui

import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.domain.MediaItem
import java.util.Locale
import kotlin.math.roundToInt

/** Opens an item; `atMs` is where its preview was when OK was pressed (null without one). */
typealias OpenAt = (item: MediaItem, atMs: Long?) -> Unit

private const val WIDE_FROM = 1.6f          // wide art takes over at ~60% of the growth
private const val LABEL_GONE = 2.5f         // the name label is gone at 40% of the growth
private const val LEAVING = 0.6f            // cards leaving on the left fade over 60% of a step
private const val PREVIEW_AFTER_MS = 950L
private const val WIDE_FADE_MS = 220
private const val PICTURE_FADE_MS = 400
private const val OVERSCAN = 1.04f

/**
 * A poster row whose focused poster grows into a 16:9 tile, the cards after it sliding right, its info under it
 * (rows below move with the row's actual height). Each card has its own 0.34 s spring. SPEC: HOME-16..19
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ExpandingRow(
    items: List<MediaItem>,
    ranked: Boolean,
    onOpen: OpenAt,
    previews: PreviewController?,
    modifier: Modifier = Modifier,
    onFocused: (MediaItem) -> Unit = {},
) {
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val d = LocalCardDims.current
    val step = with(density) { (d.posterW + Dimens.CardGap + if (ranked) d.rankW else 0.dp).toPx() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAlignStart(Dimens.Margin)) {
        LazyRow(
            state = state,
            modifier = modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = Dimens.Margin),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                ExpandingCard(
                    item = item,
                    rank = if (ranked) index + 1 else null,
                    fade = { leavingAlpha(state, index, step) },
                    onOpen = onOpen,
                    previews = previews,
                    onFocused = onFocused,
                )
            }
        }
    }
}

/** HOME-17: alpha = clamp(1 − (margin − x) / (0.6 × step)), x relative to the margin. */
private fun leavingAlpha(state: LazyListState, index: Int, step: Float): Float {
    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 1f
    return (1f + info.offset / (LEAVING * step)).coerceIn(0f, 1f)
}

@Composable
private fun ExpandingCard(
    item: MediaItem,
    rank: Int?,
    fade: () -> Float,
    onOpen: OpenAt,
    previews: PreviewController?,
    onFocused: (MediaItem) -> Unit,
) {
    val d = LocalCardDims.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow = animateFloatAsState(if (focused) 1f else 0f, Motion.pace(), label = "grow")
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) { opened = true; onFocused(item) } }
    val playing = previews != null && previews.renderingId == item.id
    val picture = animateFloatAsState(if (playing) 0f else 1f, tween(PICTURE_FADE_MS), label = "picture")

    Row(Modifier.graphicsLayer { alpha = fade() }) {
        if (rank != null) RankNumber(rank, d)
        Column {
            Box(
                Modifier
                    .tileSize(grow, d)
                    .tileOutline(focused)
                    .clip(RoundedCornerShape(Dimens.Radius))
                    .background(Palette.Card)
                    .clickable(interactionSource = interaction, indication = null) {
                        onOpen(item, previews?.positionMs(item.id))
                    },
            ) {
                if (previews != null && focused) PreviewSlot(item, previews)
                TileArt(item, grow, picture, opened, d)
                ProgressBar(item.progress)
            }
            TileText(item, grow, opened, d)
        }
    }
}

/** The poster center-cropped to the tile's current shape, giving way to the wide art as it grows. SPEC: HOME-16 */
@Composable
private fun TileArt(item: MediaItem, grow: State<Float>, picture: State<Float>, opened: Boolean, d: CardDims) {
    val poster = item.posterUrl ?: item.backdropUrl
    Picture(
        poster, d.posterW, d.posterH,
        Modifier.fillMaxSize().graphicsLayer { alpha = picture.value },
    )
    val wide = item.backdropUrl
    if (!opened || wide == null || wide == poster) return
    var loaded by remember(wide) { mutableStateOf(false) }
    val arrived = animateFloatAsState(if (loaded) 1f else 0f, tween(WIDE_FADE_MS), label = "wide")
    Picture(
        wide, d.tileW, d.tileH,
        Modifier.fillMaxSize().graphicsLayer {
            alpha = (grow.value * WIDE_FROM).coerceIn(0f, 1f) * arrived.value * picture.value
        },
        fade = false,
        onLoaded = { loaded = true },
    )
}

/** Under the card: its name (fading as it grows), then the tile's info (name, facts, story). SPEC: HOME-16, HOME-19 */
@Composable
private fun TileText(item: MediaItem, grow: State<Float>, opened: Boolean, d: CardDims) {
    Box(Modifier.infoArea(grow, d).clipToBounds()) {
        Text(
            item.title, style = Type.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(d.posterW)
                .padding(top = 8.dp)
                .graphicsLayer { alpha = 1f - (grow.value * LABEL_GONE).coerceIn(0f, 1f) },
        )
        if (opened) {
            Column(Modifier.width(d.tileW).padding(top = 12.dp).graphicsLayer { alpha = grow.value }) {
                Text(item.title, style = Type.TileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotEmpty()) {
                    Text(item.subtitle, style = Type.HeroMeta, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
                if (item.overview.isNotEmpty()) {
                    Text(
                        item.overview, style = Type.CardSub, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** After the focus rests, the provider's preview plays here, scaled for overscan. SPEC: HOME-20 */
@Composable
private fun PreviewSlot(item: MediaItem, previews: PreviewController) {
    LaunchedEffect(item.id) {
        kotlinx.coroutines.delay(PREVIEW_AFTER_MS)
        previews.start(item)
    }
    DisposableEffect(item.id) { onDispose { previews.stop(item.id) } }
    if (previews.playingId == item.id && !previews.fullScreen) {
        PreviewSurface(previews, Modifier.fillMaxSize().graphicsLayer { scaleX = OVERSCAN; scaleY = OVERSCAN })
    }
}

/** A Top 10 rank: a big outlined number left of the poster. */
@Composable
private fun RankNumber(rank: Int, d: CardDims) {
    val density = LocalDensity.current
    val outline = remember(density) { Type.Rank.copy(drawStyle = Stroke(with(density) { 2.dp.toPx() })) }
    Box(Modifier.width(d.rankW).height(d.posterH)) {
        Text(
            String.format(Locale.getDefault(), "%d", rank),
            style = outline, color = Palette.Dim, maxLines = 1, softWrap = false,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 4.dp),
        )
    }
}

/** The card's size between poster (0) and tile (1), read while laying out: growth never recomposes. */
private fun Modifier.tileSize(grow: State<Float>, d: CardDims) = layout { measurable, _ ->
    val g = grow.value
    val w = lerp(d.posterW.toPx(), d.tileW.toPx(), g).roundToInt()
    val h = lerp(d.posterH.toPx(), d.tileH.toPx(), g).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

/** The room under the card: the label's when small, the tile's info when grown. SPEC: HOME-18 */
private fun Modifier.infoArea(grow: State<Float>, d: CardDims) = layout { measurable, _ ->
    val g = grow.value
    val w = lerp(d.posterW.toPx(), d.tileW.toPx(), g).roundToInt()
    val h = lerp(d.labelRoom.toPx(), d.tileInfo.toPx(), g).roundToInt()
    val placeable = measurable.measure(Constraints(maxWidth = d.tileW.roundToPx(), maxHeight = d.tileInfo.roundToPx()))
    layout(w, h) { placeable.place(0, 0) }
}

/** The focused tile's white 4 px outline, just outside it. SPEC: HOME-16 */
private fun Modifier.tileOutline(focused: Boolean) = drawWithContent {
    drawContent()
    if (!focused) return@drawWithContent
    val gap = 2.5.dp.toPx()
    val width = 2.dp.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-gap, -gap),
        size = Size(size.width + 2 * gap, size.height + 2 * gap),
        cornerRadius = CornerRadius(Dimens.Radius.toPx() + gap),
        style = Stroke(width),
    )
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
