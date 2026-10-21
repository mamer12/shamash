package io.shamash.feature.cinemana

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.compose.AsyncImage
import io.shamash.R
import io.shamash.data.cinemana.CinemaBrowseData
import io.shamash.data.cinemana.CinemaLibrary
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ShelfStyle
import io.shamash.ui.Dimens
import io.shamash.ui.ExpandingRow
import io.shamash.ui.Images
import io.shamash.ui.FocusCard
import io.shamash.ui.Palette
import io.shamash.ui.PreviewController
import io.shamash.ui.PreviewFullScreen
import io.shamash.ui.TextInput
import io.shamash.ui.Type
import io.shamash.ui.rememberAlignStart
import io.shamash.ui.shelfTitle
import kotlinx.coroutines.delay

private val browseTabs = listOf("home", "movies", "series", "list", "search")
private val browseLabels = listOf(R.string.cinema_home, R.string.cinema_movies, R.string.cinema_series,
    R.string.cinema_my_list, R.string.cinema_search)

/** Full catalog with one local library and the same in-tile preview behavior as Shamash Home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinemaBrowse(library: CinemaLibrary, profile: Profile, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var query by rememberSaveable { mutableStateOf("") }
    var data by remember(profile, tab) { mutableStateOf(CinemaBrowseData(emptyList(), emptyList())) }
    var loading by remember(profile, tab) { mutableStateOf(true) }
    var focused by remember(profile, tab) { mutableStateOf<MediaItem?>(null) }
    var inRows by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val saved by library.store.state.collectAsState()
    val context = LocalContext.current
    val previews = remember(library, profile) { PreviewController(context.applicationContext) { library.preview(it, profile) } }
    LifecycleStartEffect(previews) { onStopOrDispose { previews.stop(null) } }
    LaunchedEffect(tab, query, profile, saved, retry) {
        loading = true
        if (tab == "search") delay(350)
        data = library.browse(profile, tab, query)
        loading = false
    }
    var featuredIndex by remember(data.featured) { mutableIntStateOf(0) }
    val featured = data.featured.getOrNull(featuredIndex)
    LaunchedEffect(featuredIndex, inRows, data.featured) {
        if (!inRows && data.featured.size > 1) {
            delay(9_000)
            featuredIndex = (featuredIndex + 1) % data.featured.size
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(tab) { list.scrollToItem(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val openItem: (MediaItem, Long?) -> Unit = { item, at ->
        previews.stop(null)
        val target = (item.open as? OpenTarget.AppPage)?.uri
        if (target != null) onOpen(if (at != null) android.net.Uri.parse(target).buildUpon()
            .appendQueryParameter("at", at.toString()).build().toString() else target)
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { previews.onKey(it.type == KeyEventType.KeyDown) }) {
        CinemaBackdrop(if (inRows) focused?.backdropUrl else featured?.backdropUrl, inRows)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAlignStart(85.dp)) {
            LazyColumn(state = list, contentPadding = PaddingValues(top = 85.dp, bottom = 360.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (tab == "search") item {
                    TextInput(stringResource(R.string.cinema_search_hint), query, { query = it },
                        Modifier.padding(horizontal = Dimens.Margin))
                }
                if (tab in listOf("home", "movies", "series")) item {
                    featured?.let { item ->
                        CinemaBillboard(item, { openItem(item, null) }, Modifier.onFocusChanged { if (it.hasFocus) inRows = false })
                    }
                }
                items(data.shelves.filter { it.items.isNotEmpty() }, key = { it.id }) { shelf ->
                    Column {
                        Text(shelfTitle(shelf.titleKey, shelf.titleArg), style = Type.RowTitle,
                            modifier = Modifier.padding(horizontal = Dimens.Margin))
                        Spacer(Modifier.height(10.dp))
                        ExpandingRow(shelf.items, shelf.style == ShelfStyle.RANKED, openItem, previews,
                            onFocused = { focused = it; inRows = true })
                    }
                }
                if (!loading && data.shelves.all { it.items.isEmpty() }) item {
                    Text(stringResource(if (tab == "list") R.string.cinema_empty_list else R.string.cinema_empty),
                        style = Type.HeroBody, modifier = Modifier.padding(Dimens.Margin))
                    CinemaButton(stringResource(R.string.cinema_retry), { retry++ }, Modifier.padding(horizontal = Dimens.Margin))
                }
            }
        }
        LazyRow(Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .90f)).padding(vertical = 18.dp),
            contentPadding = PaddingValues(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(browseTabs) { key ->
                val index = browseTabs.indexOf(key)
                CinemaButton(stringResource(browseLabels[index]), { tab = key; inRows = false },
                    if (index == 0) Modifier.focusRequester(first) else Modifier, selected = tab == key)
            }
            item { CinemaButton(stringResource(R.string.cinema_settings), onSettings) }
        }
        CinemaLoader(loading, Modifier.align(Alignment.Center))
        PreviewFullScreen(previews)
    }
}

@Composable
private fun CinemaBillboard(item: MediaItem, onOpen: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = Dimens.Margin, vertical = 24.dp).width(490.dp)) {
        Text(stringResource(R.string.cinema_name), style = Type.HeroLabel)
        Spacer(Modifier.height(12.dp))
        Text(item.title, style = Type.HeroTitle, maxLines = 2)
        Text(item.subtitle, style = Type.HeroMeta, modifier = Modifier.padding(vertical = 8.dp))
        Text(item.overview, style = Type.HeroBody, maxLines = 3)
        Spacer(Modifier.height(18.dp))
        CinemaButton(stringResource(R.string.cinema_details), onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun CinemaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    FocusCard(onClick, modifier, scale = 1.03f) { focused ->
        Text(text, style = Type.Button, color = if (focused) Palette.Night else Palette.Ink,
            modifier = Modifier.background(if (focused) Palette.Gold else if (selected) Palette.Card else Palette.Dusk)
                .padding(horizontal = 16.dp, vertical = 11.dp))
    }
}

/** CIN-02: focused art at the right with an opaque left 40% and the 40–85% bottom fade. */
@Composable
internal fun CinemaBackdrop(url: String?, rows: Boolean = false) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        Crossfade(url, label = "cinema-art") { art ->
            val request = remember(context, art) { art?.let { Images.request(context, it, Images.HERO_W, Images.HERO_H, fade = false) } }
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().drawWithCache {
            val across = Brush.horizontalGradient(0f to Palette.Night, .4f to Palette.Night,
                .65f to Palette.Night.copy(alpha = if (rows) .65f else .4f), 1f to Palette.Night.copy(alpha = .45f))
            val down = Brush.verticalGradient(.4f to Color.Transparent, .85f to Palette.Night)
            onDrawBehind { drawRect(across); drawRect(down) }
        })
    }
}
