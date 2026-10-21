package io.shamash.feature.matchcentre

import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.Profile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The "Always" star on a match (MC-12): which matches can carry one and how a star is set. Pure; the settings section
 * draws it. SPEC: MC-12
 */
object AlertStars {
    const val SHOWN_MAX = 8

    /** The follows' matches that may still alert (live first, then the soonest), at most [SHOWN_MAX]. */
    fun candidates(matches: List<Match>, profile: Profile, settings: AlertSettings): List<Match> =
        MatchAlerts.eligible(matches, profile, settings)
            .filter { it.state != MatchState.DONE }
            .sortedWith(compareBy<Match>({ if (it.state == MatchState.LIVE) 0 else 1 }, { it.startsAt }))
            .take(SHOWN_MAX)

    /** "Sat 20:00" in the TV's zone, Western digits (LAY-17). */
    fun whenText(startsAt: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE HH:mm", locale).withZone(zone).format(Instant.ofEpochMilli(startsAt))

    /**
     * [matchId] starred or unstarred. Stars on matches no longer listed ([listed] = the matches known now) are dropped
     * so the set doesn't grow; with nothing listed (the schedule couldn't be read) every star is kept.
     */
    fun toggled(settings: AlertSettings, matchId: String, listed: Collection<Match>): AlertSettings {
        val ids = listed.map { it.id }.toSet()
        val kept = if (ids.isEmpty()) settings.always else settings.always.filter { it in ids }.toSet()
        return settings.copy(always = if (matchId in settings.always) kept - matchId else kept + matchId)
    }
}
