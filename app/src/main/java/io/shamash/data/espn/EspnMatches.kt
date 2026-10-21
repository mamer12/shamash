package io.shamash.data.espn

import io.shamash.data.Http
import io.shamash.data.Logger
import io.shamash.data.Refresh
import io.shamash.data.softly
import io.shamash.domain.Competitions
import io.shamash.domain.Follow
import io.shamash.domain.CompetitionDef
import io.shamash.domain.Match
import io.shamash.domain.MatchFollows
import io.shamash.domain.MatchSource
import io.shamash.domain.Profile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * The week's matches of the user's competitions from ESPN's public scoreboard, kept in memory: the whole week every
 * 3 h, today's boards every minute while one of the user's teams plays. SPEC: MC-01, PRV-11, HOME-14
 */
class EspnMatches(
    private val http: Http,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : MatchSource {
    private data class Key(val leagues: List<String>, val follows: Set<Follow>)

    private class Week(val key: Key, val matches: List<Match>, val fullAt: Long, val checkedAt: Long)

    private val lock = Mutex()
    private var week: Week? = null

    override suspend fun matches(profile: Profile): List<Match> = lock.withLock {
        val key = Key(MatchRules.leagues(profile), MatchFollows.all(profile))
        val now = clock()
        val cached = week?.takeIf { it.key == key }
        val fresh = when {
            cached == null || now - cached.fullAt !in 0 until MatchRules.fullEvery(cached.matches) ->
                fetchWeek(key, profile, cached, now)
            now - cached.checkedAt >= MatchRules.checkEvery(cached.matches, now) -> refreshToday(cached, profile, now)
            else -> cached
        }
        week = fresh
        fresh.matches
    }

    private suspend fun fetchWeek(key: Key, profile: Profile, cached: Week?, now: Long): Week {
        val boards = boards(key.leagues, MatchRules.DAYS, profile, now)
        if (boards.all { it == null }) {
            Logger.w("ESPN didn't answer")
            return Week(key, cached?.matches.orEmpty(), now - MatchRules.FULL_EVERY + Refresh.RETRY_EMPTY, now)
        }
        return Week(key, MatchRules.order(boards.filterNotNull().flatten()), now, now)
    }

    private suspend fun refreshToday(cached: Week, profile: Profile, now: Long): Week {
        val today = boards(cached.key.leagues, 1, profile, now).filterNotNull().flatten()
        return Week(cached.key, MatchRules.order(today + cached.matches), cached.fullAt, now)
    }

    /** One board per league and day from today (a whole range at once for F1 and tennis); null where ESPN failed. */
    private suspend fun boards(leagues: List<String>, days: Int, profile: Profile, now: Long): List<List<Match>?> =
        coroutineScope {
            val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
            leagues.flatMap { league ->
                val def = Competitions.resolve(league)
                SportBoards.dates(def.sport, today, days).map { dates -> async { board(def, dates, profile) } }
            }.awaitAll()
        }

    private suspend fun board(def: CompetitionDef, dates: String, profile: Profile): List<Match>? =
        softly<List<Match>?>("ESPN ${def.id} $dates", null) {
            val text = http.text("$API${def.path}/scoreboard?dates=$dates")
            val followed = { m: Match -> m.copy(mine = m.mine || MatchFollows.sportFollowed(profile, m.sport)) }
            SportBoards.parse(def, text, MatchFollows.mine(profile)).map(followed)
        }

        private companion object {
        const val API = "https://site.api.espn.com/apis/site/v2/sports/"
    }
}

/** Pure Match Centre rules. SPEC: MC-01, HOME-14 */
object MatchRules {
    const val DAYS = 7
    const val FULL_EVERY = 3 * Refresh.HOUR
    private val DEFAULT_LEAGUES = listOf("uefa.champions", "eng.1", "esp.1")

    fun leagues(profile: Profile): List<String> =
        profile.competitions.filter { it.isNotBlank() }.sorted().ifEmpty { DEFAULT_LEAGUES }

    /** Each match once (the first copy wins), the user's teams first, then by kick-off. */
    fun order(matches: List<Match>): List<Match> =
        matches.distinctBy { it.id }.sortedWith(compareBy({ !it.mine }, { it.startsAt }))

    fun fullEvery(matches: List<Match>): Long = if (matches.isEmpty()) Refresh.RETRY_EMPTY else FULL_EVERY

    /** See [Polling]: 60 s live (30 s for F1), 10 min before a follow's match within 3 h, else the full refresh. */
    fun checkEvery(matches: List<Match>, now: Long): Long = Polling.every(matches, now)
}
