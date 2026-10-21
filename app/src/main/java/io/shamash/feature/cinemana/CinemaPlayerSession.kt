package io.shamash.feature.cinemana

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.view.SurfaceHolder
import io.shamash.data.Logger
import io.shamash.data.cinemana.*
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.io.Closeable
import java.io.IOException

internal data class PlayerState(
    val playback: CinemaPlayback? = null,
    val ready: Boolean = false,
    val loading: Boolean = true,
    val firstFrame: Boolean = false,
    val playing: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val aspect: Float = 16f / 9,
    val subtitle: String? = null,
    val language: String = SubtitleLanguage.ARABIC,
    val speed: Float = 1f,
    val next: CinemanaTitle? = null,
    val countdown: Int? = null,
    val failed: Boolean = false,
)

/** Owns one playback lifetime; every exit closes sockets, cancels downloads and releases MediaPlayer. */
internal class CinemaPlayerSession(
    private val library: CinemaLibrary,
    private val profile: Profile,
    private val id: String,
    private val atMs: Long?,
    private val onNext: (String) -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private var streamer: RangeStreamer? = null
    private var holder: SurfaceHolder? = null
    private var load: Job? = null
    private var subtitleJob: Job? = null
    private var subtitles: SubRip? = null
    private var ticker: Job? = null
    private var active = true
    private var closed = false
    private var countdownCancelled = false
    private var countdownStarted = 0L
    private var savedAt = 0L
    private var pendingSeek: Long? = null
    private var startAfterSeek = false
    private data class Save(val title: CinemanaTitle, val position: Long, val duration: Long)
    private val saves = Channel<Save>(Channel.CONFLATED)
    private val saveWorker = CoroutineScope(Dispatchers.IO).launch {
        for (save in saves) library.store.progress(save.title, save.position, save.duration)
    }
    private var resumePosition: Long? = null

    fun start() {
        if (closed || !active || load?.isActive == true || player != null) return
        load = scope.launch {
            mutable.value = mutable.value.copy(loading = true, failed = false, firstFrame = false)
            try {
                val playback = library.playback(id, profile) ?: throw IOException("Video unavailable")
                val saved = library.store.snapshot()
                val initial = resumePosition ?: atMs?.takeIf { it >= 0 }
                    ?: saved.history.find { it.title.id == playback.details.title.id }?.resumeMs ?: 0
                val episodes = playback.episodes.sortedWith(compareBy({ it.season }, { it.episode }))
                val index = episodes.indexOfFirst { it.id == playback.details.title.id }
                val next = if (index >= 0) episodes.getOrNull(index + 1) else null
                mutable.value = mutable.value.copy(playback = playback, language = saved.subtitles, position = initial, next = next)
                loadSubtitles(saved.subtitles)
                val file = playback.files.maxByOrNull { it.height } ?: throw IOException("No playable file")
                val stream = RangeStreamer(library.http.client, file.url)
                streamer = stream
                try { withContext(Dispatchers.IO) { stream.start() } }
                catch (e: Exception) { stream.close(); throw e }
                ensureActive()
                if (!active) return@launch
                prepare(stream.address, initial)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Logger.w("Cinemana playback", e)
                release()
                mutable.value = mutable.value.copy(failed = true, loading = false, ready = false)
            }
        }
    }

    private fun prepare(address: String, initial: Long) {
        val media = MediaPlayer()
        player = media
        media.setScreenOnWhilePlaying(true)
        holder?.let { media.setDisplay(it) }
        media.setOnPreparedListener {
            if (player !== it || !active) return@setOnPreparedListener
            val duration = it.duration.toLong().coerceAtLeast(0)
            mutable.value = mutable.value.copy(ready = true, duration = duration)
            if (initial > 0) {
                startAfterSeek = true
                pendingSeek = initial.coerceIn(0, (duration - 1).coerceAtLeast(0))
                it.seekTo(pendingSeek!!, MediaPlayer.SEEK_CLOSEST)
            } else beginPlaying(it)
            ticker = scope.launch { tick() }
        }
        media.setOnSeekCompleteListener {
            pendingSeek = null
            if (active && startAfterSeek) beginPlaying(it)
            startAfterSeek = false
        }
        media.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) mutable.value = mutable.value.copy(aspect = width.toFloat() / height)
        }
        media.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> mutable.value = mutable.value.copy(firstFrame = true, loading = false)
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> mutable.value = mutable.value.copy(loading = true)
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> mutable.value = mutable.value.copy(loading = false)
            }
            false
        }
        media.setOnCompletionListener {
            mutable.value = mutable.value.copy(playing = false, position = mutable.value.duration, loading = false)
            save()
            if (!countdownCancelled) mutable.value.next?.let { next -> onNext(next.id) }
        }
        media.setOnErrorListener { _, what, extra ->
            Logger.w("Cinemana MediaPlayer error $what/$extra")
            save(); release()
            mutable.value = mutable.value.copy(failed = true, loading = false, ready = false, playing = false)
            true
        }
        media.setDataSource(address)
        media.prepareAsync()
    }

    private fun beginPlaying(media: MediaPlayer) {
        if (player !== media || !active) return
        runCatching {
            media.playbackParams = PlaybackParams().setSpeed(mutable.value.speed)
            media.start()
            mutable.value = mutable.value.copy(playing = true)
        }.onFailure { Logger.w("Cinemana start", it) }
    }

    fun attach(surface: SurfaceHolder?) {
        holder = surface
        runCatching { player?.setDisplay(surface) }
    }

    fun toggle() {
        val media = player ?: return
        if (!mutable.value.ready) return
        if (mutable.value.playing) {
            cancelCountdown()
            startAfterSeek = false
            runCatching { media.pause() }
            mutable.value = mutable.value.copy(playing = false)
            save()
        } else beginPlaying(media)
    }

    fun seek(position: Long) {
        if (!mutable.value.ready) return
        cancelCountdown()
        pendingSeek = position.coerceIn(0, (mutable.value.duration - 1).coerceAtLeast(0))
        startAfterSeek = mutable.value.playing
        runCatching { player?.seekTo(pendingSeek!!, MediaPlayer.SEEK_CLOSEST) }
        mutable.value = mutable.value.copy(position = pendingSeek!!, subtitle = null)
    }

    fun speed(speed: Float) {
        if (!mutable.value.ready) return
        val playing = mutable.value.playing
        runCatching {
            player?.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(.5f, 2f))
            if (!playing) player?.pause()
            mutable.value = mutable.value.copy(speed = speed.coerceIn(.5f, 2f))
        }.onFailure { Logger.w("Cinemana playback speed", it) }
    }

    fun language(language: String) {
        mutable.value = mutable.value.copy(language = language, subtitle = null)
        scope.launch { library.store.subtitles(language) }
        loadSubtitles(language)
    }

    private fun loadSubtitles(language: String) {
        subtitleJob?.cancel()
        subtitles = null
        val url = mutable.value.playback?.subtitles?.forLanguage(language) ?: return
        subtitleJob = scope.launch {
            val call = library.http.client.newBuilder().cache(null).callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build().newCall(Request.Builder().url(url).build())
            val cancellation = launch(Dispatchers.IO) { try { awaitCancellation() } finally { call.cancel() } }
            try {
                subtitles = withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body ?: return@use null
                        body.byteStream().use { input ->
                            val bytes = input.readBytesBounded(4 * 1024 * 1024)
                            SubRip.parse(SubRip.decode(bytes))
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Logger.w("Cinemana subtitles", e) }
            finally { cancellation.cancel() }
        }
    }

    private suspend fun tick() {
        while (currentCoroutineContext().isActive) {
            delay(200)
            val media = player ?: break
            if (!mutable.value.ready) continue
            val position = pendingSeek ?: runCatching { media.currentPosition.toLong() }.getOrDefault(mutable.value.position)
            mutable.value = mutable.value.copy(position = position, subtitle = subtitles?.at(position))
            val state = mutable.value
            if (state.playing && pendingSeek == null && profile.contentLevel != ContentLevel.ALL) {
                state.playback?.details?.scenes?.firstOrNull { position >= it.startMs && position < it.endMs - 300 }
                    ?.let { seek(it.endMs) }
            }
            updateCountdown()
            if (System.nanoTime() - savedAt >= 5_000_000_000L) { save(); savedAt = System.nanoTime() }
        }
    }

    private fun updateCountdown() {
        val s = mutable.value
        val eligible = s.next != null && s.duration > 0 && s.duration - s.position <= 30_000 && s.playing && !s.loading
        if (!eligible || countdownCancelled) {
            countdownStarted = 0
            if (s.countdown != null) mutable.value = s.copy(countdown = null)
            return
        }
        if (countdownStarted == 0L) countdownStarted = System.nanoTime()
        val remaining = 10 - ((System.nanoTime() - countdownStarted) / 1_000_000_000).toInt()
        mutable.value = s.copy(countdown = remaining.coerceAtLeast(0))
        if (remaining <= 0) { cancelCountdown(); save(); onNext(s.next!!.id) }
    }

    fun cancelCountdown() {
        countdownCancelled = true
        countdownStarted = 0
        mutable.value = mutable.value.copy(countdown = null)
    }

    fun next() { cancelCountdown(); save(); mutable.value.next?.let { onNext(it.id) } }

    fun stop() {
        active = false
        resumePosition = mutable.value.position
        save()
        load?.cancel(); load = null
        subtitleJob?.cancel()
        release()
        mutable.value = mutable.value.copy(ready = false, playing = false, countdown = null)
        countdownStarted = 0
    }

    fun resume() { if (!closed) { active = true; start() } }

    private fun save() {
        val state = mutable.value
        val title = state.playback?.details?.title ?: return
        // Serial writes preserve ordering; closing the queue drains the final position after disposal.
        saves.trySend(Save(title, state.position, state.duration))
    }

    private fun release() {
        ticker?.cancel(); ticker = null
        val old = player; player = null
        runCatching { old?.setOnErrorListener(null); old?.setOnCompletionListener(null); old?.release() }
        streamer?.close(); streamer = null
        pendingSeek = null
    }

    override fun close() { if (!closed) { stop(); closed = true; scope.cancel(); saves.close() } }
}

private fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        if (out.size() + count > maximum) throw IOException("Subtitle too large")
        out.write(buffer, 0, count)
    }
}
