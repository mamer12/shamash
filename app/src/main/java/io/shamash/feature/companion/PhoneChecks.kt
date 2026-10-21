package io.shamash.feature.companion

import io.shamash.data.HttpPolicy
import io.shamash.data.ServerUrl
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.Competitions
import io.shamash.domain.Layout
import io.shamash.domain.LayoutOps
import io.shamash.domain.RowPref
import java.net.InetAddress
import java.util.Locale

/** Free text from the phone: no control characters (a title is shown as-is on the TV, LAY-06). */
internal fun plain(text: String): Boolean = text.none { it.isISOControl() || it == ' ' || it == ' ' }

/**
 * The row editor model in and out. A save is a full replacement list, refused whole when any part is off: more than
 * [MAX_ROWS] rows, a repeated or odd id, a title over [LayoutOps.TITLE_MAX] characters. Saved prefs for rows the list
 * does not name are kept (a provider may come back, LAY-01). Pure. SPEC: CMP-07, LAY-02
 */
object RowsCheck {
    const val MAX_ROWS = 60
    const val MAX_ID = 120
    private const val MAX_ORDER = 1_000

    fun model(choices: List<RowChoice>, layout: Layout): RowsOut {
        val titles = choices.associate { it.id to it.defaultTitle }
        val placed = LayoutOps.arrange(choices.map { it.id }, layout)
        val rows = placed.mapIndexed { i, p ->
            RowOut(p.id, p.pref?.title, titles[p.id].orEmpty().ifEmpty { p.id }, p.hidden, p.pinned, p.pref?.order ?: i)
        }
        return RowsOut(rows, MAX_ROWS, LayoutOps.TITLE_MAX)
    }

    /** The prefs to save, or null when the list is not acceptable. */
    fun validate(rows: List<RowIn>): List<RowPref>? {
        if (rows.size > MAX_ROWS) return null
        if (rows.map { it.id }.toSet().size != rows.size) return null
        return rows.map { r ->
            val title = r.title?.trim()
            if (r.id.isBlank() || r.id.length > MAX_ID || !plain(r.id)) return null
            if (title != null && (title.length > LayoutOps.TITLE_MAX || !plain(title))) return null
            if (r.order !in -MAX_ORDER..MAX_ORDER) return null
            RowPref(r.id, r.hidden, r.pinned, title?.ifEmpty { null }, r.order)
        }
    }

    fun apply(layout: Layout, prefs: List<RowPref>): Layout {
        val named = prefs.map { it.rowId }.toSet()
        return layout.copy(rows = prefs + layout.rows.filter { it.rowId !in named })
    }
}

/**
 * Teams and competitions from the phone: at most [MAX_TEAMS] names of at most [MAX_NAME] characters (blank lines and
 * repeats dropped); competitions only from the catalog (ESPN codes and MC-07 additions) or ones the profile already
 * follows. Pure. SPEC: CMP-09, MC-07
 */
object TeamsCheck {
    const val MAX_TEAMS = 20
    const val MAX_NAME = 40

    fun model(teams: Set<String>, competitions: Set<String>): TeamsOut = TeamsOut(
        teams.toList(), competitions.toList(),
        Competitions.catalog.map { CompetitionOut(it.id, it.name, it.sport.name) }, MAX_TEAMS, MAX_NAME,
    )

    /** (teams, competitions) to save, or null when the lists are not acceptable. */
    fun validate(input: TeamsIn, current: Set<String>): Pair<Set<String>, Set<String>>? {
        val names = input.teams.map { it.trim() }.filter { it.isNotEmpty() }
        if (names.size > MAX_TEAMS || names.any { it.length > MAX_NAME || !plain(it) }) return null
        val known = Competitions.catalog.map { it.id }.toSet() + current
        if (input.competitions.size > known.size || input.competitions.any { it !in known }) return null
        val teams = LinkedHashMap<String, String>()
        names.forEach { teams.putIfAbsent(it.lowercase(Locale.ROOT), it) }
        return teams.values.toCollection(LinkedHashSet()) to input.competitions.toCollection(LinkedHashSet())
    }
}

/** Why a feed from the phone was refused. */
enum class FeedRefusal(val code: String) {
    TITLE("feed_title_bad"), ADDRESS("feed_url_bad"), PRIVATE("feed_private"), FULL("feed_full"), MISSING("feed_missing")
}

/**
 * A feed from the phone (PEX-20): a title, an https address (http only with its own switch) on the internet, never
 * a private or local one (CMP-10: the phone must not turn the TV into a LAN scanner). Pure; [resolve] is the DNS
 * step, injected. SPEC: CMP-10, PEX-22
 */
object FeedCheck {
    const val MAX_TITLE = 60
    const val MAX_URL = 2_000

    fun feed(input: FeedIn, id: String, resolve: ((String) -> List<InetAddress>)?): Result {
        val title = input.title.trim()
        if (title.isEmpty() || title.length > MAX_TITLE || !plain(title)) return Result.Refused(FeedRefusal.TITLE)
        if (input.url.length > MAX_URL || !plain(input.url)) return Result.Refused(FeedRefusal.ADDRESS)
        val url = ServerUrl.parse(input.url, if (input.allowHttp) HttpPolicy.ANY else HttpPolicy.NONE)
            ?: return Result.Refused(FeedRefusal.ADDRESS)
        if (LanGuard.isLocal(url.host, resolve)) return Result.Refused(FeedRefusal.PRIVATE)
        return Result.Ok(
            UserFeed(id, title, url.toString(), input.kind, null, input.style, input.level, input.allowHttp),
        )
    }

    sealed interface Result {
        data class Ok(val feed: UserFeed) : Result
        data class Refused(val why: FeedRefusal) : Result
    }
}
