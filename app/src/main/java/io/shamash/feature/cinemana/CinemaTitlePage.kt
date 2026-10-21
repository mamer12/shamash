package io.shamash.feature.cinemana

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.data.cinemana.CinemaLibrary
import io.shamash.data.cinemana.CinemaProgressRules
import io.shamash.data.cinemana.CinemaRating
import io.shamash.data.cinemana.CinemaSaved
import io.shamash.data.cinemana.CinemanaDetails
import io.shamash.data.cinemana.CinemanaTitle
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.ui.Dimens
import io.shamash.ui.ExpandingRow
import io.shamash.ui.Palette
import io.shamash.ui.Type
import kotlinx.coroutines.launch

/** Title actions and series episodes share the same per-episode progress used by the player. */
@Composable
fun CinemaTitlePage(library: CinemaLibrary, profile: Profile, id: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var details by remember(id, profile) { mutableStateOf<CinemanaDetails?>(null) }
    var episodes by remember(id, profile) { mutableStateOf<List<CinemanaTitle>>(emptyList()) }
    var similar by remember(id, profile) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember(id, profile) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val saved by library.store.state.collectAsState()
    LaunchedEffect(id, profile, retry) {
        loading = true
        library.store.snapshot()
        val episode = library.detail(id, profile)
        val found = if (episode != null && episode.title.pageId != episode.title.id)
            library.detail(episode.title.pageId, profile) else episode
        details = found
        if (found?.title?.isSeries == true) episodes = library.episodes(found.title.pageId, profile)
        loading = false
    }
    LaunchedEffect(details, saved.ratings, profile) {
        similar = details?.let { library.similar(it.title, profile) }.orEmpty()
    }
    val currentDetails = details
    val title = currentDetails?.title
    val focus = remember { FocusRequester() }
    LaunchedEffect(title, loading) { if (!loading) focus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        CinemaBackdrop(title?.wide)
        if (title != null) LazyColumn(contentPadding = PaddingValues(top = 32.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { CinemaButton(stringResource(R.string.cinema_back), onBack, Modifier.padding(horizontal = Dimens.Margin).focusRequester(focus)) }
            item {
                Column(Modifier.padding(horizontal = Dimens.Margin).width(650.dp)) {
                    Text(title.title, style = Type.HeroTitle)
                    Text(titleFacts(title), style = Type.HeroMeta, modifier = Modifier.padding(vertical = 10.dp))
                    Text(title.overview, style = Type.HeroBody, maxLines = 4)
                }
            }
            item {
                Row(Modifier.padding(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    TitleActions(library, currentDetails, episodes, saved, onOpen, Modifier)
                    if (title.isSeries) EpisodePanel(episodes, saved, onOpen, Modifier.weight(1f))
                }
            }
            if (similar.isNotEmpty()) item {
                Text(stringResource(R.string.cinema_similar), style = Type.RowTitle,
                    modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = 10.dp))
                ExpandingRow(similar, false, { item, _ ->
                    (item.open as? OpenTarget.AppPage)?.uri?.let(onOpen)
                }, previews = null)
            }
        }
        if (!loading && title == null) Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.cinema_unavailable), style = Type.HeroBody)
            CinemaButton(stringResource(R.string.cinema_back), onBack, Modifier.focusRequester(focus))
            CinemaButton(stringResource(R.string.cinema_retry), { retry++ })
        }
        CinemaLoader(loading, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TitleActions(library: CinemaLibrary, details: CinemanaDetails, episodes: List<CinemanaTitle>,
    saved: CinemaSaved, onOpen: (String) -> Unit, modifier: Modifier) {
    val title = details.title
    val target = if (title.isSeries) CinemaProgressRules.episode(episodes, saved.history) else title
    val progress = saved.history.firstOrNull { it.title.id == target?.id }
    val listed = saved.myList.any { it.pageId == title.pageId }
    val rating = saved.ratings.firstOrNull { it.title.pageId == title.pageId }?.rating
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (target != null) {
            CinemaButton(stringResource(if ((progress?.resumeMs ?: 0) > 0) R.string.cinema_resume else R.string.cinema_play),
                { onOpen("shamash://cinemana/play/${target.id}") }, modifier)
            if ((progress?.resumeMs ?: 0) > 0) CinemaButton(stringResource(R.string.cinema_restart),
                { onOpen("shamash://cinemana/play/${target.id}?at=0") })
        } else Text(stringResource(R.string.cinema_no_episodes), style = Type.Small, modifier = modifier)
        CinemaButton(stringResource(if (listed) R.string.cinema_remove_list else R.string.cinema_add_list),
            { scope.launch { library.store.toggleList(title) } })
        details.trailer?.let { trailer ->
            CinemaButton(stringResource(R.string.cinema_trailer), {
                val uri = if (trailer.startsWith("https://") || trailer.startsWith("http://")) Uri.parse(trailer)
                    else Uri.parse("https://www.youtube.com/watch?v=$trailer")
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
            })
        }
        CinemaRating.entries.forEach { choice ->
            val label = when (choice) {
                CinemaRating.LOVE -> R.string.cinema_love
                CinemaRating.LIKE -> R.string.cinema_like
                CinemaRating.DISLIKE -> R.string.cinema_dislike
            }
            CinemaButton(stringResource(label), { scope.launch { library.store.rate(title, choice) } }, selected = rating == choice)
        }
    }
}

@Composable
private fun EpisodePanel(episodes: List<CinemanaTitle>, saved: CinemaSaved, onOpen: (String) -> Unit, modifier: Modifier) {
    val seasons = episodes.map { it.season }.distinct().sorted()
    var season by remember(episodes) { mutableStateOf(seasons.firstOrNull() ?: 0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cinema_episodes), style = Type.RowTitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons) { value -> CinemaButton(stringResource(R.string.cinema_season, value), { season = value }, selected = season == value) }
        }
        LazyColumn(Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(episodes.filter { it.season == season }, key = { it.id }) { episode ->
                val progress = saved.history.firstOrNull { it.title.id == episode.id }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    CinemaButton(stringResource(R.string.cinema_episode, episode.episode, episode.title),
                        { onOpen("shamash://cinemana/play/${episode.id}") })
                    if (progress != null) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.Card)) {
                            Box(Modifier.fillMaxWidth(progress.fraction).height(3.dp).background(Palette.Gold))
                        }
                        Text(stringResource(if (progress.finished) R.string.cinema_completed else R.string.cinema_progress,
                            (progress.fraction * 100).toInt()), style = Type.Small)
                    }
                }
            }
        }
        if (episodes.isEmpty()) Text(stringResource(R.string.cinema_no_episodes), style = Type.Small)
    }
}

@Composable
private fun titleFacts(title: CinemanaTitle): String = listOfNotNull(
    title.year.takeIf { it.isNotBlank() },
    title.minutes.takeIf { it > 0 }?.let { stringResource(R.string.cinema_minutes, it) },
    title.rating.takeIf { it.isNotBlank() },
    title.stars.takeIf { it.isNotBlank() },
).joinToString(stringResource(R.string.cinema_separator))
