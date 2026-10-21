package io.shamash.feature.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.MyListOps
import io.shamash.domain.OpenTarget
import io.shamash.domain.RankedOffer
import io.shamash.domain.Reasons
import io.shamash.domain.Signal
import io.shamash.domain.SignalKind
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

private enum class Page { MENU, WHY, WHERE }

/**
 * The card menu opened by a long OK: My List, Not for you, Less like this, why it is shown and where to watch it.
 * Every action is saved at once and the menu closes. SPEC: PER-07, PER-10, PER-12, PER-14
 */
@Composable
fun ItemMenu(locator: ServiceLocator, item: MediaItem, onClose: () -> Unit) {
    val personal = locator.personal
    val state by personal.state.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.MENU) }
    val first = remember(page) { FocusRequester() }
    LaunchedEffect(page) { runCatching { first.requestFocus() } }
    val act: (suspend () -> Unit) -> Unit = { work -> scope.launch { work(); onClose() } }
    val lookup = item.open as? OpenTarget.Lookup

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(560.dp).background(Palette.Dusk, RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.title, color = Palette.Ink, fontSize = 24.sp, maxLines = 2)
            when (page) {
                Page.MENU -> {
                    val inList = MyListOps.contains(state.data.myList, item)
                    Entry(if (inList) R.string.personal_remove_list else R.string.personal_add_list, Modifier.focusRequester(first)) {
                        act { personal.toggleMyList(item) }
                    }
                    if (item.kind != Kind.MATCH) {
                        Entry(R.string.personal_not_for_you) { act { personal.mute(item, lessLikeThis = false) } }
                        Entry(R.string.personal_less_like) { act { personal.mute(item, lessLikeThis = true) } }
                        Entry(R.string.personal_why) { page = Page.WHY }
                    }
                    if (lookup != null) Entry(R.string.personal_where) { page = Page.WHERE }
                    Entry(R.string.personal_close) { onClose() }
                }
                Page.WHY -> Why(item, state.data.signals, first, onMute = { act { personal.mute(item, lessLikeThis = false) } }, onBack = { page = Page.MENU })
                Page.WHERE -> if (lookup != null) Where(locator, item, lookup, first, onClose) { page = Page.MENU }
            }
        }
    }
}

@Composable
private fun Entry(text: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(selected = false, onClick = onClick, headlineContent = { Text(stringResource(text)) }, modifier = modifier)
}

@Composable
private fun Why(item: MediaItem, signals: List<Signal>, first: FocusRequester, onMute: () -> Unit, onBack: () -> Unit) {
    val used = remember(item, signals) { Reasons.evidence(item, signals) }
    if (used.isEmpty()) Text(stringResource(R.string.personal_why_none), color = Palette.Dim, fontSize = 18.sp)
    used.forEach { s ->
        val res = when (s.kind) {
            SignalKind.FINISH -> R.string.personal_why_finished
            SignalKind.LIKE -> R.string.personal_why_liked
            else -> R.string.personal_why_opened
        }
        Text(stringResource(res, s.title), color = Palette.Ink, fontSize = 20.sp, maxLines = 1)
    }
    Entry(R.string.personal_not_for_you, Modifier.focusRequester(first), onMute)
    Entry(R.string.personal_close, onClick = onBack)
}

@Composable
private fun Where(
    locator: ServiceLocator, item: MediaItem, lookup: OpenTarget.Lookup, first: FocusRequester,
    onClose: () -> Unit, onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offers by produceState<List<RankedOffer>?>(null, lookup) { value = locator.opener.where?.ranked(lookup).orEmpty() }
    val list = offers
    LaunchedEffect(list) { runCatching { first.requestFocus() } }
    if (list == null) Text(stringResource(R.string.personal_looking), color = Palette.Dim, fontSize = 18.sp)
    else if (list.isEmpty()) Text(stringResource(R.string.personal_nowhere), color = Palette.Dim, fontSize = 18.sp)
    list.orEmpty().forEachIndexed { i, ranked ->
        val name = stringResource(R.string.personal_open_in, ranked.offer.name)
        ListItem(
            selected = false,
            onClick = {
                scope.launch { locator.opener.open(item.copy(open = lookup.copy(appPackage = ranked.offer.appPackage)), pickApp = false); onClose() }
            },
            headlineContent = { Text(name) },
            supportingContent = if (ranked.inYourApps) null else ({ Text(stringResource(R.string.personal_not_in_apps)) }),
            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
        )
    }
    Entry(R.string.personal_close, if (list.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier, onBack)
}
