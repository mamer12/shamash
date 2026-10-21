package io.shamash.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.TvApps
import io.shamash.data.softly
import io.shamash.data.weather.Place
import io.shamash.domain.LayoutPreset
import io.shamash.domain.LayoutPresets
import io.shamash.domain.Profile
import io.shamash.feature.layout.PresetPicker
import io.shamash.domain.TvApp
import io.shamash.feature.settings.KeyFields
import io.shamash.feature.tvcontrol.SwitchPanel
import io.shamash.feature.tvcontrol.SwitchState
import io.shamash.feature.tvcontrol.rememberSwitchModel
import io.shamash.ui.AppBanner
import io.shamash.ui.ChipPicker
import io.shamash.ui.Palette
import io.shamash.ui.Picture
import io.shamash.ui.TextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Competitions Match Centre can follow (ESPN's codes → names). */
val COMPETITIONS = linkedMapOf(
    "uefa.champions" to "Champions League", "eng.1" to "Premier League", "esp.1" to "LaLiga",
    "ita.1" to "Serie A", "ger.1" to "Bundesliga", "fra.1" to "Ligue 1", "ksa.1" to "Saudi Pro League",
    "uefa.europa" to "Europa League",
)

val GENRES = listOf(
    "Action", "Comedy", "Drama", "Thriller", "Sci-Fi", "Animation", "Documentary", "Romance", "Horror",
    "Family", "Anime", "Arabic", "Turkish", "Korean", "Sports",
)

/** The steps, in order. SPEC: ONB-02 */
private enum class Step { WELCOME, YOU, APPS, TEAMS, GENRES, LOOK, KEYS, HOME }

/**
 * The first run: who you are, the apps you pay for, your teams, what you like, optional keys, then the switch. One
 * screen each, all skippable except the welcome; Back goes to the previous step; `done` gets the answers.
 */
@Composable
fun Onboarding(start: Profile, done: (Profile) -> Unit) {
    val locator = ServiceLocator.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var answers by remember { mutableStateOf(Answers.of(start)) }
    var place by remember { mutableStateOf<Lookup?>(null) }
    BackHandler(enabled = step != Step.WELCOME) { step = Step.entries[step.ordinal - 1] }

    fun next() {
        step = Step.entries[step.ordinal + 1]
    }

    /** City: look it up once, show what was found, then go on (a second Next). SPEC: ONB-02 step 2 */
    fun nextFromYou() {
        val city = answers.city.trim()
        if (city.isEmpty() || place?.city == city) return next()
        scope.launch {
            val found = locator.geocoder.find(city)
            place = Lookup(city, found)
            answers = answers.located(found)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Dusk, Palette.Night)))
            .padding(horizontal = 96.dp, vertical = 64.dp)
    ) {
        when (step) {
            Step.WELCOME -> Page(R.string.setup_welcome_title, R.string.setup_welcome_text, R.string.setup_start, ::next, skip = null) {}
            Step.YOU -> Page(R.string.setup_you_title, R.string.setup_you_text, R.string.setup_next, ::nextFromYou, ::next) {
                TextInput(stringResource(R.string.setup_name), answers.name, { answers = answers.copy(name = it) })
                Spacer(Modifier.height(20.dp))
                TextInput(stringResource(R.string.setup_city), answers.city, { answers = answers.copy(city = it) })
                place?.takeIf { it.city == answers.city.trim() }?.let { CityFound(it.found) }
            }
            Step.APPS -> Page(R.string.setup_apps_title, R.string.setup_apps_text, R.string.setup_next, ::next, ::next) {
                AppPicker(answers.subscriptions) { answers = answers.copy(subscriptions = it) }
            }
            Step.TEAMS -> Page(R.string.setup_teams_title, R.string.setup_teams_text, R.string.setup_next, ::next, ::next) {
                TextInput(stringResource(R.string.setup_teams_hint), answers.teams, { answers = answers.copy(teams = it) })
                Spacer(Modifier.height(24.dp))
                ChipPicker(COMPETITIONS.keys.toList(), answers.competitions, { answers = answers.copy(competitions = it) }) {
                    COMPETITIONS.getValue(it)
                }
            }
            Step.GENRES -> Page(R.string.setup_genres_title, R.string.setup_genres_text, R.string.setup_next, ::next, ::next) {
                ChipPicker(GENRES, answers.genres, { answers = answers.copy(genres = it) })
            }
            Step.LOOK -> Page(R.string.layout_preset_title, R.string.layout_preset_text, R.string.setup_next, ::next, ::next) {
                PresetPicker(LayoutPresets.all.map { it.id }, answers.preset ?: LayoutPreset.CINEMA) {
                    answers = answers.copy(preset = it)
                }
            }
            Step.KEYS -> Page(R.string.setup_keys_title, R.string.setup_keys_text, R.string.setup_next, ::next, ::next) {
                KeyFields(answers.keys) { answers = answers.copy(keys = it) }
            }
            Step.HOME -> HomeStep(onDone = { done(answers.into(start)) })
        }
    }
}

/** The last step: the switch itself, or "Not now" (Settings › Home screen has it later). SPEC: ONB-02 step 7 */
@Composable
private fun HomeStep(onDone: () -> Unit) {
    val model = rememberSwitchModel()
    val state by model.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Title(R.string.setup_home_title, R.string.setup_home_text)
        SwitchPanel(model, Modifier.weight(1f)) {
            val finished = state is SwitchState.Finished
            OutlinedButton(onClick = onDone, enabled = state !is SwitchState.Running) {
                Text(stringResource(if (finished) R.string.setup_done else R.string.setup_not_now))
            }
        }
    }
}

@Composable
private fun CityFound(found: Place?) {
    Spacer(Modifier.height(12.dp))
    val text = if (found == null) stringResource(R.string.setup_city_unknown)
    else stringResource(R.string.setup_city_found, "${found.name}, ${found.countryCode}")
    Text(text, color = if (found == null) Palette.Dim else Palette.Gold, fontSize = 18.sp)
}

/** A city and what the geocoder found for it (null: nothing). */
private data class Lookup(val city: String, val found: Place?)

@Composable
private fun Title(title: Int, subtitle: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.displaySmall, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(subtitle), style = MaterialTheme.typography.titleMedium, color = Palette.Dim, modifier = Modifier.width(900.dp))
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun Page(title: Int, subtitle: Int, next: Int, onNext: () -> Unit, skip: (() -> Unit)?, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Title(title, subtitle)
        Column(Modifier.weight(1f)) { body() }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onNext) { Text(stringResource(next)) }
            if (skip != null) OutlinedButton(onClick = skip) { Text(stringResource(R.string.setup_skip)) }
        }
    }
}

/**
 * The TV's apps as cards (their own TV art); the streaming ones first. Selected = you subscribe. The list and the
 * banners load off the main thread (the banners through the image loader's cache). SPEC: PERF-02, PERF-04
 */
@Composable
private fun AppPicker(picked: Set<String>, onChange: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState(emptyList<TvApp>()) {
        value = withContext(Dispatchers.IO) { softly("setup apps", emptyList()) { TvApps(context).installed() } }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(apps, key = { it.packageName }) { app -> AppCard(app, app.packageName in picked) {
            onChange(if (app.packageName in picked) picked - app.packageName else picked + app.packageName)
        } }
    }
}

@Composable
private fun AppCard(app: TvApp, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(240.dp, 135.dp)
                .border(if (on) 4.dp else 0.dp, Palette.Gold, RoundedCornerShape(12.dp)),
        ) {
            // The label shows until the banner is drawn over it (and stays when the app has none).
            Box(Modifier.fillMaxSize().background(Palette.Dusk), contentAlignment = Alignment.Center) { Text(app.label) }
            Picture(AppBanner(app.packageName), 240.dp, 135.dp, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "✓ " else "", color = Palette.Gold, fontSize = 18.sp)
            Text(app.label, color = if (on) Palette.Ink else Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        }
    }
}
