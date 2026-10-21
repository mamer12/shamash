package io.shamash.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** How things move: one critically damped spring shared by growth and scrolling. SPEC: HOME-12, HOME-17 */
object Motion {
    /** A spring that settles in about 0.34 s without overshoot: stiffness = (2π / 0.34)². */
    const val STIFFNESS = 341.5f
    const val FOCUS_SCALE = 1.08f
    val BORDER = 3.dp

    fun <T> pace(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = STIFFNESS)
}

/**
 * A focusable, clickable card: grows 8% with a white 3 dp border on focus, on a spring (fast presses retarget it, no
 * jumps). The growth is read only while drawing, so focus motion never recomposes the content. SPEC: HOME-12
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.Radius),
    corner: Dp = Dimens.Radius,
    scale: Float = Motion.FOCUS_SCALE,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow = animateFloatAsState(if (focused) 1f else 0f, Motion.pace(), label = "focus")
    val density = LocalDensity.current
    val stroke = remember(density) { Stroke(with(density) { Motion.BORDER.toPx() }) }
    val radius = with(density) { corner.toPx() }
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val s = 1f + (scale - 1f) * grow.value
                scaleX = s
                scaleY = s
            }
            .drawWithContent {
                drawContent()
                val g = grow.value
                if (g > 0.01f) drawFocusRing(g, radius, stroke)
            }
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content(focused) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusRing(alpha: Float, radius: Float, stroke: Stroke) {
    val inset = stroke.width / 2
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = Offset(-inset, -inset),
        size = Size(size.width + stroke.width, size.height + stroke.width),
        cornerRadius = CornerRadius(radius + inset),
        style = stroke,
    )
}

/**
 * Scrolls so the focused child's leading edge sits at [startPx] (the margin): rows keep the focused card at the left,
 * the rows list keeps the focused row under the top bar. SPEC: HOME-17
 */
@OptIn(ExperimentalFoundationApi::class)
class AlignStart(private val startPx: Float) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = Motion.pace()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - startPx
}

@Composable
fun rememberAlignStart(start: Dp): AlignStart {
    val density = LocalDensity.current
    return remember(density, start) { AlignStart(with(density) { start.toPx() }) }
}
