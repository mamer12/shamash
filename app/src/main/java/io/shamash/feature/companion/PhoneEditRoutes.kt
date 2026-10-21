package io.shamash.feature.companion

import io.shamash.data.Redact
import io.shamash.data.myfeed.FeedStore
import io.shamash.feature.companion.PhoneApi.Companion.answer
import io.shamash.feature.companion.PhoneApi.Companion.fail
import io.shamash.feature.companion.PhoneApi.Companion.ok

/**
 * The active profile's rows, teams, feeds and queue, edited from the phone once [PhoneApi] has checked the PINs.
 * Every save is validated whole first; a refused one changes nothing. SPEC: CMP-07, CMP-09, CMP-10, CMP-11
 */
internal class PhoneEditRoutes(private val backend: PhoneBackend) {
    suspend fun route(r: HttpRequest): HttpReply {
        val get = r.method == "GET"
        return when (r.path) {
            ApiPaths.ROWS -> if (get) rows() else saveRows(r.body)
            ApiPaths.TEAMS -> if (get) teams() else saveTeams(r.body)
            ApiPaths.FEEDS -> if (get) feeds() else feed(r.body)
            ApiPaths.QUEUE -> queue(r.body)
            else -> fail(404, "unknown")
        }
    }

    private suspend fun rows(): HttpReply =
        ok(RowsOut.serializer(), RowsCheck.model(backend.rowChoices(), backend.active().layout))

    /** SPEC: CMP-07 */
    private suspend fun saveRows(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(RowsIn.serializer(), body)
        val prefs = RowsCheck.validate(input.rows) ?: return fail(400, "rows_invalid")
        val p = backend.active()
        backend.saveProfile(p.copy(layout = RowsCheck.apply(p.layout, prefs)), Changed.ROWS)
        return answer(Answer(true, "saved"))
    }

    private suspend fun teams(): HttpReply {
        val p = backend.active()
        return ok(TeamsOut.serializer(), TeamsCheck.model(p.teams, p.competitions))
    }

    /** SPEC: CMP-09 */
    private suspend fun saveTeams(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(TeamsIn.serializer(), body)
        val p = backend.active()
        val (teams, competitions) = TeamsCheck.validate(input, p.competitions) ?: return fail(400, "teams_invalid")
        backend.saveProfile(p.copy(teams = teams, competitions = competitions), Changed.TEAMS)
        return answer(Answer(true, "saved"))
    }

    private suspend fun feeds(): HttpReply {
        val list = backend.feeds() ?: return fail(404, "no_feeds")
        val out = list.map { FeedOut(it.id, it.title, Redact.host(it.url), it.kind, it.style, it.level) }
        return ok(FeedsOut.serializer(), FeedsOut(out, FeedStore.MAX))
    }

    /** add / remove / try. SPEC: CMP-10 */
    private suspend fun feed(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(FeedIn.serializer(), body)
        val list = backend.feeds() ?: return fail(404, "no_feeds")
        if (input.action == REMOVE) {
            val id = input.id ?: return fail(400, "invalid")
            if (list.none { it.id == id }) return fail(404, FeedRefusal.MISSING.code)
            backend.saveFeeds(list.filterNot { it.id == id })
            return answer(Answer(true, "removed"))
        }
        if (input.action != ADD && input.action != TRY) return fail(400, "invalid")
        if (input.action == ADD && list.size >= FeedStore.MAX) return fail(409, FeedRefusal.FULL.code)
        val id = "f" + backend.now().toString(Character.MAX_RADIX)
        val feed = when (val checked = FeedCheck.feed(input, id, backend::resolve)) {
            is FeedCheck.Result.Refused -> return fail(400, checked.why.code)
            is FeedCheck.Result.Ok -> checked.feed
        }
        if (input.action == TRY) {
            val titles = runCatching { backend.tryFeed(feed) }.getOrNull() ?: return fail(502, "feed_unreadable")
            return answer(Answer(true, "tried", titles = titles.take(TRY_COUNT)))
        }
        backend.saveFeeds(list + feed)
        return answer(Answer(true, "added"))
    }

    /** "Add to queue" for the active profile. SPEC: CMP-11 */
    private suspend fun queue(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(QueueIn.serializer(), body)
        val p = backend.active()
        if (PhoneApi.savesNothing(p.kind)) return fail(403, "refused")
        val entry = QueueOps.entry(input.text, backend.now()) ?: return fail(400, "queue_invalid")
        return if (backend.addToQueue(p.id, entry)) answer(Answer(true, "queued")) else fail(500, "failed")
    }

    private companion object {
        const val ADD = "add"
        const val REMOVE = "remove"
        const val TRY = "try"
        const val TRY_COUNT = 3
    }
}
