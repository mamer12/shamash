package io.shamash.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Switch
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.ContentLevel
import io.shamash.data.softly
import io.shamash.domain.TvApp
import io.shamash.domain.Profile
import io.shamash.feature.tvcontrol.SwitchPanel
import io.shamash.feature.tvcontrol.TvControl
import io.shamash.feature.tvcontrol.rememberSwitchModel
import io.shamash.ui.ChipPicker
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Switch / Original, and a one-time notice when the watchdog went back to Original. SPEC: TVC-05 */
@Composable
internal fun HomePanel() {
    val context = LocalContext.current
    val tv = remember { TvControl.get(context) }
    var watchdog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { watchdog = tv.watchdogRestoredAt() != null }
    if (watchdog) Explain(stringResource(R.string.settings_watchdog))
    val scope = rememberCoroutineScope()
    SwitchPanel(rememberSwitchModel())
    LaunchedEffect(watchdog) { if (watchdog) scope.launch { tv.clearWatchdogNotice() } }
}

/** Redo setup: the first-run questions come back (MainActivity shows setup while `onboarded` is false). SPEC: ONB-01 */
@Composable
internal fun YouPanel(p: Profile, save: (Profile) -> Unit) {
    Explain(stringResource(R.string.settings_redo_text))
    Button(onClick = { save(p.copy(onboarded = false)) }) { Text(stringResource(R.string.settings_redo)) }
}

/** The optional keys; typed text is hidden, saved on Save. SPEC: ONB-02 step 6 */
@Composable
internal fun KeysPanel(p: Profile, save: (Profile) -> Unit) {
    var keys by remember(p) { mutableStateOf(Keys.of(p)) }
    KeyFields(keys) { keys = it }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { save(keys.into(p)) }) { Text(stringResource(R.string.settings_save)) }
}

/** The three key fields, shared with setup. */
@Composable
internal fun KeyFields(keys: Keys, onChange: (Keys) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextInput(
            stringResource(R.string.key_tmdb), keys.tmdb, { onChange(keys.copy(tmdb = it)) },
            secret = true, hint = stringResource(R.string.key_tmdb_where),
        )
        TextInput(stringResource(R.string.key_youtube_id), keys.youtubeId, { onChange(keys.copy(youtubeId = it)) }, secret = true)
        TextInput(
            stringResource(R.string.key_youtube_secret), keys.youtubeSecret, { onChange(keys.copy(youtubeSecret = it)) },
            secret = true, hint = stringResource(R.string.key_youtube_where),
        )
    }
}

/** The keys as edited, trimmed when saved. */
data class Keys(val tmdb: String, val youtubeId: String, val youtubeSecret: String) {
    fun into(p: Profile) = p.copy(tmdbKey = tmdb.trim(), youtubeClientId = youtubeId.trim(), youtubeClientSecret = youtubeSecret.trim())

    companion object {
        fun of(p: Profile) = Keys(p.tmdbKey, p.youtubeClientId, p.youtubeClientSecret)
    }
}

/** The Cinemana add-on and what it may show. SPEC: PRV-14, CIN-08 */
@Composable
internal fun AddonsPanel(p: Profile, save: (Profile) -> Unit) {
    ListItem(
        selected = false, onClick = { save(p.copy(cinemanaAddon = !p.cinemanaAddon)) },
        headlineContent = { Text(stringResource(R.string.settings_cinemana)) },
        supportingContent = { Text(stringResource(R.string.settings_cinemana_text)) },
        trailingContent = { Switch(checked = p.cinemanaAddon, onCheckedChange = null) },
    )
    if (!p.cinemanaAddon) return
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.settings_level), color = Palette.Dim)
    Spacer(Modifier.height(8.dp))
    OnePicker(ContentLevel.entries, p.contentLevel, { save(p.copy(contentLevel = it)) }) { level ->
        stringResource(
            when (level) {
                ContentLevel.ALL -> R.string.level_all
                ContentLevel.FAMILY -> R.string.level_family
                ContentLevel.KIDS -> R.string.level_kids
            }
        )
    }
}

/** Apps not closed when the user goes back Home. SPEC: HOME-15 */
@Composable
internal fun KeepPanel(p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    Explain(stringResource(R.string.settings_keep_text))
    val apps by produceState(emptyList<TvApp>(), locator) {
        value = withContext(Dispatchers.IO) { softly("settings apps", emptyList()) { locator.apps.installed() } }
    }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    ChipPicker(apps.map { it.packageName }, p.keepRunning, { save(p.copy(keepRunning = it)) }) { labels[it] ?: it }
}

/** The TV's own settings app. */
@Composable
internal fun TvPanel() {
    val context = LocalContext.current
    Explain(stringResource(R.string.settings_tv_text))
    Button(onClick = {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { Text(stringResource(R.string.settings_open_tv)) }
}
