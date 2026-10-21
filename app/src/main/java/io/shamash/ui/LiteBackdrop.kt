package io.shamash.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.roundToInt

/** The lite billboard photo: half of 1080p, enough under the scrims on a weak TV. SPEC: PERF-06 */
const val LITE_HERO_W = 960
const val LITE_HERO_H = 540

/**
 * Lite's billboard photo: the scrims baked into the picture once (off the main thread), drawn as one opaque bitmap
 * per frame, darkened by a color filter rather than a full-screen layer over it. The last photo stays until the next
 * has loaded (no crossfade, no flash). SPEC: PERF-06, HOME-02
 */
@Composable
fun LitePhoto(url: String?, dim: () -> Float) {
    val context = LocalContext.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val request = remember(url, rtl) { url?.let { Images.hero(context, it, lite = true, rtl = rtl) } }
    val painter = rememberAsyncImagePainter(request)
    var shown by remember { mutableStateOf<Painter?>(null) }
    LaunchedEffect(painter) {
        painter.state.collect { if (it is AsyncImagePainter.State.Success) shown = it.painter }
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val picture = shown ?: return@drawBehind
            with(picture) { draw(size, colorFilter = DimFilters.of(dim())) }
        },
    )
}

/** Darkening toward Palette.Night as color filters, one per step, so drawing never allocates. */
private object DimFilters {
    private const val STEPS = 32
    private val filters = arrayOfNulls<ColorFilter>(STEPS + 1)

    fun of(dim: Float): ColorFilter? {
        val step = (dim.coerceIn(0f, 1f) * STEPS).roundToInt()
        if (step == 0) return null
        return filters[step] ?: build(step.toFloat() / STEPS).also { filters[step] = it }
    }

    private fun build(a: Float): ColorFilter {
        val keep = 1f - a
        val n = Palette.Night
        return ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    keep, 0f, 0f, 0f, n.red * 255f * a,
                    0f, keep, 0f, 0f, n.green * 255f * a,
                    0f, 0f, keep, 0f, n.blue * 255f * a,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}

/**
 * Center-crops the photo to [width] × [height] and paints Backdrop's three scrims into it, as RGB_565 (opaque, half
 * the memory). Same stops as the full look's `scrims()`. SPEC: PERF-06
 */
class BakedScrims(private val width: Int, private val height: Int, private val rtl: Boolean) : Transformation() {
    override val cacheKey: String = "scrims:$width:$height:$rtl"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        val scale = max(width.toFloat() / input.width, height.toFloat() / input.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - input.width * scale) / 2f, (height - input.height * scale) / 2f)
        }
        canvas.drawBitmap(input, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val w = width.toFloat()
        val h = height.toFloat()
        val night = Palette.Night.toArgb()
        val paint = Paint()
        paint.shader = LinearGradient(
            if (rtl) w else 0f, 0f, if (rtl) 0f else w, 0f,
            intArrayOf(alpha(night, 0.92f), alpha(night, 0.55f), alpha(night, 0f), alpha(night, 0f)),
            floatArrayOf(0f, 0.42f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, intArrayOf(alpha(night, 0f), alpha(night, 0f), night), floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * TOP_SCRIM, alpha(night, 0.55f), alpha(night, 0f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h * TOP_SCRIM, paint)
        return out
    }

    private fun alpha(argb: Int, a: Float): Int = ((a * 255).roundToInt() shl 24) or (argb and 0xFFFFFF)

    private companion object {
        const val TOP_SCRIM = 0.22f
    }
}
