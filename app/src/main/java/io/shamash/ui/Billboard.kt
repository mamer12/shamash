package io.shamash.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.MatchState
import io.shamash.feature.accessibility.A11y
import io.shamash.feature.accessibility.CardLabel
import io.shamash.domain.MediaItem
import kotlinx.coroutines.delay
import java.util.Locale

/** A billboard item and the name of the app it opens in ("Apple TV", "TOD"). */
data class Featured(val item: MediaItem, val appName: String)

private const val ADVANCE_MS = 9_000L
private const val TEXT_FADE_MS = 400
private const val MAX_DOTS = 16

/** Which billboard item shows; a refresh keeps the same item in place when it's still there. SPEC: HOME-02 */
@Stable
class BillboardState {
    var items by mutableStateOf<List<Featured>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    val current: Featured? get() = items.getOrNull(index)
    val next: Featured? get() = if (items.size < 2) null else items[(index + 1) % items.size]

    fun update(fresh: List<Featured>) {
        val showing = current?.item?.id
        items = fresh
        index = fresh.indexOfFirst { it.item.id == showing }.coerceAtLeast(0)
    }

    fun step(by: Int) {
        if (items.isNotEmpty()) index = Math.floorMod(index + by, items.size)
    }
}

@Composable
fun rememberBillboardState(items: List<Featured>): BillboardState {
    val state = remember { BillboardState() }
    LaunchedEffect(items) { state.update(items) }
    return state
}

/**
 * The billboard's words: provider in caps, title (≤ 2 lines), meta, overview (≤ 3 lines), "Open in <app>" and the
 * dots. Advances every 9 s while [active] (off for a layout without autoplay, LAY-07); ←/→ on the button changes
 * item. SPEC: HOME-02, MC-03
 */
@Composable
fun BillboardInfo(
    state: BillboardState,
    active: Boolean,
    onOpen: (MediaItem) -> Unit,
    buttonRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    val count = state.items.size
    LaunchedEffect(state.index, active, count) {
        if (!active || count < 2) return@LaunchedEffect
        delay(ADVANCE_MS)
        state.step(1)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.width(Dimens.HeroTextWidth)) {
        Crossfade(current, animationSpec = tween(A11y.ms(TEXT_FADE_MS)), label = "billboard-text") { HeroText(it) }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                io.shamash.domain.Carousel.noApp(current.item) -> R.string.match_no_app   // SPEC: MC-10
                current.item.match != null -> R.string.watch_on
                else -> R.string.open_in
            }
            HeroButton(
                text = stringResource(label, current.appName),
                description = CardLabel.join(   // PLT-13: "<n> of <m>"
                    "${stringResource(label, current.appName)}: ${current.item.title}",
                    CardLabel.position(state.index, count, stringResource(R.string.a11y_carousel_position)),
                ),
                onClick = { onOpen(current.item) },
                modifier = Modifier
                    .focusRequester(buttonRequester)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { state.step(if (rtl) 1 else -1); count > 1 }
                            Key.DirectionRight -> { state.step(if (rtl) -1 else 1); count > 1 }
                            else -> false
                        }
                    },
            )
            Spacer(Modifier.width(22.dp))
            Dots(count.coerceAtMost(MAX_DOTS), state.index)
        }
    }
}

/** No featured titles yet (no keys, no sign-in): what to do about it. */
@Composable
fun EmptyBillboard(onSettings: () -> Unit, buttonRequester: FocusRequester, modifier: Modifier = Modifier) {
    Column(modifier.width(Dimens.HeroTextWidth)) {
        Text(stringResource(R.string.billboard_empty_title), style = Type.HeroTitle)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.billboard_empty_body), style = Type.HeroBody, maxLines = 3)
        Spacer(Modifier.height(18.dp))
        HeroButton(stringResource(R.string.open_settings), onSettings, Modifier.focusRequester(buttonRequester))
    }
}

@Composable
private fun HeroText(featured: Featured) {
    val item = featured.item
    val match = item.match
    // PLT-12: solid ground behind the words when contrast is high
    val ground = if (A11y.highContrast) Modifier.background(Palette.Night) else Modifier
    Column(Modifier.fillMaxWidth().height(HERO_TEXT_H).then(ground), verticalArrangement = Arrangement.Bottom) {
        val live = match?.state == MatchState.LIVE
        val label = when {
            match == null -> featured.appName.uppercase(Locale.getDefault())
            live -> stringResource(R.string.billboard_live)
            else -> stringResource(R.string.billboard_match_day)
        }
        Text(label, style = Type.HeroLabel, color = if (live) Palette.Live else Palette.Gold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = if (match != null) stringResource(R.string.dot_join, match.competitionName, matchWhen(match))
        else item.subtitle.takeIf { it != featured.appName }.orEmpty()
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(meta, style = Type.HeroMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.overview.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(item.overview, style = Type.HeroBody, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val HERO_TEXT_H = 200.dp

@Composable
private fun matchWhen(match: io.shamash.domain.Match): String =
    if (match.state == MatchState.PRE) stringResource(R.string.dot_join, matchDay(match.startsAt), matchMiddle(match))
    else matchMiddle(match)

/** The billboard's pill button: gold when focused. */
@Composable
fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = text) {
    FocusCard(onClick, modifier, shape = RoundedCornerShape(50), corner = 24.dp, scale = 1.05f, description = description) { focused ->
        val ink = if (focused) Palette.Night else Palette.Ink
        Row(
            Modifier
                .background(if (focused) Palette.Gold else Palette.Ink.copy(alpha = 0.16f))
                .padding(start = 18.dp, end = 22.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Icons.Play, ink, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = Type.Button, color = ink, maxLines = 1)
        }
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == index
            Box(
                Modifier
                    .size(if (on) 16.dp else 6.dp, 6.dp)
                    .background(if (on) Palette.Gold else Palette.Faint, if (on) RoundedCornerShape(3.dp) else CircleShape),
            )
        }
    }
}
