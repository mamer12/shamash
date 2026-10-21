package io.shamash.data.trakt

import io.shamash.data.json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

private fun body(vararg pairs: Pair<String, String>): String =
    buildJsonObject { pairs.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString()

/**
 * Trakt (api.trakt.tv): device code sign-in with the user's own client, refresh tokens, watchlist and history, and
 * optional "watched" write-back of movies. SPEC: PEX-26
 */
class TraktApi(
    private val http: TrackerHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val base: HttpUrl = "https://api.trakt.tv/".toHttpUrl(),
) : TrackerApi {
    private fun url(path: String, query: Map<String, String> = emptyMap()) =
        base.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    private fun headers(client: Client, session: Session? = null) = buildMap {
        put("trakt-api-version", "2")
        put("trakt-api-key", client.id)
        session?.let { put("Authorization", "Bearer ${it.access}") }
    }

    override suspend fun start(client: Client): DeviceCode {
        val (code, text) = http.call(url("oauth/device/code"), headers(client), body("client_id" to client.id))
        if (code != 200) throw IOException("device code: HTTP $code")
        return TrackerJson.deviceCode(text, clock()) ?: throw IOException("device code: unreadable")
    }

    override suspend fun poll(client: Client, code: DeviceCode): PollAnswer {
        val form = body("code" to code.deviceCode, "client_id" to client.id, "client_secret" to client.secret.orEmpty())
        val (status, text) = http.call(url("oauth/device/token"), headers(client), form)
        return when (status) {
            200 -> TrackerJson.session(text, clock())?.let { PollAnswer.Granted(it) } ?: PollAnswer.Failed
            400 -> PollAnswer.Pending
            429 -> PollAnswer.SlowDown
            else -> PollAnswer.Failed
        }
    }

    override suspend fun refresh(client: Client, session: Session): RefreshAnswer {
        val refresh = session.refresh ?: return RefreshAnswer.SignedOut
        val form = body(
            "refresh_token" to refresh, "client_id" to client.id, "client_secret" to client.secret.orEmpty(),
            "redirect_uri" to "urn:ietf:wg:oauth:2.0:oob", "grant_type" to "refresh_token",
        )
        val (status, text) = http.call(url("oauth/token"), headers(client), form)
        return when {
            status == 200 -> TrackerJson.session(text, clock())?.let { RefreshAnswer.Ok(it.copy(refresh = it.refresh ?: refresh)) } ?: RefreshAnswer.Unavailable
            TrackerJson.error(text) == "invalid_grant" || status == 401 -> RefreshAnswer.SignedOut
            else -> RefreshAnswer.Unavailable
        }
    }

    override suspend fun library(client: Client, session: Session, sinceMs: Long): Library {
        val list = get(url("sync/watchlist", mapOf("extended" to "full")), client, session)
        val query = mutableMapOf("extended" to "full", "limit" to "50")
        if (sinceMs > 0) query["start_at"] = TrackerJson.iso(sinceMs + 1)
        val history = get(url("sync/history", query), client, session)
        return Library(TrackerJson.traktWatchlist(list), TrackerJson.traktHistory(history).filter { (it.at ?: 0) > sinceMs })
    }

    private suspend fun get(url: HttpUrl, client: Client, session: Session): String {
        val (status, text) = http.call(url, headers(client, session))
        if (status == 401) throw TrackerUnauthorized()
        if (status !in 200..299) throw IOException("HTTP $status for ${url.host}")
        return text
    }

    override suspend fun pushWatched(client: Client, session: Session, movies: List<Pair<Int, Long>>): Boolean {
        if (movies.isEmpty()) return true
        val (status, _) = http.call(url("sync/history"), headers(client, session), TrackerJson.historyBody(movies))
        if (status == 401) throw TrackerUnauthorized()
        return status in 200..299
    }
}

/**
 * Simkl (api.simkl.com): PIN sign-in (a code the user enters at simkl.com/pin), tokens that don't expire, the whole
 * library in one call, optional "watched" write-back of movies. SPEC: PEX-26
 */
class SimklApi(
    private val http: TrackerHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val base: HttpUrl = "https://api.simkl.com/".toHttpUrl(),
) : TrackerApi {
    private fun url(path: String, client: Client, query: Map<String, String> = emptyMap()) =
        base.newBuilder().addPathSegments(path).addQueryParameter("client_id", client.id)
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    private fun headers(client: Client, session: Session? = null) = buildMap {
        put("simkl-api-key", client.id)
        session?.let { put("Authorization", "Bearer ${it.access}") }
    }

    override suspend fun start(client: Client): DeviceCode {
        val (code, text) = http.call(url("oauth/pin", client), headers(client))
        if (code != 200) throw IOException("pin: HTTP $code")
        return TrackerJson.deviceCode(text, clock()) ?: throw IOException("pin: unreadable")
    }

    override suspend fun poll(client: Client, code: DeviceCode): PollAnswer {
        val (status, text) = http.call(url("oauth/pin/${code.userCode}", client), headers(client))
        if (status == 429) return PollAnswer.SlowDown
        if (status != 200) return PollAnswer.Failed
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull()
        val result = (root as? kotlinx.serialization.json.JsonObject)?.get("result")?.let { (it as? JsonPrimitive)?.content }
        return when {
            result == "OK" -> TrackerJson.session(text, clock())?.let { PollAnswer.Granted(it) } ?: PollAnswer.Pending
            text.contains("slow down", ignoreCase = true) -> PollAnswer.SlowDown
            else -> PollAnswer.Pending
        }
    }

    /** Simkl tokens don't expire; a refused one shows up as a 401 on the next call. */
    override suspend fun refresh(client: Client, session: Session): RefreshAnswer = RefreshAnswer.SignedOut

    override suspend fun library(client: Client, session: Session, sinceMs: Long): Library {
        val target = url("sync/all-items/", client, mapOf("extended" to "full"))
        val (status, text) = http.call(target, headers(client, session))
        if (status == 401) throw TrackerUnauthorized()
        if (status !in 200..299) throw IOException("HTTP $status for ${target.host}")
        return TrackerJson.simklLibrary(text, sinceMs)
    }

    override suspend fun pushWatched(client: Client, session: Session, movies: List<Pair<Int, Long>>): Boolean {
        if (movies.isEmpty()) return true
        val (status, _) = http.call(url("sync/history", client), headers(client, session), TrackerJson.historyBody(movies))
        if (status == 401) throw TrackerUnauthorized()
        return status in 200..299
    }
}
