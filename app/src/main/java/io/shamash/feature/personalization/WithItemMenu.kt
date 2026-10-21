package io.shamash.feature.personalization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.shamash.app.ServiceLocator
import io.shamash.domain.MediaItem
import io.shamash.ui.LocalItemMenu

/** Gives every card inside [content] the long-OK menu and shows it when asked. Guest and Kids profiles have none. SPEC: PER-07 */
@Composable
fun WithItemMenu(locator: ServiceLocator, content: @Composable () -> Unit) {
    var item by remember { mutableStateOf<MediaItem?>(null) }
    val open: (MediaItem) -> Unit = { item = it }
    CompositionLocalProvider(LocalItemMenu provides open) { content() }
    item?.let { ItemMenu(locator, it) { item = null } }
}
