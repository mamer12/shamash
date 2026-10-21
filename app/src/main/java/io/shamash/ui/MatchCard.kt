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
import io.shamash.domain.Carousel
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.MediaItem
import io.shamash.domain.Sport
import io.shamash.domain.Team
import io.shamash.feature.matchcentre.MatchLabels
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Size of a match card in a row. */
val MatchCardHeight = 120.dp
private val CREST = 40.dp

/** A match item's card; it says so when no app on the TV shows the match. SPEC: MC-02, MC-10 */
@Composable
fun MatchCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val match = item.match ?: return
    MatchCard(match, onClick, modifier, noApp = Carousel.noApp(item))
}

/**
 * A match card: the competition on top (with the sport, beyond football), both crests with their short names (an F1
 * session's localized name), the kick-off (or the score) between them, the day or the live minute (red) under it;
 * the user's team's matches get a gold edge. SPEC: MC-02, MC-08, MC-15
 */
@Composable
fun MatchCard(match: Match, onClick: () -> Unit, modifier: Modifier = Modifier, noApp: Boolean = false) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(Dimens.Radius)
    val d = LocalCardDims.current
    FocusCard(onClick, modifier.size(d.wideW, d.matchH), description = mediaMatchLabel(match)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(if (match.mine) Palette.MineCard else Palette.Card)
                .then(if (match.mine) Modifier.border(2.dp, Palette.Gold, shape) else Modifier)
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val heading = remember(match.sport, match.competitionName) { competitionLine(context, match) }
            Text(
                (if (match.mine) "\u2605 " else "") + heading,   // PLT-15: mine = gold edge + star
                style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TeamMark(match.home, CREST)
                Text(matchMiddle(match), style = Type.Score, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
                TeamMark(match.away, CREST, awayName(context, match))
            }
            Spacer(Modifier.height(4.dp))
            MatchNote(match, extra = if (noApp) stringResource(R.string.mcs_no_app_short) else null)
        }
    }
}

/** The competition, after the sport's localized name for sports other than football. SPEC: MC-15 */
private fun competitionLine(context: Context, match: Match): String =
    if (match.sport == Sport.SOCCER) match.competitionName
    else context.getString(R.string.mcs_sport_line, MatchLabels.sport(context, match.sport), match.competitionName)

/** An F1 session stands where the away side would: its name, localized (MC-08, MC-15). */
private fun awayName(context: Context, match: Match): String =
    if (match.sport == Sport.F1 && match.session.isNotBlank()) MatchLabels.session(context, match.session)
    else match.away.shortName

@Composable
private fun TeamMark(team: Team, crest: Dp, name: String = team.shortName) {
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, crest)
        Text(name, style = Type.CardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A team's crest, whole (transparent, fit). */
@Composable
fun Crest(team: Team, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        if (team.logoUrl.isNotBlank()) Picture(team.logoUrl, size, size, Modifier.fillMaxSize(), ContentScale.Fit)
    }
}

/** The day ("Today", "Tomorrow", a weekday) or, while it's on, the minute in red; then [extra] if any. SPEC: MC-02 */
@Composable
fun MatchNote(match: Match, style: androidx.compose.ui.text.TextStyle = Type.CardSub, extra: String? = null) {
    val live = match.state == MatchState.LIVE
    val note = if (live) "${stringResource(R.string.a11y_live)} ${match.clock}".trim() else matchDay(match.startsAt)   // PLT-15
    val text = if (extra == null) note else stringResource(R.string.mcs_note_line, note, extra)
    Text(text, style = style, color = if (live) Palette.Live else Palette.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
