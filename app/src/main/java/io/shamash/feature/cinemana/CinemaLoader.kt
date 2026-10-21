package io.shamash.feature.cinemana

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos

/** Delayed loader, continuously redrawn through its exit fade. SPEC: CIN-05, CIN-06 */
@Composable
fun CinemaLoader(loading: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (loading) { delay(250); visible = true } else visible = false }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "loader fade")
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible, alpha > 0f) {
        if (visible || alpha > 0f) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { elapsed = (it - start) / 1_000_000_000f }
        }
    }
    if (alpha > 0f) Canvas(modifier.size(88.dp)) {
        val radius = size.minDimension / 2
        drawCircle(Color.Black.copy(alpha = .12f * alpha), radius)
        drawCircle(Color.Black.copy(alpha = .15f * alpha), radius * .9f)
        drawCircle(Color.Black.copy(alpha = .24f * alpha), radius * .79f)
        val diameter = radius * 1.12f
        val top = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(Color.White.copy(alpha = .12f * alpha), 0f, 360f, false, top, Size(diameter, diameter), style = stroke)
        val sweep = 30f + 230f * ((1 - cos(elapsed * 2 * Math.PI / 1.5)) / 2).toFloat()
        drawArc(Color(0xFFE50914).copy(alpha = alpha), elapsed * 320f - 90, sweep, false, top,
            Size(diameter, diameter), style = stroke)
    }
}
