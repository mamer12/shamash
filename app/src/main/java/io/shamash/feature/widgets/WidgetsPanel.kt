package io.shamash.feature.widgets

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
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.HttpPolicy
import io.shamash.data.ServerUrl
import io.shamash.data.widgets.TileType
import io.shamash.data.widgets.WidgetConfig
import io.shamash.data.widgets.WidgetsProvider
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.launch

/** The "Today" row: which symbols, which news feeds (https), and which tile types show. SPEC: PEX-27 */
@Composable
internal fun WidgetsPanel(locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == WidgetsProvider.ID } as? WidgetsProvider } ?: return
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<WidgetConfig?>(null) }
    var symbols by remember { mutableStateOf("") }
    var feeds by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.more_w_saved)
    LaunchedEffect(provider) {
        val c = provider.config()
        config = c
        symbols = c.symbols.joinToString(", ")
        feeds = c.feeds.joinToString(", ")
    }
    val c = config ?: return
    fun save(next: WidgetConfig) = scope.launch { config = provider.save(next); status = saved }
    Column(Modifier.padding(bottom = 12.dp)) {
        TileType.entries.forEach { type ->
            ListItem(
                selected = false,
                onClick = { save(c.copy(hidden = if (c.shows(type)) c.hidden + type.key else c.hidden - type.key)) },
                headlineContent = { Text(stringResource(label(type))) },
                trailingContent = { Switch(checked = c.shows(type), onCheckedChange = null) },
            )
        }
        TextInput(stringResource(R.string.more_w_symbols), symbols, { symbols = it }, width = 860.dp, hint = stringResource(R.string.more_w_symbols_hint))
        TextInput(stringResource(R.string.more_w_feeds), feeds, { feeds = it }, width = 860.dp, hint = stringResource(R.string.more_w_feeds_hint), modifier = Modifier.padding(top = 12.dp))
        Button(onClick = {
            val list = feeds.split(',', ' ', '\n').map { it.trim() }.filter { ServerUrl.parse(it, HttpPolicy.NONE) != null }
            save(c.copy(symbols = symbols.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }, feeds = list))
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.more_w_save)) }
        status?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
    }
}

private fun label(type: TileType) = when (type) {
    TileType.CRYPTO -> R.string.more_w_type_crypto
    TileType.FX -> R.string.more_w_type_fx
    TileType.NEWS -> R.string.more_w_type_news
}
