package io.shamash.domain

/** The part of the day the home screen dresses for (TV clock). SPEC: HOME-06 */
enum class Moment {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun at(hour: Int): Moment = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..22 -> EVENING
            else -> NIGHT
        }
    }
}

/** Which greeting to show and the name after it (null: no name, so no comma). SPEC: HOME-07 */
data class Greeting(val moment: Moment, val name: String?)

object Moments {
    /** How far ahead the user's team's match counts as today's. SPEC: HOME-08, MC-03 */
    const val MATCH_WINDOW_MS = 14L * 3_600_000L

    /** How much more the background darkens late at night. SPEC: HOME-09 */
    const val NIGHT_DIM = 0.35f

    /** A title watched this far counts as finished. */
    private const val FINISHED = 0.95f

    /** Late night still says "Good evening": "Good night" is a goodbye. SPEC: HOME-07 */
    fun greeting(moment: Moment, name: String): Greeting = Greeting(
        moment = if (moment == Moment.NIGHT) Moment.EVENING else moment,
        name = name.trim().ifEmpty { null },
    )

    fun nightDim(moment: Moment): Float = if (moment == Moment.NIGHT) NIGHT_DIM else 0f

    /** The user's team playing now or kicking off within [window]. SPEC: HOME-08, MC-03 */
    fun mineSoon(matches: List<Match>, now: Long, window: Long = MATCH_WINDOW_MS): List<Match> =
        matches.filter { m ->
            m.mine && (m.state == MatchState.LIVE || (m.state == MatchState.PRE && m.startsAt in now..now + window))
        }.sortedWith(compareBy({ it.state != MatchState.LIVE }, { it.startsAt }))

    /** The first movie or series the user hasn't finished. */
    fun firstUnfinished(watching: List<MediaItem>): MediaItem? =
        watching.firstOrNull { it.kind != Kind.VIDEO && (it.progress == null || it.progress < FINISHED) }

    /**
     * The moment's row: the user's matches soon, the first unfinished movie or series, one personal pick and one
     * YouTube pick (in the morning YouTube comes first); each title once. SPEC: HOME-08
     */
    fun row(
        moment: Moment,
        matches: List<MediaItem>,
        unfinished: MediaItem?,
        personal: MediaItem?,
        youtube: MediaItem?,
    ): List<MediaItem> {
        val picks = if (moment == Moment.MORNING) listOf(youtube, unfinished, personal)
        else listOf(unfinished, personal, youtube)
        return (matches + picks.filterNotNull()).distinctBy { it.title.lowercase() }
    }
}
