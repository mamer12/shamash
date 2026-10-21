package io.shamash.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.shamash.R
import io.shamash.domain.Kind
import io.shamash.domain.MatchState
import io.shamash.domain.MediaItem
import io.shamash.feature.accessibility.CardLabel

/** What TalkBack says for a card: "<title>, <kind>, <provider>, <progress>%". SPEC: PLT-13 */
@Composable
fun mediaLabel(item: MediaItem, rank: Int? = null): String {
    item.match?.let { return mediaMatchLabel(it) }
    val kind = stringResource(
        when (item.kind) {
            Kind.MOVIE -> R.string.a11y_kind_movie
            Kind.SERIES -> R.string.a11y_kind_series
            Kind.VIDEO -> R.string.a11y_kind_video
            Kind.MATCH -> R.string.a11y_kind_match
            Kind.LIVE -> R.string.a11y_kind_live
        },
    )
    val provider = providerLabel(item.providerId)
    val percent = CardLabel.percent(item.progress)?.let { stringResource(R.string.a11y_progress, it) }
    val ranked = rank?.let { stringResource(R.string.a11y_rank, it) }
    return CardLabel.join(ranked, item.title, kind, provider ?: item.subtitle.takeIf { it.isNotBlank() }, percent)
}

/** A match card's spoken form: teams, competition, LIVE, score or kick-off, "Your team". SPEC: PLT-13, PLT-15 */
@Composable
fun mediaMatchLabel(match: io.shamash.domain.Match): String {
    val vs = stringResource(R.string.a11y_match_vs, match.home.name, match.away.name)
    val live = if (match.state == MatchState.LIVE) stringResource(R.string.a11y_live) else null
    val mine = if (match.mine) stringResource(R.string.a11y_mine) else null
    return CardLabel.join(vs, match.competitionName, live, matchMiddle(match), mine)
}

@Composable
private fun providerLabel(providerId: String): String? = when (providerId) {
    "tmdb" -> stringResource(R.string.provider_tmdb)
    "youtube" -> stringResource(R.string.provider_youtube)
    "cinemana" -> stringResource(R.string.provider_cinemana)
    "tod" -> stringResource(R.string.provider_tod)
    "espn" -> stringResource(R.string.provider_espn)
    else -> null
}
