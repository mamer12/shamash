package io.shamash.feature.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.CardSize
import io.shamash.domain.ClockFormat
import io.shamash.domain.DateStyle
import io.shamash.domain.Layout
import io.shamash.domain.LayoutPresets
import io.shamash.domain.Look
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.TempUnit
import io.shamash.feature.home.HomeModel
import io.shamash.feature.settings.Explain
import io.shamash.ui.ChipPicker
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import io.shamash.domain.Accents

private val ACCENT_NAMES = listOf(
    R.string.accent_gold, R.string.accent_sky, R.string.accent_mint, R.string.accent_coral,
    R.string.accent_violet, R.string.accent_rose, R.string.accent_orange, R.string.accent_teal,
)

/**
 * Settings, Layout: presets, the row editor, moment rules and the look. Everything is saved at once in the profile's
 * layout; a Kids profile cannot open it. SPEC: LAY-02, LAY-08, LAY-21
 */
@Composable
fun LayoutPanel(p: Profile, save: (Profile) -> Unit, locator: ServiceLocator) {
    if (p.kind == ProfileKind.KIDS) {
        Explain(stringResource(R.string.layout_locked_kids))
        return
    }
    val ui by remember(locator) { HomeModel.get(locator).state }.collectAsState()
    val change: (Layout) -> Unit = { save(p.copy(layout = it)) }
    val layout = p.layout

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Explain(stringResource(R.string.layout_preset_text))
        PresetPicker(LayoutPresets.all.map { it.id }, layout.presetId) { id ->
            change(LayoutPresets.apply(LayoutPresets.byId(id), layout))
        }
        Section(R.string.layout_rows_title) { RowEditor(ui.editableRows, layout, change) }
        Section(R.string.layout_rules_title) { RuleEditor(ui.editableRows, layout, change) }
        Section(R.string.layout_look_title) { LookEditor(layout, change, locator.providers.map { it.id }) }
    }
}

@Composable
private fun Section(title: Int, body: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(title), color = Palette.Gold, fontSize = 24.sp)
    body()
}

@Composable
private fun LookEditor(layout: Layout, onChange: (Layout) -> Unit, providerIds: List<String>) {
    val look = layout.look
    val set: (Look) -> Unit = { onChange(layout.copy(look = it)) }
    Label(R.string.layout_accent)
    OnePicker(Accents.ALL, look.accent, { set(look.copy(accent = it)) }) { stringResource(ACCENT_NAMES[Accents.ALL.indexOf(it)]) }
    Label(R.string.layout_card_size)
    OnePicker(CardSize.entries, layout.cardSize, { onChange(layout.copy(cardSize = it)) }) {
        stringResource(when (it) { CardSize.S -> R.string.size_s; CardSize.M -> R.string.size_m; CardSize.L -> R.string.size_l })
    }
    Label(R.string.layout_clock)
    OnePicker(ClockFormat.entries, look.clock, { set(look.copy(clock = it)) }) {
        stringResource(when (it) { ClockFormat.FOLLOW -> R.string.clock_follow; ClockFormat.H12 -> R.string.clock_12; ClockFormat.H24 -> R.string.clock_24 })
    }
    Label(R.string.layout_date)
    OnePicker(DateStyle.entries, look.date, { set(look.copy(date = it)) }) {
        stringResource(if (it == DateStyle.WEEKDAY) R.string.date_weekday else R.string.date_numeric)
    }
    Label(R.string.layout_temp)
    OnePicker(TempUnit.entries, look.temp, { set(look.copy(temp = it)) }) {
        stringResource(when (it) { TempUnit.AUTO -> R.string.temp_auto; TempUnit.C -> R.string.temp_c; TempUnit.F -> R.string.temp_f })
    }
    Toggle(R.string.layout_autoplay, layout.autoplay) { onChange(layout.copy(autoplay = it)) }   // LAY-07
    Toggle(R.string.layout_weather_show, look.showWeather) { set(look.copy(showWeather = it)) }
    Toggle(R.string.layout_arabic_layout, look.arabicLayout) { set(look.copy(arabicLayout = it)) }
    Toggle(R.string.layout_arabic_digits, look.arabicDigits) { set(look.copy(arabicDigits = it)) }
    Label(R.string.layout_mix_title)
    ChipPicker(providerIds, providerIds.toSet() - layout.mixOff, {
        onChange(layout.copy(mixOff = providerIds.toSet() - it))
    }) { id -> id.replaceFirstChar { c -> c.titlecase() } }
}

@Composable
private fun Label(res: Int) = Text(stringResource(res), color = Palette.Dim, fontSize = 18.sp)

@Composable
private fun Toggle(label: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    OutlinedButton(onClick = { onChange(!on) }) {
        Text("${stringResource(label)}: ${stringResource(if (on) R.string.layout_on else R.string.layout_off)}")
    }
}
