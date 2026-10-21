package io.shamash.ui

import io.shamash.data.Logger
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.shamash.domain.MediaItem
import io.shamash.domain.Preview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Plays a title's preview inside its tile: one MediaPlayer at a time, drawn on whichever surface is newest (the tile,
 * or the full screen after 12 s untouched). Without a [PreviewSource][io.shamash.domain.PreviewSource] for the item,
 * [start] does nothing. SPEC: HOME-20
 */
@Stable
class PreviewController(
    private val context: Context,
    private val source: suspend (MediaItem) -> Preview?,
) {
    var playingId by mutableStateOf<String?>(null)
        private set

    /** The item whose first frame is on screen (its picture fades away over it). */
    var renderingId by mutableStateOf<String?>(null)
        private set

    var fullScreen by mutableStateOf(false)
        private set

    /** Bumped by every key while a preview plays (restarts the 12 s wait). */
    var touches by mutableIntStateOf(0)
        private set

    private var player: MediaPlayer? = null
    private val surfaces = ArrayList<Surface>()

    /** Plays [item]'s preview if its provider has one (a row rebuild with the same item keeps it playing). */
    suspend fun start(item: MediaItem) {
        if (playingId == item.id) return
        val preview = try {
            source(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("preview", e)
            null
        } ?: return
        stop(null)
        play(item.id, preview)
    }

    /** Stops the preview of [id] (or whatever plays, for null). */
    fun stop(id: String?) {
        if (id != null && id != playingId) return
        player?.release()
        player = null
        playingId = null
        renderingId = null
        fullScreen = false
    }

    fun positionMs(id: String): Long? = if (id == playingId) player?.currentPosition?.toLong() else null

    /**
     * A key event: true when it only brings the full-screen preview back to its tile (that key's release is
     * swallowed too, so Back doesn't also act).
     */
    fun onKey(down: Boolean): Boolean {
        if (!down) return swallowRelease.also { swallowRelease = false }
        if (playingId == null) return false
        touches++
        if (!fullScreen) return false
        fullScreen = false
        swallowRelease = true
        return true
    }

    private var swallowRelease = false

    fun goFullScreen() {
        if (renderingId != null) fullScreen = true
    }

    internal fun attach(surface: Surface) {
        surfaces += surface
        player?.setSurface(surface)
    }

    internal fun detach(surface: Surface) {
        surfaces -= surface
        player?.setSurface(surfaces.lastOrNull())
    }

    private fun play(id: String, preview: Preview) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(context, Uri.parse(preview.url), preview.headers)
            mp.setVolume(VOLUME, VOLUME)
            surfaces.lastOrNull()?.let(mp::setSurface)
            mp.setOnPreparedListener { it.start() }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) renderingId = id
                false
            }
            mp.setOnErrorListener { _, what, extra -> Logger.w("preview error $what/$extra"); stop(id); true }
            mp.setOnCompletionListener { stop(id) }
            mp.prepareAsync()
            player = mp
            playingId = id
        } catch (e: Exception) {
            Logger.w("preview", e)
            mp.release()
        }
    }

    private companion object {
        const val VOLUME = 0.8f
    }
}

/** Where the preview is drawn (a TextureView, so it clips and scales with the tile). */
@Composable
fun PreviewSurface(controller: PreviewController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> TextureView(context).apply { surfaceTextureListener = SurfaceBridge(controller) } },
    )
}

/** The preview over the whole screen after it has played 12 s untouched; any key brings it back. SPEC: HOME-20 */
@Composable
fun PreviewFullScreen(controller: PreviewController) {
    LaunchedEffect(controller.renderingId, controller.touches) {
        if (controller.renderingId == null) return@LaunchedEffect
        delay(FULL_SCREEN_AFTER_MS)
        controller.goFullScreen()
    }
    if (!controller.fullScreen) return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PreviewSurface(controller, Modifier.fillMaxSize())
    }
}

private const val FULL_SCREEN_AFTER_MS = 12_000L

private class SurfaceBridge(private val controller: PreviewController) : TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also(controller::attach)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        surface?.let {
            controller.detach(it)
            it.release()
        }
        surface = null
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
