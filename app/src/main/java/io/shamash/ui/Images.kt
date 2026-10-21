package io.shamash.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

/** An installed app's TV banner, as an image Coil can load (off the main thread, cached at its drawn size). */
data class AppBanner(val packageName: String)

/** The one image loader: the app's OkHttp client, memory ≤ 20% of the heap, app banners. SPEC: ARCH-04, PERF-04 */
object Images {
    private const val MEMORY_SHARE = 0.2
    private const val FADE_MS = 180

    /** The full-screen billboard photo: sharp enough under the scrims at a third of 1080p's pixels. SPEC: PERF-04 */
    const val HERO_W = 1280
    const val HERO_H = 720

    fun install(client: OkHttpClient, banner: (String) -> Drawable?) {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_SHARE).build() }
                .components {
                    add(PrivateImages)   // SPEC: PEX-04: token-bearing image URLs never reach the disk cache
                    add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                    add(BannerKeyer(), AppBanner::class)
                    add(BannerFetcher.Factory(banner), AppBanner::class)
                }
                .crossfade(FADE_MS)
                .build()
        }
    }

    fun request(context: Context, data: Any?, widthPx: Int, heightPx: Int, fade: Boolean = true): ImageRequest =
        ImageRequest.Builder(context).data(data).size(widthPx, heightPx).crossfade(fade).build()
}

/** An image requested at the size it's drawn ([width] × [height]), cropped to fill. SPEC: PERF-04 */
@Composable
fun Picture(
    data: Any?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fade: Boolean = true,
    onLoaded: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(data, width, height, fade) {
        with(density) { Images.request(context, data, width.roundToPx(), height.roundToPx(), fade) }
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = onLoaded?.let { done -> { done() } },
    )
}

private class BannerKeyer : Keyer<AppBanner> {
    override fun key(data: AppBanner, options: Options) = "banner:${data.packageName}:${options.size}"
}

/** Draws the app's banner into a bitmap of the requested size; an icon (not a banner) sits centered on a card. */
private class BannerFetcher(
    private val data: AppBanner,
    private val options: Options,
    private val load: (String) -> Drawable?,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val drawable = load(data.packageName) ?: return@withContext null
        val w = options.size.width.pxOrElse { DEFAULT_W }
        val h = options.size.height.pxOrElse { DEFAULT_H }
        ImageFetchResult(rasterize(drawable, w, h).asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun rasterize(d: Drawable, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        val isBanner = iw > 0 && ih > 0 && iw.toFloat() / ih > BANNER_SHAPE
        if (isBanner || iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h)
        } else {
            canvas.drawColor(Palette.Card.toArgb())
            val side = (h * ICON_SHARE).roundToInt()
            val left = (w - side) / 2
            val top = (h - side) / 2
            d.setBounds(left, top, left + side, top + side)
        }
        d.draw(canvas)
        return bitmap
    }

    class Factory(private val load: (String) -> Drawable?) : Fetcher.Factory<AppBanner> {
        override fun create(data: AppBanner, options: Options, imageLoader: ImageLoader): Fetcher =
            BannerFetcher(data, options, load)
    }

    private companion object {
        const val DEFAULT_W = 320
        const val DEFAULT_H = 180
        const val BANNER_SHAPE = 1.3f
        const val ICON_SHARE = 0.6f
    }
}
