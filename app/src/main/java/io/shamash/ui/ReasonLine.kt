package io.shamash.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.shamash.R
import io.shamash.domain.Reason
import io.shamash.domain.ReasonKind
import io.shamash.domain.Reasons

/** The one line under a recommended row's title; null (no line) when the row has no reason. SPEC: PER-11 */
@Composable
fun reasonLine(reason: Reason?): String? {
    reason ?: return null
    val text = when (reason.kind) {
        ReasonKind.BECAUSE_WATCHED -> stringResource(R.string.personal_reason_watched, reason.arg.orEmpty())
        ReasonKind.BECAUSE_LOVED -> stringResource(R.string.personal_reason_loved, reason.arg.orEmpty())
        ReasonKind.BECAUSE_LIKED -> stringResource(R.string.personal_reason_liked, reason.arg.orEmpty())
        ReasonKind.BECAUSE_GENRE -> stringResource(R.string.personal_reason_genre, reason.arg.orEmpty())
        ReasonKind.POPULAR_WITH_SERVICE -> stringResource(R.string.personal_reason_popular, reason.arg.orEmpty())
        ReasonKind.FROM_CHANNEL -> stringResource(R.string.personal_reason_channel)
    }
    return Reasons.clip(text)
}
