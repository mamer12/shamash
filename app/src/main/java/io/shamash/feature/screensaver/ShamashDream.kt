package io.shamash.feature.screensaver

import android.graphics.Bitmap
import android.service.dreams.DreamService
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The screensaver: the featured titles' backdrops, 30 s each, with the time and weather; a dark gradient and the clock
 * when there is nothing to show. Registered as `io.shamash/.feature.screensaver.ShamashDream`. SPEC: SCR-01..03
 */
class ShamashDream : DreamService() {
    private var scope: CoroutineScope? = null
    private var view: DreamView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        view = DreamView(this).also(::setContentView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val screen = view ?: return
        scope = MainScope().apply { launch { play(screen) } }
    }

    override fun onDreamingStopped() {
        stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stop()
        view = null
        super.onDetachedFromWindow()
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        view?.clear()
    }

    private suspend fun play(screen: DreamView) {
        val locator = ServiceLocator.get(this)
        val profile = withContext(Dispatchers.IO) { locator.prefs.current() }
        scope?.launch { showWeather(locator, profile, screen) }
        val slides = featured(locator, profile)
        if (slides.isEmpty()) return     // SCR-02: the gradient and the clock
        val appNames = withContext(Dispatchers.IO) { locator.apps.installed().associate { it.packageName to it.label } }
        var index = 0
        var failures = 0
        while (failures < slides.size) {
            val item = slides[index++ % slides.size]
            val bitmap = item.backdropUrl?.let { load(it) }
            if (bitmap == null) {
                failures++
                continue
            }
            failures = 0
            screen.show(bitmap, item.title, item.appPackage?.let(appNames::get).orEmpty())
            delay(SLIDE_MS)
        }
    }

    /** Every provider's featured titles with a backdrop, asked in parallel; one failing provider is skipped. */
    private suspend fun featured(locator: ServiceLocator, profile: Profile): List<MediaItem> = coroutineScope {
        locator.providers.filter { it.isEnabled(profile) }
            .map { provider -> async { softly(provider.id) { provider.featured(profile) }.orEmpty() } }
            .awaitAll()
            .flatten()
            .filter { !it.backdropUrl.isNullOrBlank() }
            .distinctBy { it.backdropUrl }
            .take(MAX_SLIDES)
    }

    private suspend fun showWeather(locator: ServiceLocator, profile: Profile, screen: DreamView) {
        val weather = softly("weather") { locator.weather.current(profile) } ?: return
        screen.showWeather(getString(R.string.screensaver_temperature, weather.temperatureC.roundToInt()))
    }

    /** From the disk cache when it's there, else the network; never kept in the memory cache. SPEC: SCR-03 */
    private suspend fun load(url: String): Bitmap? {
        val loader = SingletonImageLoader.get(this)
        for (network in listOf(CachePolicy.DISABLED, CachePolicy.ENABLED)) {
            val request = ImageRequest.Builder(this)
                .data(url)
                .size(IMAGE_WIDTH, IMAGE_HEIGHT)
                .memoryCachePolicy(CachePolicy.READ_ONLY)
                .networkCachePolicy(network)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) return result.image.toBitmap()
        }
        return null
    }

    private suspend fun <T> softly(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("screensaver $what", e)
            null
        }

    companion object {
        const val SLIDE_MS = 30_000L
        private const val MAX_SLIDES = 20
        private const val IMAGE_WIDTH = 1280      // PERF-04: full-screen art at 1280×720
        private const val IMAGE_HEIGHT = 720
    }
}
