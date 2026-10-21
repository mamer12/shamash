package io.shamash.data.trakt

import io.shamash.R
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.MediaItem
import io.shamash.domain.Signal
import io.shamash.domain.SignalKind

/** What one import changes: the mirrored state and the titles to report as finished. */
data class ImportPlan(val state: TrackerState, val finished: List<MediaItem>)

/** The pure part of a tracker sync. SPEC: PEX-26 */
object TrackerSync {
    const val MAX_PUSH = 50
    private val TMDB_MOVIE = Regex("""^tmdb:movie:(\d+)$""")

    /**
     * The watchlist (minus titles removed on the TV) becomes the mirrored list; watched titles become FINISH items
     * only when someone [listening] records them, and only then does the history mark move on.
     */
    fun plan(providerId: String, before: TrackerState, library: Library, listening: Boolean): ImportPlan {
        val dismissed = before.dismissed.toSet()
        val list = library.watchlist.filter { it.key !in dismissed }.map { TrackerJson.entry(providerId, it) }
        val fresh = library.watched.filter { (it.at ?: 0) > before.since }
        val finished = if (listening) fresh.map { TrackerJson.entry(providerId, it).toItem() } else emptyList()
        val since = if (listening) maxOf(before.since, fresh.maxOfOrNull { it.at ?: 0 } ?: 0) else before.since
        return ImportPlan(before.copy(list = list, since = since), finished)
    }

    /** Movies from TMDB titles the profile finished after [since]: (TMDB id, finished at), oldest first. */
    fun toPush(signals: List<Signal>, since: Long): List<Pair<Int, Long>> = signals.asSequence()
        .filter { it.kind == SignalKind.FINISH && it.at > since && it.providerId == "tmdb" }
        .mapNotNull { s -> TMDB_MOVIE.find(s.itemKey)?.groupValues?.get(1)?.toIntOrNull()?.let { it to s.at } }
        .sortedBy { it.second }.distinctBy { it.first }.take(MAX_PUSH).toList()
}

/** The values a tracker asks for: the user's own client, and the sign-in the code flow fills in. SPEC: PEX-03, PEX-26 */
object TrackerFields {
    fun of(secretRequired: Boolean) = listOf(
        CredentialField(TrackerProvider.CLIENT_ID, R.string.more_trakt_client_id, CredentialKind.TEXT, hintRes = R.string.more_trakt_client_id_hint),
        CredentialField(TrackerProvider.CLIENT_SECRET, R.string.more_trakt_client_secret, CredentialKind.SECRET, required = secretRequired, hintRes = R.string.more_trakt_client_secret_hint),
        CredentialField(TrackerProvider.SESSION, R.string.more_trakt_session, CredentialKind.TOKEN, required = false, hintRes = R.string.more_trakt_session_hint),
    )
}
