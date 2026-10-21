package io.shamash.feature.radio

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
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.radio.RadioProvider
import io.shamash.data.radio.Station
import io.shamash.data.softly
import io.shamash.domain.Profile
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Favorites: search stations by name (in the chosen country), switch each on or off; favorites listed below. SPEC: PEX-24 */
@Composable
internal fun RadioPanel(p: Profile, locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == RadioProvider.ID } as? RadioProvider } ?: return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(emptyList<Station>()) }
    var favorites by remember { mutableStateOf(emptyList<Station>()) }
    LaunchedEffect(provider, p.id) { favorites = provider.favorites() }
    LaunchedEffect(provider, query) {
        val q = query.trim()
        if (q.length < 2) { found = emptyList(); return@LaunchedEffect }
        delay(SEARCH_DELAY_MS)
        found = softly("radio search", emptyList()) { provider.find(q, p) }
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        TextInput(stringResource(R.string.more_radio_search), query, { query = it }, width = 480.dp)
        (found + favorites.filter { f -> found.none { it.id == f.id } }).forEach { s ->
            val on = favorites.any { it.id == s.id }
            ListItem(
                selected = false,
                onClick = { scope.launch { favorites = provider.toggleFavorite(s) } },
                headlineContent = { Text(s.name) },
                supportingContent = { if (s.tags.isNotEmpty()) Text(s.tags) },
                trailingContent = { Switch(checked = on, onCheckedChange = null) },
            )
        }
        Text(stringResource(R.string.more_radio_fav_count).format(favorites.size), color = Palette.Dim, modifier = Modifier.padding(top = 8.dp))
    }
}

private const val SEARCH_DELAY_MS = 400L
