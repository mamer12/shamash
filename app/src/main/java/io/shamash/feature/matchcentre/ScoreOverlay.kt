package io.shamash.feature.matchcentre

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import io.shamash.data.Logger
import io.shamash.domain.Match

/**
 * The card's window at the top-right over any app. It never takes focus or touches, so the app underneath keeps the
 * remote. SPEC: MC-04, MC-06
 */
internal class ScoreOverlay(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: ScoreCardView? = null

    fun show(match: Match, card: OverlayCard, now: Long) {
        val shown = view ?: attach() ?: return
        shown.bind(match, card, now)
    }

    fun hide() {
        val shown = view ?: return
        view = null
        runCatching { windows?.removeView(shown) }.onFailure { Logger.w("match overlay", it) }
    }

    private fun attach(): ScoreCardView? {
        if (windows == null || !Settings.canDrawOverlays(context)) return null
        val card = ScoreCardView(context)
        return try {
            windows.addView(card, layoutParams())
            card.also { view = it }
        } catch (e: RuntimeException) {     // permission taken back, or the window manager refused
            Logger.w("match overlay", e)
            null
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val k = context.resources.displayMetrics.widthPixels / DESIGN_WIDTH
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = (MARGIN_X * k).toInt()
            y = (MARGIN_Y * k).toInt()
        }
    }

    private companion object {
        const val DESIGN_WIDTH = 1920f
        const val MARGIN_X = 48f
        const val MARGIN_Y = 40f
    }
}
