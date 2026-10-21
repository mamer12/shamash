package io.shamash.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import io.shamash.data.Sources
import io.shamash.domain.AddOn
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/**
 * Settings -> Add-ons: every registered add-on with its on/off switch; an add-on that is on shows its own settings
 * ([ProviderSettingsPanel]). SPEC: PEX-02, PEX-05, PEX-08
 */
@Composable
internal fun AddonsPanel(p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    Explain(stringResource(R.string.addons_intro))
    Sources.addOns.forEach { addOn ->
        val on = Sources.addOnRegistry.isOn(addOn, p)
        ListItem(
            selected = false, onClick = { save(p.withAddon(addOn.id, !on)) },
            headlineContent = { Text(stringResource(addOn.nameRes)) },
            supportingContent = { Text(stringResource(addOn.descRes)) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
        )
        if (on) {
            ProviderSettingsPanel(addOn, p, save, locator) {
                when (addOn.id) {
                    "cinemana" -> CinemanaLevel(p, save)
                    "plex" -> io.shamash.feature.plex.PlexExtra(p, locator)
                    "iptv" -> io.shamash.feature.iptv.IptvFavoritesPanel(locator)
                    "myfeed" -> io.shamash.feature.myfeed.FeedEditorPanel(locator)
                    "podcasts" -> io.shamash.feature.podcasts.PodcastsPanel(locator)
                    "radio" -> io.shamash.feature.radio.RadioPanel(p, locator)
                    "live" -> io.shamash.feature.live.LivePanel(locator)
                    "trakt", "simkl" -> io.shamash.feature.trakt.TrackerPanel(addOn.id, p, locator)
                    "widgets" -> io.shamash.feature.widgets.WidgetsPanel(locator)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Explain(stringResource(R.string.addons_guide))
    Explain(stringResource(R.string.addons_guide_link, stringResource(R.string.addons_guide_url)))   // PEX-08
}

/** What Cinemana may show (CIN-08). */
@Composable
private fun CinemanaLevel(p: Profile, save: (Profile) -> Unit) {
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

/** Remove: forgets the credentials and row choices, turns the add-on off. SPEC: PEX-05 */
internal fun removed(p: Profile, addOn: AddOn): Profile =
    io.shamash.domain.RowSources.cleared(p.withAddon(addOn.id, false), addOn.id)

@Composable
internal fun RemoveButton(addOn: AddOn, p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    val scope = rememberCoroutineScope()
    Explain(stringResource(R.string.addon_remove_text))
    Button(onClick = {
        scope.launch {
            Sources.credentials(locator.prefs, addOn).clear()
            save(removed(p, addOn))
        }
    }) { Text(stringResource(R.string.addon_remove)) }
}
