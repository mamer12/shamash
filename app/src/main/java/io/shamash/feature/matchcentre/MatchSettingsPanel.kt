package io.shamash.feature.matchcentre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.Profile
import io.shamash.domain.Sport
import io.shamash.ui.ChipPicker
import io.shamash.ui.Palette
import io.shamash.ui.TextInput

/**
 * Settings › Matches: who and what the profile follows (teams and players, whole sports, competitions by sport), then
 * its alerts (levels, key moments only, quiet hours). Everything stays on the TV. SPEC: MC-07, MC-09, MC-11..13, MC-16
 */
@Composable
fun MatchSettingsPanel(p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelNote(stringResource(R.string.mcs_text))
        FollowsSection(p, save)
        PanelHeading(R.string.mcs_competitions)
        CompetitionChips(p.competitions, { save(p.copy(competitions = it)) })
        AlertSettingsSection(p, locator)
    }
}

/** Teams and players typed by name (saved on Save), then whole sports as chips (saved at once). SPEC: MC-09 */
@Composable
private fun FollowsSection(p: Profile, save: (Profile) -> Unit) {
    val context = LocalContext.current
    var text by remember(p.id) { mutableStateOf(MatchSettingsEdits.followsText(p)) }
    PanelHeading(R.string.mcs_follows)
    TextInput(
        stringResource(R.string.mcs_follows_label), text, { text = it },
        hint = stringResource(R.string.mcs_follows_hint), width = 760.dp,
    )
    Button(onClick = { save(MatchSettingsEdits.withFollowsText(p, text)) }) { Text(stringResource(R.string.settings_save)) }
    PanelHeading(R.string.mcs_sports)
    val followed = Sport.entries.filterTo(HashSet()) { MatchSettingsEdits.followsSport(p, it) }
    ChipPicker(Sport.entries, followed, { chosen ->
        val changed = Sport.entries.filter { (it in chosen) != (it in followed) }
        save(changed.fold(p) { acc, s -> MatchSettingsEdits.withSport(acc, s, s in chosen) })
    }) { MatchLabels.sport(context, it) }
}

@Composable
internal fun PanelHeading(text: Int) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(text), color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun PanelNote(text: String) {
    Text(text, color = Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(860.dp).padding(bottom = 4.dp))
}
