package io.shamash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.shamash.domain.MediaItem

/** Opens the card menu (Add to My List, Not for you...) for a title; null where there is no menu. SPEC: PER-07 */
val LocalItemMenu = staticCompositionLocalOf<((MediaItem) -> Unit)?> { null }

private class Held { var down = false }

/**
 * A long OK press on this card opens the card menu; the release after it is swallowed so the card does not also open.
 * A short press is untouched. SPEC: PER-07
 */
@Composable
fun Modifier.itemMenu(item: MediaItem): Modifier {
    val open = LocalItemMenu.current ?: return this
    val held = remember { Held() }
    return onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        when {
            !ok -> false
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 -> {
                if (!held.down) { held.down = true; open(item) }
                true
            }
            e.type == KeyEventType.KeyUp && held.down -> { held.down = false; true }
            else -> false
        }
    }
}
