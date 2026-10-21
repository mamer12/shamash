package io.shamash.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.Profile
import io.shamash.feature.profiles.ProfilesPanel
import io.shamash.feature.tvcontrol.TvControl
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/** The Settings sections, in menu order. Remote buttons show only on TVs that have them. SPEC: ONB-06, TVC-05 */
enum class Section(val title: Int) {
    HOME(R.string.settings_home),
    LAYOUT(R.string.settings_layout),
    PERSONAL(R.string.personal_section),
    MATCHES(R.string.mcs_section),   // SPEC: MC-07, MC-11..13
    ACCESSIBILITY(R.string.settings_a11y),
    YOU(R.string.settings_you),
    PROFILES(R.string.settings_profiles),
    YOUTUBE(R.string.settings_youtube),
    PHONE(R.string.settings_phone),
    ADDONS(R.string.settings_addons),
    BUTTONS(R.string.settings_buttons),
    KEEP(R.string.settings_keep),
    BACKUP(R.string.settings_backup),
    TV(R.string.settings_tv),
    ;

    companion object {
        /** The section a link names ("settings?section=layout"), or null. */
        fun named(name: String?): Section? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Settings: a menu on the start side, the focused section's panel beside it. Every change is saved at once (keys on
 * Save). Back leaves.
 */
@Composable
fun SettingsScreen(locator: ServiceLocator, onBack: () -> Unit, start: Section? = null) {
    BackHandler(onBack = onBack)
    val profile by locator.prefs.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(start ?: Section.HOME) }
    val startFocus = remember { FocusRequester() }
    val sections = remember {
        Section.entries.filter { it != Section.BUTTONS || TvControl.get(locator.appContext).remoteButtonsSupported }
    }
    val save: (Profile) -> Unit = { p -> scope.launch { locator.prefs.save(p) } }

    Row(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Palette.Dusk, Palette.Night)))
            .padding(horizontal = 56.dp, vertical = 48.dp),
    ) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(
                stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink, modifier = Modifier.padding(start = 16.dp, bottom = 24.dp),
            )
            sections.forEach { s ->
                ListItem(
                    selected = s == section, onClick = { section = s },
                    headlineContent = { Text(stringResource(s.title)) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) section = s }
                        .then(if (s == start) Modifier.focusRequester(startFocus) else Modifier),
                )
            }
            if (start != null) LaunchedEffect(start) { runCatching { startFocus.requestFocus() } }   // LAY-02 shortcut
        }
        Spacer(Modifier.width(56.dp))
        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            val p = profile ?: return@Box
            Panel(section, p, save, locator)
        }
    }
}

@Composable
private fun Panel(section: Section, p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(section.title), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Spacer(Modifier.height(16.dp))
        when (section) {
            Section.HOME -> HomePanel()
            Section.LAYOUT -> io.shamash.feature.layout.LayoutPanel(p, save, locator)
            Section.ACCESSIBILITY -> io.shamash.feature.accessibility.A11yPanel()
            Section.PERSONAL -> io.shamash.feature.personalization.PersonalPanel(locator)
            Section.MATCHES -> io.shamash.feature.matchcentre.MatchSettingsPanel(p, save, locator)
            Section.YOU -> YouPanel(p, save)
            Section.PROFILES -> ProfilesPanel(locator)
            Section.YOUTUBE -> YouTubePanel(p, locator)
            Section.PHONE -> PhonePanel(locator)
            Section.ADDONS -> AddonsPanel(p, save, locator)
            Section.BUTTONS -> ButtonsPanel(locator)
            Section.KEEP -> KeepPanel(p, save, locator)
            Section.BACKUP -> io.shamash.feature.backup.BackupPanel(locator)
            Section.TV -> TvPanel()
        }
    }
}

/** Secondary text under a panel's title. */
@Composable
internal fun Explain(text: String) {
    Text(text, color = Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(860.dp).padding(bottom = 16.dp))
}
