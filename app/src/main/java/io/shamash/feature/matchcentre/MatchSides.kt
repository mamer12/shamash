package io.shamash.feature.matchcentre

import io.shamash.domain.Match

/** Arabic match text mirrors: the crests and scores swap sides in right-to-left. SPEC: MC-15 */
object MatchSides {
    fun forDirection(match: Match, rtl: Boolean): Match = if (rtl) match.copy(home = match.away, away = match.home) else match
}
