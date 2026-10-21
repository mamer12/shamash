package io.shamash.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import io.shamash.feature.accessibility.A11y
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import io.shamash.domain.Match
import io.shamash.domain.MediaItem

private const val CROSSFADE_MS = 700
private val HERO_CREST = 132.dp

/**
 * The full-screen picture behind the home screen: the current billboard item's photo (1280 × 720) darkened toward the
 * bottom-left, crossfading in ≤ 700 ms; [dim] (0..1, read while drawing) darkens it further. SPEC: HOME-02, PERF-04
 */
@Composable
fun BillboardBackdrop(state: BillboardState, dim: () -> Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nextUrl = state.next?.item?.backdropUrl
    LaunchedEffect(nextUrl) {
        if (nextUrl != null) {
            SingletonImageLoader.get(context).enqueue(Images.request(context, nextUrl, Images.HERO_W, Images.HERO_H))
        }
    }
    Box(modifier.fillMaxSize().background(Palette.Night)) {
        Crossfade(state.current?.item, animationSpec = tween(A11y.ms(CROSSFADE_MS)), label = "backdrop") { item ->
            when {
                item == null -> Unit
                item.match != null -> MatchBackdrop(item.match)
                else -> HeroPhoto(item)
            }
        }
        Box(Modifier.fillMaxSize().scrims())
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Palette.Night, alpha = dim().coerceIn(0f, 1f)) })
    }
}

@Composable
private fun HeroPhoto(item: MediaItem) {
    val context = LocalContext.current
    val url = item.backdropUrl ?: return
    val request = remember(url) { Images.request(context, url, Images.HERO_W, Images.HERO_H, fade = false) }
    AsyncImage(request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

/** Match day: deep night with a gold glow, the crests large on the right, kick-off or score between. SPEC: MC-03 */
@Composable
private fun MatchBackdrop(match: Match) {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val glow = Brush.radialGradient(
                listOf(Palette.Gold.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.42f),
                radius = size.height * 0.75f,
            )
            val base = Brush.verticalGradient(listOf(Palette.Dusk, Palette.Night))
            onDrawBehind {
                drawRect(base)
                drawRect(glow)
            }
        },
    ) {
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = Dimens.Margin * 1.5f, bottom = 80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crest(match.home, HERO_CREST)
            Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(matchMiddle(match), style = Type.HeroScore, maxLines = 1)
                MatchNote(match, Type.HeroMeta)
            }
            Crest(match.away, HERO_CREST)
        }
    }
}

/** Darker toward the start side and the bottom (where the words are), a little at the top (the clock). */
private fun Modifier.scrims() = drawWithCache {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val across = Brush.horizontalGradient(
        0f to Palette.Night.copy(alpha = 0.92f),
        0.42f to Palette.Night.copy(alpha = 0.55f),
        0.75f to Color.Transparent,
        startX = if (rtl) size.width else 0f,
        endX = if (rtl) 0f else size.width,
    )
    val down = Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.Night)
    val top = Brush.verticalGradient(0f to Palette.Night.copy(alpha = 0.55f), 0.22f to Color.Transparent)
    onDrawBehind {
        drawRect(across)
        drawRect(down)
        drawRect(top)
    }
}
