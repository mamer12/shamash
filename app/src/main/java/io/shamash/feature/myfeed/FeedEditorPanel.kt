package io.shamash.feature.myfeed

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
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.HttpPolicy
import io.shamash.data.ServerUrl
import io.shamash.data.myfeed.FeedStore
import io.shamash.data.myfeed.JsonMapping
import io.shamash.data.myfeed.MyFeedProvider
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.ContentLevel
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import io.shamash.domain.FeedKind
import io.shamash.domain.FeedStyle
import kotlinx.coroutines.launch

/** What the user is typing for a new feed. */
private data class Draft(
    val title: String = "",
    val url: String = "",
    val kind: FeedKind = FeedKind.RSS,
    val style: FeedStyle = FeedStyle.WIDE,
    val level: ContentLevel = ContentLevel.ALL,
    val allowHttp: Boolean = false,
    val auth: String = "",
    val map: JsonMapping = JsonMapping(),
) {
    fun toFeed(id: String) = UserFeed(
        id, title.trim().ifEmpty { id }, url.trim(), kind, if (kind == FeedKind.JSON) map else null, style, level, allowHttp, auth.trim(),
    )

    /** Whether the address can be used with the chosen http switch. */
    fun addressOk() = ServerUrl.parse(url, if (allowHttp) HttpPolicy.ANY else HttpPolicy.NONE) != null
}

/**
 * The feeds list and the form to add one: address, kind, card style, who it suits, optional auth header, and for JSON
 * the mapping with a "Try it" that shows the first three cards. SPEC: PEX-20, PEX-21, PEX-22
 */
@Composable
internal fun FeedEditorPanel(locator: ServiceLocator) {
    val provider = remember(locator) { locator.providers.firstOrNull { it.id == MyFeedProvider.ID } as? MyFeedProvider } ?: return
    val scope = rememberCoroutineScope()
    var feeds by remember { mutableStateOf(emptyList<UserFeed>()) }
    var draft by remember { mutableStateOf(Draft()) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(provider) { feeds = provider.feeds() }
    val badUrl = stringResource(R.string.pex_feed_bad_url)
    val tryFailed = stringResource(R.string.pex_feed_try_failed)
    val full = stringResource(R.string.pex_feed_max).format(FeedStore.MAX)
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(R.string.pex_feed_list), color = Palette.Dim)
        feeds.forEach { f ->
            ListItem(
                selected = false,
                onClick = { scope.launch { feeds = provider.save(feeds - f) } },
                headlineContent = { Text(f.title) },
                supportingContent = { Text(stringResource(R.string.pex_feed_remove)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pex_feed_add), color = Palette.Dim)
        TextInput(stringResource(R.string.pex_feed_title), draft.title, { draft = draft.copy(title = it) }, width = 640.dp)
        TextInput(stringResource(R.string.pex_feed_url), draft.url, { draft = draft.copy(url = it) }, width = 640.dp)
        Spacer(Modifier.height(8.dp))
        OnePicker(FeedKind.entries, draft.kind, { draft = draft.copy(kind = it) }) { it.name }
        Spacer(Modifier.height(8.dp))
        OnePicker(FeedStyle.entries, draft.style, { draft = draft.copy(style = it) }) {
            stringResource(when (it) { FeedStyle.WIDE -> R.string.pex_style_wide; FeedStyle.VIDEO -> R.string.pex_style_video; FeedStyle.POSTER -> R.string.pex_style_poster })
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pex_feed_level), color = Palette.Dim)
        OnePicker(ContentLevel.entries, draft.level, { draft = draft.copy(level = it) }) {
            stringResource(when (it) { ContentLevel.ALL -> R.string.level_all; ContentLevel.FAMILY -> R.string.level_family; ContentLevel.KIDS -> R.string.level_kids })
        }
        ListItem(
            selected = false, onClick = { draft = draft.copy(allowHttp = !draft.allowHttp) },
            headlineContent = { Text(stringResource(R.string.pex_feed_http)) },
            trailingContent = { Switch(checked = draft.allowHttp, onCheckedChange = null) },
        )
        TextInput(stringResource(R.string.pex_feed_auth), draft.auth, { draft = draft.copy(auth = it) }, secret = true, width = 640.dp,
            hint = stringResource(R.string.pex_feed_auth_hint))
        if (draft.kind == FeedKind.JSON) MappingForm(draft.map) { draft = draft.copy(map = it) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                note = try {
                    provider.preview(draft.toFeed("try")).joinToString("\n") { "• " + it.title }.ifEmpty { tryFailed }
                } catch (e: Exception) {
                    tryFailed   // not shown: the error may carry an address
                }
            }
        }) { Text(stringResource(R.string.pex_feed_try)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            note = when {
                feeds.size >= FeedStore.MAX -> full
                !draft.addressOk() -> badUrl
                else -> {
                    val feed = draft.toFeed("f" + System.currentTimeMillis().toString(36))
                    scope.launch { feeds = provider.save(feeds + feed) }
                    draft = Draft()
                    null
                }
            }
        }) { Text(stringResource(R.string.pex_feed_save)) }
        note?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun MappingForm(m: JsonMapping, change: (JsonMapping) -> Unit) {
    TextInput(stringResource(R.string.pex_map_items), m.items, { change(m.copy(items = it)) }, width = 480.dp)
    TextInput(stringResource(R.string.pex_map_title), m.title, { change(m.copy(title = it)) }, width = 480.dp)
    TextInput(stringResource(R.string.pex_map_link), m.link, { change(m.copy(link = it)) }, width = 480.dp)
    TextInput(stringResource(R.string.pex_map_image), m.image, { change(m.copy(image = it)) }, width = 480.dp)
    TextInput(stringResource(R.string.pex_map_subtitle), m.subtitle, { change(m.copy(subtitle = it)) }, width = 480.dp)
    TextInput(stringResource(R.string.pex_map_date), m.date, { change(m.copy(date = it)) }, width = 480.dp)
}
