package io.shamash.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The weather as the top bar shows it (already in the user's unit). */
@Immutable
data class WeatherNow(val degrees: Int, val code: Int, val isDay: Boolean)

/** The top bar's three round buttons. */
@Immutable
data class TopActions(val onSearch: () -> Unit, val onInputs: () -> Unit, val onTvSettings: () -> Unit)

/**
 * Search, Inputs and TV Settings on the left; the tabs centered (moving onto a tab selects it); the time, and under
 * it the date, weather and city, on the right. SPEC: HOME-01
 */
@Composable
fun TopBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tabRequesters: List<FocusRequester>,
    actions: TopActions,
    weather: WeatherNow?,
    city: String,
    onTabsFocused: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(Dimens.TopBarHeight).padding(start = Dimens.Margin, end = Dimens.Margin, top = 22.dp)) {
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(Icons.Search, stringResource(R.string.action_search), actions.onSearch)
            RoundButton(Icons.Input, stringResource(R.string.inputs), actions.onInputs)
            RoundButton(Icons.Settings, stringResource(R.string.action_tv_settings), actions.onTvSettings)
        }
        Row(
            Modifier.align(Alignment.TopCenter).onFocusChanged { onTabsFocused(it.hasFocus) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(label, i == selected, { onSelect(i) }, Modifier.focusRequester(tabRequesters[i]))
            }
        }
        Clock(weather, city, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FocusCard(
        onClick, Modifier.size(40.dp).semantics { contentDescription = label },
        shape = CircleShape, corner = 20.dp, scale = 1.1f,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Palette.Ink else Palette.Ink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Symbol(icon, if (focused) Palette.Night else Palette.Ink, Modifier.size(20.dp)) }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    FocusCard(
        onSelect,
        modifier.onFocusChanged { if (it.isFocused) onSelect() },
        shape = RoundedCornerShape(50), corner = 20.dp, scale = 1.05f,
    ) { focused ->
        Column(
            Modifier
                .background(if (focused) Palette.Ink else Palette.Night.copy(alpha = 0f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ink = when {
                focused -> Palette.Night
                selected -> Palette.Gold
                else -> Palette.Dim
            }
            Text(label, style = Type.Tab, color = ink)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(4.dp).background(if (selected && !focused) Palette.Gold else Palette.Night.copy(alpha = 0f), CircleShape))
        }
    }
}

/** The time, then "Tue 21 Oct · ☀ 21° Baghdad"; ticks once a minute (only this recomposes). */
@Composable
private fun Clock(weather: WeatherNow?, city: String, modifier: Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(MINUTE_MS - value % MINUTE_MS)
        }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now / MINUTE_MS) {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(Date(now))
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(time, style = Type.Clock, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = Type.Small, maxLines = 1)
            if (weather != null) {
                Text(" · ", style = Type.Small)
                Symbol(Icons.weather(weather.code, weather.isDay), Palette.Dawn, Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                val degrees = stringResource(R.string.temperature, weather.degrees)
                Text(if (city.isEmpty()) degrees else stringResource(R.string.weather_line, degrees, city), style = Type.Small, maxLines = 1)
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
