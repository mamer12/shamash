package io.shamash.feature.podcasts

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
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Redact
import io.shamash.data.podcasts.PodcastProvider
import io.shamash.data.podcasts.Subscription
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.launch

/**
 * The shows a profile follows: add one by its feed address, import an OPML export from a podcast app (by address),
 * remove one with OK. Errors are not shown as they are (they may carry an address). SPEC: PEX-23
 */
@Composable
internal fun PodcastsPanel(locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == PodcastProvider.ID } as? PodcastProvider } ?: return
    val scope = rememberCoroutineScope()
    var subs by remember { mutableStateOf(emptyList<Subscription>()) }
    var address by remember { mutableStateOf("") }
    var opml by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val added = stringResource(R.string.more_pod_added)
    val failed = stringResource(R.string.more_pod_failed)
    val imported = stringResource(R.string.more_pod_opml_done)
    LaunchedEffect(provider) { subs = provider.subscriptions() }
    Column(Modifier.padding(bottom = 12.dp)) {
        TextInput(stringResource(R.string.more_pod_add), address, { address = it }, hint = stringResource(R.string.more_pod_add_hint))
        Button(onClick = {
            scope.launch {
                status = try {
                    added.format(provider.subscribe(address).title).also { address = "" }
                } catch (e: Exception) { failed }
                subs = provider.subscriptions()
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.more_pod_add_button)) }
        Spacer(Modifier.height(12.dp))
        TextInput(stringResource(R.string.more_pod_opml), opml, { opml = it }, hint = stringResource(R.string.more_pod_opml_hint))
        Button(onClick = {
            scope.launch {
                status = try { imported.format(provider.importOpml(opml)).also { opml = "" } } catch (e: Exception) { failed }
                subs = provider.subscriptions()
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.more_pod_opml_button)) }
        status?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.more_pod_list_title).format(subs.size), color = Palette.Dim)
        subs.forEach { s ->
            ListItem(
                selected = false,
                onClick = { scope.launch { provider.unsubscribe(s.url); subs = provider.subscriptions() } },
                headlineContent = { Text(s.title.ifEmpty { Redact.host(s.url) }) },
                supportingContent = { Text(stringResource(R.string.more_pod_remove)) },
            )
        }
    }
}
