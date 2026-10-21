package io.shamash.feature.matchcentre

import android.content.Context
import io.shamash.R
import io.shamash.data.espn.SessionCodes
import io.shamash.domain.Sport

/** Localized names for sports, F1 sessions and alert levels (en and ar in strings_matches.xml). SPEC: MC-15 */
object MatchLabels {
    fun sport(context: Context, sport: Sport): String = context.getString(
        when (sport) {
            Sport.SOCCER -> R.string.sport_soccer
            Sport.F1 -> R.string.sport_f1
            Sport.TENNIS -> R.string.sport_tennis
            Sport.BASKETBALL -> R.string.sport_basketball
            Sport.CRICKET -> R.string.sport_cricket
        },
    )

    fun session(context: Context, code: String): String = context.getString(
        when (code) {
            SessionCodes.FP1 -> R.string.f1_fp1
            SessionCodes.FP2 -> R.string.f1_fp2
            SessionCodes.FP3 -> R.string.f1_fp3
            SessionCodes.QUALI -> R.string.f1_quali
            SessionCodes.SPRINT_QUALI -> R.string.f1_sprint_quali
            SessionCodes.SPRINT -> R.string.f1_sprint
            else -> R.string.f1_race
        },
    )

    fun level(context: Context, level: AlertLevel): String = context.getString(
        when (level) {
            AlertLevel.OFF -> R.string.alert_off
            AlertLevel.KICK_OFF -> R.string.alert_kick_off
            AlertLevel.GOALS -> R.string.alert_goals
            AlertLevel.ALL -> R.string.alert_all
        },
    )
}
