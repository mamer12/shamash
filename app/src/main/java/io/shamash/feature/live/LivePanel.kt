package io.shamash.feature.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.live.LiveFollow
import io.shamash.data.live.LiveProvider
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.launch

/** Who to follow: "twitch:name, kick:name" or channel addresses, comma separated. SPEC: PEX-25 */
@Composable
internal fun LivePanel(locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == LiveProvider.ID } as? LiveProvider } ?: return
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.more_live_saved)
    val skipped = stringResource(R.string.more_live_bad)
    LaunchedEffect(provider) { text = provider.follows().joinToString(", ") }
    Column(Modifier.padding(bottom = 12.dp)) {
        TextInput(stringResource(R.string.more_live_follows), text, { text = it }, width = 860.dp, hint = stringResource(R.string.more_live_follows_hint))
        Button(onClick = {
            scope.launch {
                val parts = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                val good = parts.mapNotNull { LiveFollow.parse(it) }
                val kept = provider.saveFollows(good)
                val bad = parts.filter { LiveFollow.parse(it) == null }
                text = kept.joinToString(", ")
                status = saved.format(kept.size) + if (bad.isEmpty()) "" else " " + skipped.format(bad.joinToString(", ").take(80))
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.more_live_save)) }
        status?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
    }
}
