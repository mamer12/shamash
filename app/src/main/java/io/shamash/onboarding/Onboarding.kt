package io.shamash.onboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.shamash.data.TvApps
import io.shamash.domain.Profile
import io.shamash.domain.TvApp
import io.shamash.ui.Palette

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

/**
 * The first run: who you are, the apps you pay for, your teams, what you like. Five short steps, each one screen,
 * all skippable except the welcome; `done` gets the answers.
 */
@Composable
fun Onboarding(start: Profile, done: (Profile) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(start.name) }
    var city by rememberSaveable { mutableStateOf(start.city) }
    var subs by remember { mutableStateOf(start.subscriptions) }
    var teams by rememberSaveable { mutableStateOf(start.teams.joinToString(", ")) }
    var comps by remember { mutableStateOf(start.competitions) }
    var genres by remember { mutableStateOf(start.genres) }

    fun finish() = done(
        start.copy(
            onboarded = true, name = name.trim(), city = city.trim(), subscriptions = subs,
            teams = teams.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            competitions = comps, genres = genres,
        )
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Dusk, Palette.Night)))
            .padding(horizontal = 96.dp, vertical = 64.dp)
    ) {
        when (step) {
            0 -> Step(
                title = "Welcome to Shamash",
                subtitle = "Let's make this TV yours. A few questions, and the home screen shapes itself around you.",
                next = "Start", onNext = { step = 1 },
            ) {}
            1 -> Step("About you", "For the greeting and the weather.", "Next", { step = 2 }) {
                Field("Your first name", name) { name = it }
                Spacer(Modifier.height(20.dp))
                Field("Your city", city) { city = it }
            }
            2 -> Step(
                "Your apps", "Pick the ones you subscribe to: the home screen recommends from them and opens titles there.",
                "Next", { step = 3 },
            ) { AppPicker(subs) { subs = it } }
            3 -> Step("Your teams", "Their matches get a reminder and a live score over anything you watch.", "Next", { step = 4 }) {
                Field("Teams (comma separated), e.g. Barcelona, Al Hilal", teams) { teams = it }
                Spacer(Modifier.height(24.dp))
                Chips(COMPETITIONS.values.toList(), comps.mapNotNull { COMPETITIONS[it] }.toSet()) { picked ->
                    comps = COMPETITIONS.filterValues { it in picked }.keys
                }
            }
            else -> Step("What you like", "Pick a few: recommendations lean toward them.", "Done", ::finish) {
                Chips(GENRES, genres) { genres = it }
            }
        }
    }
}

@Composable
private fun Step(title: String, subtitle: String, next: String, onNext: () -> Unit, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = Palette.Dim, modifier = Modifier.width(900.dp))
        Spacer(Modifier.height(40.dp))
        Column(Modifier.weight(1f)) { body() }
        Button(onClick = onNext) { Text(next) }
    }
}

@Composable
private fun Field(hint: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(hint, color = Palette.Dim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Palette.Ink, fontSize = 24.sp),
            cursorBrush = SolidColor(Palette.Gold),
            modifier = Modifier
                .width(640.dp)
                .background(Palette.Dusk, RoundedCornerShape(16.dp))
                .border(2.dp, Palette.Dim.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun Chips(all: List<String>, picked: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        all.forEach { item ->
            val on = item in picked
            FilterChip(selected = on, onClick = { onChange(if (on) picked - item else picked + item) }) { Text(item) }
        }
    }
}

/** The TV's apps as cards (their own TV art); the streaming ones first. Selected = you subscribe. */
@Composable
private fun AppPicker(picked: Set<String>, onChange: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val source = remember { TvApps(context) }
    val apps = remember { source.installed() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(apps, key = { it.packageName }) { app -> AppCard(app, source, app.packageName in picked) {
            onChange(if (app.packageName in picked) picked - app.packageName else picked + app.packageName)
        } }
    }
}

@Composable
private fun AppCard(app: TvApp, source: TvApps, on: Boolean, onClick: () -> Unit) {
    val art = remember(app.packageName) { source.banner(app.packageName)?.let { bitmap(it) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(240.dp, 135.dp)
                .border(if (on) 4.dp else 0.dp, Palette.Gold, RoundedCornerShape(12.dp)),
        ) {
            if (art != null) Image(art.asImageBitmap(), contentDescription = app.label, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Palette.Dusk), contentAlignment = Alignment.Center) { Text(app.label) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "✓ " else "", color = Palette.Gold, fontSize = 18.sp)
            Text(app.label, color = if (on) Palette.Ink else Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        }
    }
}

private fun bitmap(d: Drawable): Bitmap {
    val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, 320, 180)
    d.draw(Canvas(b))
    return b
}
