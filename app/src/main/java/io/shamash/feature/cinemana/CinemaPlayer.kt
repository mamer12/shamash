package io.shamash.feature.cinemana

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.shamash.R
import io.shamash.data.cinemana.CinemaLibrary
import io.shamash.data.cinemana.SubtitleLanguage
import io.shamash.domain.Profile
import kotlinx.coroutines.delay

@Composable
fun CinemaPlayer(library: CinemaLibrary, profile: Profile, id: String, atMs: Long?, onPlay: (String) -> Unit, onBack: () -> Unit) {
    val next by rememberUpdatedState(onPlay)
    val session = remember(library, profile, id, atMs) { CinemaPlayerSession(library, profile, id, atMs) { next(it) } }
    val state by session.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var controls by remember(session) { mutableStateOf(true) }
    var activity by remember(session) { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.stop()
            if (event == Lifecycle.Event.ON_START) session.resume()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session.start()
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    LaunchedEffect(controls, state.ready) {
        if (controls && state.ready) runCatching { playFocus.requestFocus() }
        else if (!controls) rootFocus.requestFocus()
    }
    LaunchedEffect(activity, state.playing, controls) {
        if (controls && state.playing) { delay(5_000); controls = false }
    }
    BackHandler { if (controls && state.ready) controls = false else onBack() }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus)
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            activity++
            when (key.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    session.toggle(); controls = true; true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { session.seek(state.position + 10_000); true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { session.seek(state.position - 10_000); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!controls) { controls = true; true } else false
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!controls) { session.seek(state.position - 10_000); true } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controls) { session.seek(state.position + 10_000); true } else false
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (!controls) { controls = true; true } else false
                else -> false
            }
        }.focusable()) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * state.aspect)
            key(session) { AndroidView(factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = session.attach(holder)
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = session.attach(holder)
                        override fun surfaceDestroyed(holder: SurfaceHolder) = session.attach(null)
                    })
                }
            }, modifier = Modifier.width(width).height(width / state.aspect)) }
        }
        val artAlpha by animateFloatAsState(if (state.firstFrame) 0f else 1f, tween(450), label = "title art")
        if (artAlpha > 0f) AsyncImage(model = state.playback?.details?.title?.let { it.wide ?: it.poster }, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha })
        CinemaLoader(state.loading, Modifier.align(Alignment.Center))
        state.subtitle?.let { cue ->
            Text(cue, color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (controls) 195.dp else 44.dp)
                    .widthIn(max = 850.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(3.dp)).padding(8.dp))
        }
        if (state.failed) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .8f)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.cinema_player_error), color = Color.White)
            Button(onClick = { session.start() }) { Text(stringResource(R.string.cinema_player_retry)) }
            Button(onClick = onBack) { Text(stringResource(R.string.cinema_player_back)) }
        }
        if (controls && state.ready) PlayerControls(state, session, playFocus, Modifier.align(Alignment.BottomCenter))
        val intro = state.playback?.details?.intros?.firstOrNull { state.position >= it.startMs && state.position < it.endMs - 1500 }
        if (intro != null && state.ready) Button(onClick = { activity++; session.seek(intro.endMs) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (controls) 210.dp else 70.dp)) {
            Text(stringResource(R.string.cinema_player_skip_intro))
        }
        state.countdown?.let { seconds ->
            Row(Modifier.align(Alignment.TopEnd).padding(36.dp).background(Color.Black.copy(alpha = .8f)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = session::next) { Text(stringResource(R.string.cinema_player_next_countdown, seconds)) }
                Button(onClick = session::cancelCountdown) { Text(stringResource(R.string.cinema_player_cancel)) }
            }
        }
    }
}

@Composable
private fun PlayerControls(state: PlayerState, session: CinemaPlayerSession, focus: FocusRequester, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = .82f)).padding(horizontal = 36.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.playback?.details?.title?.title.orEmpty(), color = Color.White, fontSize = 22.sp)
            Text("${playerTime(state.position)} / ${playerTime(state.duration)}", color = Color.White, fontSize = 18.sp)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .25f))) {
            val progress = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFE50914)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = session::toggle, modifier = Modifier.focusRequester(focus)) {
                Text(stringResource(if (state.playing) R.string.cinema_player_pause else R.string.cinema_player_play))
            }
            Button(onClick = { session.seek(state.position - 10_000) }) { Text(stringResource(R.string.cinema_player_rewind)) }
            Button(onClick = { session.seek(state.position + 10_000) }) { Text(stringResource(R.string.cinema_player_forward)) }
            Button(onClick = { session.speed(if (state.speed >= 2f) .5f else state.speed + .25f) }) {
                Text(stringResource(R.string.cinema_player_speed, state.speed))
            }
            val language = when (state.language) {
                SubtitleLanguage.ARABIC -> R.string.cinema_player_arabic
                SubtitleLanguage.ENGLISH -> R.string.cinema_player_english
                else -> R.string.cinema_player_off
            }
            Button(onClick = {
                val choices = SubtitleLanguage.ALL
                session.language(choices[(choices.indexOf(state.language) + 1).mod(choices.size)])
            }) { Text(stringResource(R.string.cinema_player_subtitles, stringResource(language))) }
            if (state.next != null) Button(onClick = session::next) { Text(stringResource(R.string.cinema_player_next)) }
        }
    }
}

internal fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
