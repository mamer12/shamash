package io.shamash.domain

/** What leads the billboard and how the providers' featured titles take turns. SPEC: HOME-10, MC-01, MC-03 */
object Carousel {
    const val MAX = 16

    /** Where matches are watched by default (MC-03: "TOD by default"). */
    const val MATCH_APP = "com.todtv.tod"

    /** The providers' turn order; others follow in the order they came. SPEC: HOME-10 */
    val ORDER = listOf("cinemana", "youtube", "tmdb", "tod")

    /**
     * The billboard's items: [lead] (the user's team's match) first, then one item per provider in turn, only those
     * with wide art, each once, at most [max]. SPEC: HOME-10
     */
    fun mix(lead: MediaItem?, featured: Map<String, List<MediaItem>>, max: Int = MAX): List<MediaItem> {
        val queues = featured.entries
            .sortedBy { (id, _) -> ORDER.indexOf(id).let { if (it < 0) ORDER.size else it } }
            .map { (_, items) -> items.filter { !it.backdropUrl.isNullOrBlank() } }
        val out = ArrayList<MediaItem>(max)
        val seen = HashSet<String>()
        fun add(item: MediaItem) {
            if (out.size < max && seen.add(item.title.lowercase())) out += item
        }
        lead?.let(::add)
        val longest = queues.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) queues.forEach { q -> q.getOrNull(i)?.let(::add) }
        return out
    }

    /** The match the billboard leads with: the user's team live, else its next kick-off within 14 h. SPEC: MC-03 */
    fun matchDay(matches: List<Match>, now: Long): Match? = Moments.mineSoon(matches, now).firstOrNull()

    /** The Matches row: the user's teams first, then by kick-off, at most [max]. SPEC: MC-01 */
    fun matchRow(matches: List<Match>, max: Int = MAX): List<Match> =
        matches.sortedWith(compareBy({ !it.mine }, { it.startsAt })).take(max)

    /** Marks the user's teams' matches (the source may not know the profile's teams). */
    fun markMine(matches: List<Match>, profile: Profile): List<Match> = matches.map { m ->
        if (m.mine || profile.isMyTeam(m.home.name) || profile.isMyTeam(m.away.name)) m.copy(mine = true) else m
    }

    /** A match as a card or billboard item; it opens [app]. */
    fun matchItem(match: Match, app: String = MATCH_APP): MediaItem = MediaItem(
        id = "match:${match.id}",
        providerId = "matches",
        title = "${match.home.name} – ${match.away.name}",
        subtitle = match.competitionName,
        kind = Kind.MATCH,
        open = OpenTarget.App(app),
        appPackage = app,
        startsAt = match.startsAt,
        match = match,
    )
}
