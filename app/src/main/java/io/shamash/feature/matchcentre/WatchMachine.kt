package io.shamash.feature.matchcentre

import io.shamash.data.espn.Polling
import io.shamash.domain.Match
import io.shamash.domain.MatchState

/** The corner card's faces. SPEC: MC-04, MC-05 */
enum class OverlayCard { REMINDER, LIVE, GOAL, FINAL }

/** What the overlay does now, and when to look again. */
sealed interface WatchStep {
    data class Show(val match: Match, val card: OverlayCard, val recheckInMs: Long) : WatchStep
    data class Hide(val recheckInMs: Long) : WatchStep
    data object Stop : WatchStep
}

/**
 * Follows one of the user's matches: a reminder 10 min before kick-off for 15 s, then the live score (looked at every
 * 60 s) with a gold "GOAL!" for 10 s when the score changes, the final score for 30 s, then stop. Give it the time
 * and the latest matches; it remembers what it already showed. [priority] ranks the user's matches: a live match that
 * outranks the followed live one takes over the bubble (never two). SPEC: MC-04, MC-05, MC-06, MC-11
 */
class WatchMachine(private val priority: (Match) -> Int = { 0 }) {
    private var following: Match? = null
    private var reminderAt: Long? = null
    private var lastScore: Pair<String, String>? = null
    private var goalAt: Long? = null
    private var finalAt: Long? = null

    fun next(now: Long, matches: List<Match>): WatchStep {
        val match = current(now, matches) ?: return finish(now)
        following = match
        return when (match.state) {
            MatchState.PRE -> beforeKickOff(now, match)
            MatchState.LIVE -> live(now, match)
            MatchState.DONE -> finish(now)
        }
    }

    /** The followed match as it is now (DONE when it left the list), or the next one to follow. */
    private fun current(now: Long, matches: List<Match>): Match? {
        val followed = following ?: return nextToWatch(matches, now, WATCH_WINDOW_MS, priority)
        val fresh = matches.firstOrNull { it.id == followed.id } ?: followed.copy(state = MatchState.DONE)
        following = fresh
        takeOver(fresh, matches, now)?.let { return it }
        val stale = fresh.state == MatchState.PRE && now - fresh.startsAt > STALE_PRE_MS
        return if (fresh.state == MatchState.DONE || stale) null else fresh
    }

    /** A live match of higher priority than the live one followed; the memory starts over for it. */
    private fun takeOver(current: Match, matches: List<Match>, now: Long): Match? {
        if (current.state != MatchState.LIVE) return null
        val other = nextToWatch(matches, now, WATCH_WINDOW_MS, priority) ?: return null
        if (other.id == current.id || other.state != MatchState.LIVE || priority(other) <= priority(current)) return null
        reminderAt = null; lastScore = null; goalAt = null; finalAt = null
        following = other
        return other
    }

    private fun beforeKickOff(now: Long, match: Match): WatchStep {
        val remindAt = match.startsAt - REMIND_BEFORE_MS
        if (now < remindAt) return WatchStep.Hide(remindAt - now)
        val shownAt = reminderAt ?: now.takeIf { it < match.startsAt }?.also { reminderAt = it }
        val left = shownAt?.let { REMINDER_MS - (now - it) } ?: 0
        return if (left > 0) WatchStep.Show(match, OverlayCard.REMINDER, left) else WatchStep.Hide(TICK_MS)
    }

    private fun live(now: Long, match: Match): WatchStep {
        val score = match.home.score to match.away.score
        if (KeyMoments.happened(match.sport, lastScore, score)) goalAt = now
        lastScore = score
        val goalLeft = goalAt?.let { GOAL_MS - (now - it) } ?: 0
        return if (goalLeft > 0) WatchStep.Show(match, OverlayCard.GOAL, goalLeft)
        else WatchStep.Show(match, OverlayCard.LIVE, Polling.live(match.sport))
    }

    /** Full time: the final score for a while, if the match was seen live; then nothing. */
    private fun finish(now: Long): WatchStep {
        val match = following?.takeIf { lastScore != null } ?: return WatchStep.Stop
        val since = now - (finalAt ?: now.also { finalAt = it })
        return if (since < FINAL_MS) WatchStep.Show(match, OverlayCard.FINAL, FINAL_MS - since) else WatchStep.Stop
    }

    companion object {
        const val TICK_MS = 60_000L
        const val REMIND_BEFORE_MS = 10 * 60_000L
        const val WAKE_BEFORE_MS = 12 * 60_000L
        const val REMINDER_MS = 15_000L
        const val GOAL_MS = 10_000L
        const val FINAL_MS = 30_000L
        /** A little more than the wake-up lead, for alarms that fire early. */
        const val WATCH_WINDOW_MS = 20 * 60_000L
        /** A match still "about to start" this long after kick-off was postponed or isn't covered. */
        const val STALE_PRE_MS = 3 * 60 * 60_000L

        /** The user's next match not over yet, live ones first; within [window] of kick-off when one is given. */
        fun nextToWatch(
            matches: List<Match>,
            now: Long,
            window: Long = Long.MAX_VALUE,
            priority: (Match) -> Int = { 0 },
        ): Match? =
            matches.filter { it.mine && it.state != MatchState.DONE }
                .filter { it.state == MatchState.LIVE || now - it.startsAt <= STALE_PRE_MS }
                .filter { it.state == MatchState.LIVE || it.startsAt - now <= window }
                .minWithOrNull(compareBy({ -priority(it) }, { it.state != MatchState.LIVE }, { it.startsAt }))
    }
}
