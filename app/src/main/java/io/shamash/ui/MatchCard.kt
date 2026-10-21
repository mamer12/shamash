package io.shamash.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Team
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Size of a match card in a row. */
val MatchCardHeight = 120.dp
private val CREST = 40.dp

/**
 * A match card: the competition on top, both crests with their short names, the kick-off (or the score) between them,
 * the day or the live minute (red) under it; the user's team's matches get a gold edge. SPEC: MC-02
 */
@Composable
fun MatchCard(match: Match, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.Radius)
    FocusCard(onClick, modifier.size(Dimens.WideW, MatchCardHeight)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(if (match.mine) Palette.MineCard else Palette.Card)
                .then(if (match.mine) Modifier.border(2.dp, Palette.Gold, shape) else Modifier)
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(match.competitionName, style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TeamMark(match.home, CREST)
                Text(matchMiddle(match), style = Type.Score, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
                TeamMark(match.away, CREST)
            }
            Spacer(Modifier.height(4.dp))
            MatchNote(match)
        }
    }
}

@Composable
private fun TeamMark(team: Team, crest: Dp) {
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, crest)
        Text(team.shortName, style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A team's crest, whole (transparent, fit). */
@Composable
fun Crest(team: Team, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        if (team.logoUrl.isNotBlank()) Picture(team.logoUrl, size, size, Modifier.fillMaxSize(), ContentScale.Fit)
    }
}

/** The day ("Today", "Tomorrow", a weekday) or, while it's on, the minute in red. SPEC: MC-02 */
@Composable
fun MatchNote(match: Match, style: androidx.compose.ui.text.TextStyle = Type.CardSub) {
    val live = match.state == MatchState.LIVE
    val text = if (live) match.clock else matchDay(match.startsAt)
    Text(text, style = style, color = if (live) Palette.Live else Palette.Dim, maxLines = 1)
}

/** The kick-off time before the match, the score once it has started. */
@Composable
fun matchMiddle(match: Match): String {
    if (match.state != MatchState.PRE) return stringResource(R.string.score_line, match.home.score, match.away.score)
    val context = LocalContext.current
    return remember(match.startsAt) { kickOff(context, match.startsAt) }
}

@Composable
fun matchDay(at: Long): String {
    val today = stringResource(R.string.day_today)
    val tomorrow = stringResource(R.string.day_tomorrow)
    return remember(at, today) {
        when (daysFromToday(at)) {
            0 -> today
            1 -> tomorrow
            else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(at))
        }
    }
}

fun kickOff(context: Context, at: Long): String = DateFormat.getTimeFormat(context).format(Date(at))

private fun daysFromToday(at: Long): Int {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.floorDiv(at - start, DAY_MS).toInt()
}

private const val DAY_MS = 86_400_000L
