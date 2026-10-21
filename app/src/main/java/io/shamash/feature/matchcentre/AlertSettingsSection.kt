package io.shamash.feature.matchcentre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.softly
import io.shamash.domain.MatchFollows
import io.shamash.domain.Profile
import io.shamash.ui.ChipPicker
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The profile's alerts, kept by [AlertStore]: a level per follow, key moments only, quiet hours. Each change is saved
 * at once; a store that can't be read shows the defaults (fail soft). SPEC: MC-11, MC-12, MC-13, MC-16
 */
@Composable
internal fun AlertSettingsSection(p: Profile, locator: ServiceLocator) {
    val store = remember(locator) { AlertStore(locator.prefs) }
    val scope = rememberCoroutineScope()
    var settings by remember(p.id) { mutableStateOf<AlertSettings?>(null) }
    LaunchedEffect(p.id) { settings = softly("match alert settings", AlertSettings()) { store.load() } }
    val now = settings ?: return
    val update: (AlertSettings) -> Unit = { next ->
        settings = next
        scope.launch { softly("match alert settings", Unit) { store.save(next) } }
    }
    PanelHeading(R.string.alerts_title)
    LevelRows(p, now, update)
    PanelNote(stringResource(R.string.mcs_goal_only_text))
    Toggle(R.string.alerts_goal_only, now.goalOnly) { update(now.copy(goalOnly = it)) }
    QuietHoursRows(now.quiet) { update(now.copy(quiet = it)) }
}

/** One row per follow: its name and Off / Kick-off / Goals / All. SPEC: MC-11 */
@Composable
private fun LevelRows(p: Profile, settings: AlertSettings, update: (AlertSettings) -> Unit) {
    val context = LocalContext.current
    val follows = MatchSettingsEdits.alertFollows(p)
    if (follows.isEmpty()) return PanelNote(stringResource(R.string.mcs_alerts_none))
    PanelNote(stringResource(R.string.mcs_alerts_text))
    follows.forEach { f ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val sport = MatchFollows.sportOf(f.name)?.takeIf { f.kind == MatchFollows.SPORT }
            val name = sport?.let { MatchLabels.sport(context, it) } ?: f.name
            Text(name, color = Palette.Ink, fontSize = 18.sp, modifier = Modifier.width(220.dp))
            OnePicker(AlertLevel.entries, settings.levelOf(f), { update(settings.withLevel(f, it)) }) {
                MatchLabels.level(context, it)
            }
        }
    }
}

/** On/off, the start and end moved by half hours (the range may cross midnight), and the days. SPEC: MC-12 */
@Composable
private fun QuietHoursRows(quiet: QuietHours, update: (QuietHours) -> Unit) {
    PanelHeading(R.string.alerts_quiet_hours)
    PanelNote(stringResource(R.string.mcs_quiet_text))
    Toggle(R.string.alerts_quiet_hours, quiet.enabled) { update(quiet.copy(enabled = it)) }
    if (!quiet.enabled) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Edge(R.string.mcs_quiet_from, quiet.startMinute) { update(quiet.copy(startMinute = it)) }
        Edge(R.string.mcs_quiet_to, quiet.endMinute) { update(quiet.copy(endMinute = it)) }
    }
    PanelNote(stringResource(R.string.mcs_days))
    ChipPicker((1..7).toList(), quiet.days, { update(quiet.copy(days = it)) }) {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}

@Composable
private fun Edge(label: Int, minute: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label, MatchSettingsEdits.clock(minute)), color = Palette.Ink, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        OutlinedButton(onClick = { onChange(MatchSettingsEdits.shift(minute, -1)) }) { Text(stringResource(R.string.mcs_earlier)) }
        OutlinedButton(onClick = { onChange(MatchSettingsEdits.shift(minute, 1)) }) { Text(stringResource(R.string.mcs_later)) }
    }
}

@Composable
private fun Toggle(label: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    OutlinedButton(onClick = { onChange(!on) }) {
        Text(stringResource(R.string.mcs_toggle, stringResource(label), stringResource(if (on) R.string.mcs_on else R.string.mcs_off)))
    }
}
