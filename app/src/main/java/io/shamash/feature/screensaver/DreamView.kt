package io.shamash.feature.screensaver

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * The screensaver's screen: backdrops full screen with a slow zoom, crossfading; the title and its app bottom-left; the
 * time and weather top-right; a dark gradient underneath for when there is nothing to show. Holds at most two bitmaps:
 * the one fading out is let go as soon as the fade ends. SPEC: SCR-01..03
 */
@SuppressLint("ViewConstructor")
internal class DreamView(context: Context) : FrameLayout(context) {
    private val k = resources.displayMetrics.widthPixels / DESIGN_WIDTH
    private val stage = FrameLayout(context)
    private var front = image()
    private var back = image()
    private var zoom: ObjectAnimator? = null
    private val title = text(TITLE_SIZE, Typeface.DEFAULT_BOLD, INK)
    private val app = text(APP_SIZE, Typeface.DEFAULT, DIM)
    private val caption = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; alpha = 0f }
    private val weather = text(WEATHER_SIZE, Typeface.DEFAULT, DIM)

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(NIGHT, DUSK))
        stage.addView(back, fill())
        stage.addView(front, fill())
        addView(stage, fill())
        addView(scrim(GradientDrawable.Orientation.BOTTOM_TOP), LayoutParams(LayoutParams.MATCH_PARENT, px(BOTTOM_SCRIM), Gravity.BOTTOM))
        addView(scrim(GradientDrawable.Orientation.TOP_BOTTOM), LayoutParams(LayoutParams.MATCH_PARENT, px(TOP_SCRIM), Gravity.TOP))
        caption.addView(title)
        caption.addView(app)
        addView(caption, LayoutParams(px(CAPTION_WIDTH), LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.LEFT).apply {
            setMargins(px(MARGIN_X), 0, 0, px(MARGIN_Y))
        })
        addView(clockAndWeather(), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.RIGHT).apply {
            setMargins(0, px(MARGIN_Y), px(MARGIN_X), 0)
        })
    }

    /** Fades [bitmap] in over the current one, starts its slow zoom, and lets the old one go after the fade. */
    fun show(bitmap: Bitmap, titleText: String, appName: String) {
        val incoming = back
        val outgoing = front
        front = incoming
        back = outgoing
        incoming.setImageBitmap(bitmap)
        incoming.alpha = 0f
        incoming.bringToFront()
        startZoom(incoming)
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            outgoing.setImageDrawable(null)
            outgoing.scaleX = 1f
            outgoing.scaleY = 1f
        }.start()
        caption.animate().alpha(0f).setDuration(FADE_MS / 2).withEndAction {
            title.text = titleText
            app.text = appName
            app.visibility = if (appName.isEmpty()) View.GONE else View.VISIBLE
            caption.animate().alpha(1f).setDuration(FADE_MS / 2).start()
        }.start()
    }

    fun showWeather(text: String) {
        weather.text = text
    }

    /** Lets both images go (the dream is ending). */
    fun clear() {
        zoom?.cancel()
        listOf(front, back).forEach { it.animate().cancel(); it.setImageDrawable(null) }
    }

    /** Ken Burns: up to 6% larger over the slide, towards a random point near the middle. SPEC: SCR-01 */
    private fun startZoom(view: ImageView) {
        zoom?.cancel()
        view.pivotX = width * pivot()
        view.pivotY = height * pivot()
        zoom = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, MAX_ZOOM),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, MAX_ZOOM),
        ).apply {
            duration = ShamashDream.SLIDE_MS + FADE_MS
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pivot() = PIVOT_MIN + Random.nextFloat() * (PIVOT_MAX - PIVOT_MIN)

    private fun clockAndWeather() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
        addView(TextClock(context).apply {
            format12Hour = CLOCK_12
            format24Hour = CLOCK_24
            setTextSize(TypedValue.COMPLEX_UNIT_PX, CLOCK_SIZE * k)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(INK)
            setShadowLayer(SHADOW * k, 0f, 0f, SHADOW_COLOR)
        })
        addView(weather)
    }

    private fun image() = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

    private fun text(size: Float, face: Typeface, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k)
        typeface = face
        setTextColor(color)
        maxLines = 2
        setShadowLayer(SHADOW * k, 0f, 0f, SHADOW_COLOR)
    }

    private fun scrim(orientation: GradientDrawable.Orientation) = View(context).apply {
        background = GradientDrawable(orientation, intArrayOf(SCRIM, Color.TRANSPARENT))
    }

    private fun px(value: Float) = (value * k).toInt()

    private fun fill() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    private companion object {
        const val DESIGN_WIDTH = 1920f
        const val FADE_MS = 1_500L
        const val MAX_ZOOM = 1.06f
        const val PIVOT_MIN = 0.3f
        const val PIVOT_MAX = 0.7f
        const val TITLE_SIZE = 56f
        const val APP_SIZE = 28f
        const val CLOCK_SIZE = 72f
        const val WEATHER_SIZE = 30f
        const val SHADOW = 12f
        const val MARGIN_X = 96f
        const val MARGIN_Y = 64f
        const val CAPTION_WIDTH = 1100f
        const val BOTTOM_SCRIM = 480f
        const val TOP_SCRIM = 280f
        const val CLOCK_12 = "h:mm"
        const val CLOCK_24 = "HH:mm"
        const val NIGHT = 0xFF0A0C1E.toInt()
        const val DUSK = 0xFF14172E.toInt()
        const val INK = 0xFFF5F0E6.toInt()
        const val DIM = 0xB3F5F0E6.toInt()
        const val SCRIM = 0xB3000000.toInt()
        const val SHADOW_COLOR = 0x99000000.toInt()
    }
}
