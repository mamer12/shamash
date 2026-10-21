package io.shamash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.domain.MediaItem

/** A thin gold bar over the bottom of a card: how far the user got. SPEC: HOME-11 */
@Composable
fun BoxScope.ProgressBar(progress: Float?) {
    if (progress == null || progress <= 0f) return
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(shape)
            .background(Palette.Faint),
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(MIN_PROGRESS, 1f)).height(3.dp).background(Palette.Gold))
    }
}

private const val MIN_PROGRESS = 0.04f

/** A 16:9 card: the wide picture (or the poster), the title under it. */
@Composable
fun WideCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (item.match != null) {
        MatchCard(item.match, onClick, modifier)
        return
    }
    val d = LocalCardDims.current
    Column(modifier.width(d.wideW)) {
        FocusCard(onClick, Modifier.size(d.wideW, d.wideH).itemMenu(item), description = mediaLabel(item)) {
            Box(Modifier.fillMaxSize().background(Palette.Card))
            Picture(item.backdropUrl ?: item.posterUrl, d.wideW, d.wideH, Modifier.fillMaxSize())
            ProgressBar(item.progress)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = Type.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A video: the 16:9 thumbnail, its title (two lines) and the channel. */
@Composable
fun VideoCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalCardDims.current
    Column(modifier.width(d.wideW)) {
        FocusCard(onClick, Modifier.size(d.wideW, d.wideH).itemMenu(item), description = mediaLabel(item)) {
            Box(Modifier.fillMaxSize().background(Palette.Card))
            Picture(item.backdropUrl ?: item.posterUrl, d.wideW, d.wideH, Modifier.fillMaxSize())
            ProgressBar(item.progress)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = Type.CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotEmpty()) {
            Text(item.subtitle, style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An app's own TV banner. */
@Composable
fun AppTile(packageName: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val d = LocalCardDims.current
    FocusCard(onClick, modifier.size(d.appW, d.appH), description = label ?: packageName) {
        Box(Modifier.fillMaxSize().background(Palette.Card))
        Picture(AppBanner(packageName), d.appW, d.appH, Modifier.fillMaxSize(), fade = false)
    }
}

/** A tile for an action (an input, a settings page): a symbol over its name. */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dimens.AppW,
) {
    FocusCard(onClick, modifier.size(width, Dimens.AppH), description = label) { focused ->
        val ink = if (focused) Palette.Night else Palette.Ink
        Column(
            Modifier.fillMaxSize().background(if (focused) Palette.Ink else Palette.Card).padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Symbol(icon, ink, Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label, style = Type.CardTitle, color = ink, maxLines = 2, textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
