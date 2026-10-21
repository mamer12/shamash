package io.shamash.feature.iptv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.iptv.Channel
import io.shamash.data.iptv.IptvProvider
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.launch

/** Marking channels as favorites: search the playlist by name, then switch each on or off. SPEC: PEX-14 */
@Composable
internal fun IptvFavoritesPanel(locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == IptvProvider.ID } as? IptvProvider } ?: return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<Channel>()) }
    var favorites by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(provider) { favorites = provider.favorites().toSet() }
    LaunchedEffect(provider, query) {
        val q = query.trim()
        matches = if (q.length < 2) emptyList()
        else provider.playlist()?.channels.orEmpty().filter { it.name.contains(q, ignoreCase = true) }.take(MAX_MATCHES)
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(R.string.pex_iptv_fav_title), color = Palette.Dim)
        TextInput(label = stringResource(R.string.pex_iptv_fav_search), value = query, onChange = { query = it }, width = 480.dp)
        Spacer(Modifier.height(8.dp))
        matches.forEach { c ->
            ListItem(
                selected = false,
                onClick = { scope.launch { favorites = provider.toggleFavorite(c.id).toSet() } },
                headlineContent = { Text(c.name) },
                supportingContent = { if (c.group.isNotEmpty()) Text(c.group) },
                trailingContent = { Switch(checked = c.id in favorites, onCheckedChange = null) },
            )
        }
        Text(stringResource(R.string.pex_iptv_fav_count).format(favorites.size), color = Palette.Dim, modifier = Modifier.padding(top = 8.dp))
    }
}

private const val MAX_MATCHES = 8
