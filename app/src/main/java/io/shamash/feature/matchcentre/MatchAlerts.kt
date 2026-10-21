package io.shamash.feature.matchcentre

import io.shamash.domain.Follow
import io.shamash.domain.Match
import io.shamash.domain.MatchFollows
import io.shamash.domain.MatchState
import io.shamash.domain.Profile
import kotlinx.serialization.Serializable

/** What an alert level lets through. SPEC: MC-11 */
enum class AlertLevel { OFF, KICK_OFF, GOALS, ALL }

/**
 * The profile's alert choices: a level per follow (default Kick-off), quiet hours, goal-only mode and the matches
 * starred "Always". Local only (MC-16). SPEC: MC-11, MC-12, MC-13
 */
@Serializable
data class AlertSettings(
    val levels: Map<String, AlertLevel> = emptyMap(),     // key: "kind:name"
    val quiet: QuietHours = QuietHours(),
    val goalOnly: Boolean = false,
    val always: Set<String> = emptySet(),                 // match ids that ignore quiet hours
) {
    fun levelOf(follow: Follow): AlertLevel = levels[key(follow)] ?: AlertLevel.KICK_OFF
    fun withLevel(follow: Follow, level: AlertLevel) = copy(levels = levels + (key(follow) to level))

    companion object {
        fun key(f: Follow) = "${f.kind}:${f.name.trim().lowercase()}"
    }
}

/** Pure decisions on what shows. SPEC: MC-11..13 */
object MatchAlerts {
    /** The best level among the follows this match touches; OFF when it touches none. */
    fun levelFor(match: Match, profile: Profile, settings: AlertSettings): AlertLevel =
        MatchFollows.matching(profile, match).map(settings::levelOf).maxByOrNull { it.ordinal }
            ?: AlertLevel.OFF

    /** The matches an alert may ever come from: the follows not turned Off, and for Kids only their own. */
    fun eligible(matches: List<Match>, profile: Profile, settings: AlertSettings): List<Match> =
        matches.filter { it.mine && levelFor(it, profile, settings) != AlertLevel.OFF }

    /** Higher is more important: a live match of an All follow beats everything. SPEC: MC-11 */
    fun priority(match: Match, profile: Profile, settings: AlertSettings): Int {
        val level = levelFor(match, profile, settings)
        val live = if (match.state == MatchState.LIVE) 10 else 0
        return live + level.ordinal
    }

    /** Whether [card] may show for this match now. SPEC: MC-11, MC-12, MC-13 */
    fun shows(
        card: OverlayCard,
        match: Match,
        profile: Profile,
        settings: AlertSettings,
        now: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Boolean {
        val level = levelFor(match, profile, settings)
        if (!levelAllows(level, card, settings.goalOnly)) return false
        val starred = match.id in settings.always
        return starred || !settings.quiet.suppresses(now, zone)
    }

    fun levelAllows(level: AlertLevel, card: OverlayCard, goalOnly: Boolean): Boolean = when (card) {
        OverlayCard.REMINDER -> level == AlertLevel.KICK_OFF || level == AlertLevel.ALL
        OverlayCard.LIVE -> level == AlertLevel.ALL && !goalOnly
        OverlayCard.GOAL, OverlayCard.FINAL -> level == AlertLevel.GOALS || level == AlertLevel.ALL
    }
}
