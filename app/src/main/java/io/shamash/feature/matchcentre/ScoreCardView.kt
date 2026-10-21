package io.shamash.feature.matchcentre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.shamash.R
import io.shamash.domain.Match
import kotlin.math.ceil

/**
 * The corner card over any app: crests and "starts in 10 min" before kick-off; crests, score and the minute in red
 * while live; gold "GOAL!" for a moment after a goal; the final score at full time. Drawn by hand, sized for a 1920
 * wide screen and scaled. SPEC: MC-04, MC-05
 */
internal class ScoreCardView(context: Context) : View(context) {
    private val k = resources.displayMetrics.widthPixels / DESIGN_WIDTH
    private val crestPx = (CREST * k).toInt()
    private val crests = HashMap<String, Bitmap>()
    private val box = RectF()
    private val crestBox = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crestPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val big = textPaint(Typeface.DEFAULT_BOLD, BIG_TEXT * k)
    private val small = textPaint(Typeface.create(Typeface.DEFAULT, Typeface.BOLD), SMALL_TEXT * k)
    private var match: Match? = null
    private var card = OverlayCard.LIVE
    private var now = 0L

    fun bind(match: Match, card: OverlayCard, now: Long) {
        val sizeChanged = card != this.card || this.match == null
        this.match = MatchSides.forDirection(match, context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL)
        this.card = card
        this.now = now
        loadCrests(match)
        if (sizeChanged) requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (card == OverlayCard.REMINDER) REMINDER_WIDTH else SCORE_WIDTH
        val height = if (card == OverlayCard.GOAL) GOAL_HEIGHT else HEIGHT
        setMeasuredDimension((width * k).toInt(), (height * k).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val m = match ?: return
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        fill.color = if (card == OverlayCard.GOAL) GOLD else CARD
        canvas.drawRoundRect(box, RADIUS * k, RADIUS * k, fill)
        if (card == OverlayCard.REMINDER) drawReminder(canvas, m) else drawScore(canvas, m)
    }

    private fun drawReminder(canvas: Canvas, m: Match) {
        val cy = height / 2f
        drawCrest(canvas, m.home.logoUrl, CREST_INSET * k, cy)
        drawCrest(canvas, m.away.logoUrl, (CREST_INSET + CREST + CREST_GAP) * k, cy)
        val x = REMINDER_TEXT_X * k
        val minutes = ceil((m.startsAt - now).coerceAtLeast(0) / MINUTE).toInt()
        small.color = DIM
        canvas.drawText(
            context.getString(R.string.match_starts_in, minutes, context.getString(R.string.match_default_app)),
            x, cy - 10 * k, small,
        )
        big.color = INK
        big.textSize = REMINDER_TEXT * k
        canvas.drawText(context.getString(R.string.match_versus, m.home.shortName, m.away.shortName), x, cy + 28 * k, big)
        big.textSize = BIG_TEXT * k
    }

    private fun drawScore(canvas: Canvas, m: Match) {
        val goal = card == OverlayCard.GOAL
        val cy = if (goal) GOAL_ROW_Y * k else height / 2f
        if (goal) {
            big.color = ON_GOLD
            canvas.drawText(context.getString(R.string.match_goal), 32 * k, 52 * k, big)
        }
        drawCrest(canvas, m.home.logoUrl, CREST_INSET * k, cy)
        drawCrest(canvas, m.away.logoUrl, width - CREST_INSET * k, cy)
        val score = context.getString(R.string.match_score, m.home.score.ifEmpty { "0" }, m.away.score.ifEmpty { "0" })
        big.color = if (goal) ON_GOLD else INK
        drawCentered(canvas, score, cy + 6 * k, big)
        val note = if (card == OverlayCard.FINAL) context.getString(R.string.match_full_time) else LIVE_DOT + m.clock
        small.color = when (card) {
            OverlayCard.GOAL -> ON_GOLD_DIM
            OverlayCard.FINAL -> DIM
            else -> LIVE_RED
        }
        drawCentered(canvas, note, cy + 38 * k, small)
    }

    private fun drawCentered(canvas: Canvas, text: String, y: Float, paint: Paint) =
        canvas.drawText(text, width / 2f - paint.measureText(text) / 2f, y, paint)

    private fun drawCrest(canvas: Canvas, url: String, cx: Float, cy: Float) {
        val bitmap = crests[url] ?: return
        val half = crestPx / 2f
        crestBox.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawBitmap(bitmap, null, crestBox, crestPaint)
    }

    /** Keeps only this match's two crests; the image loader fits them to the card's size. */
    private fun loadCrests(m: Match) {
        val wanted = setOf(m.home.logoUrl, m.away.logoUrl).filter { it.isNotBlank() }
        crests.keys.retainAll(wanted.toSet())  // a new match: the old crests go
        wanted.filterNot(crests::containsKey).forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(crestPx)
                .allowHardware(false)
                .target(onSuccess = { image -> crests[url] = image.toBitmap(); invalidate() })
                .build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    private fun textPaint(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        color = INK
    }

    private companion object {
        const val DESIGN_WIDTH = 1920f
        const val REMINDER_WIDTH = 640f
        const val SCORE_WIDTH = 420f
        const val HEIGHT = 112f
        const val GOAL_HEIGHT = 150f
        const val GOAL_ROW_Y = 92f
        const val RADIUS = 28f
        const val CREST = 56f
        const val CREST_INSET = 52f
        const val CREST_GAP = 8f
        const val REMINDER_TEXT_X = 160f
        const val BIG_TEXT = 40f
        const val REMINDER_TEXT = 30f
        const val SMALL_TEXT = 22f
        const val MINUTE = 60_000.0
        const val LIVE_DOT = "● "
        const val CARD = 0xEB0A0C1E.toInt()
        const val GOLD = 0xF2F4C450.toInt()
        const val INK = 0xFFF5F0E6.toInt()
        const val DIM = 0xCCF5F0E6.toInt()
        const val ON_GOLD = 0xFF0A0C1E.toInt()
        const val ON_GOLD_DIM = 0xCC0A0C1E.toInt()
        const val LIVE_RED = 0xFFFF453A.toInt()
    }
}
