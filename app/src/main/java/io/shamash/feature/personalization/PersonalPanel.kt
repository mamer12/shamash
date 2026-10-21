package io.shamash.feature.personalization

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/**
 * Settings -> Personalization: learn my order, muted titles with "Show again", My List with Remove (titles of a
 * removed provider are marked unavailable), and "Clear my history and signals". Saved at once. SPEC: PER-04, PER-05,
 * PER-07, PER-15
 */
@Composable
fun PersonalPanel(locator: ServiceLocator) {
    val personal = locator.personal
    val state by personal.state.collectAsState()
    val data = state.data
    val scope = rememberCoroutineScope()
    val profile by locator.prefs.profile.collectAsState(initial = null)
    val enabled = remember(profile) {
        val p = profile
        if (p == null) emptySet() else locator.providers.filter { runCatching { it.isEnabled(p) }.getOrDefault(false) }.map { it.id }.toSet()
    }
    var sure by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    Note(stringResource(R.string.personal_local))
    ListItem(
        selected = false, onClick = { scope.launch { personal.setLearnOrder(!data.learnOrder) } },
        headlineContent = { Text(stringResource(R.string.personal_learn)) },
        supportingContent = { Text(stringResource(R.string.personal_learn_text)) },
        trailingContent = { Switch(checked = data.learnOrder, onCheckedChange = null) },
    )

    Heading(R.string.personal_list)
    if (data.myList.isEmpty()) Note(stringResource(R.string.personal_list_none))
    data.myList.sortedByDescending { it.addedAt }.forEach { e ->
        val gone = enabled.isNotEmpty() && e.providerId !in enabled
        ListItem(
            selected = false, onClick = { scope.launch { personal.removeMyList(e.itemKey) } },
            headlineContent = { Text(e.title) },
            supportingContent = if (gone) ({ Text(stringResource(R.string.personal_unavailable)) }) else null,
            trailingContent = { Text(stringResource(R.string.personal_remove)) },
        )
    }

    Heading(R.string.personal_muted)
    if (data.mutes.isEmpty()) Note(stringResource(R.string.personal_muted_none))
    data.mutes.sortedByDescending { it.at }.forEach { m ->
        ListItem(
            selected = false, onClick = { scope.launch { personal.unmute(m) } },
            headlineContent = { Text(m.title) },
            supportingContent = if (m.lessLikeThis) ({ Text(stringResource(R.string.personal_muted_less)) }) else null,
            trailingContent = { Text(stringResource(R.string.personal_show_again)) },
        )
    }

    Spacer(Modifier.height(20.dp))
    Button(onClick = {
        if (!sure) sure = true
        else scope.launch { personal.clear(); locator.history.clear(); sure = false; cleared = true }
    }) { Text(stringResource(if (sure) R.string.personal_clear_sure else R.string.personal_clear)) }
    if (cleared) Note(stringResource(R.string.personal_cleared))
}

@Composable
private fun Heading(text: Int) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(text), color = Palette.Ink, fontSize = 22.sp)
}

@Composable
private fun Note(text: String) {
    Text(text, color = Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(860.dp).padding(bottom = 8.dp))
}
