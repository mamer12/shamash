package io.shamash.data.espn

import io.shamash.data.Refresh
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Sport
import kotlin.math.abs

/**
 * How often to look again, per match: every 60 s while live (30 s in an F1 session), every minute around kick-off,
 * every 10 min when a follow plays within 3 h, else the 3 h refresh (HOME-14). SPEC: MC-14
 */
object Polling {
    const val LIVE_MS = Refresh.MINUTE
    const val F1_LIVE_MS = 30_000L
    const val SOON_MS = 10 * Refresh.MINUTE
    const val SOON_WINDOW_MS = 3 * Refresh.HOUR
    const val IDLE_MS = 3 * Refresh.HOUR
    private const val KICK_OFF_WINDOW_MS = 5 * Refresh.MINUTE

    fun live(sport: Sport): Long = if (sport == Sport.F1) F1_LIVE_MS else LIVE_MS

    fun every(match: Match, now: Long): Long = when {
        match.state == MatchState.LIVE -> live(match.sport)
        match.state == MatchState.DONE -> IDLE_MS
        abs(match.startsAt - now) <= KICK_OFF_WINDOW_MS -> LIVE_MS
        match.startsAt - now in 0..SOON_WINDOW_MS -> SOON_MS
        else -> IDLE_MS
    }

    /** The shortest wait over the user's matches. */
    fun every(matches: List<Match>, now: Long): Long =
        matches.filter { it.mine }.minOfOrNull { every(it, now) } ?: IDLE_MS
}
