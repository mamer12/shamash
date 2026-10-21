package io.shamash.data.live

import io.shamash.data.HttpPrivacy
import io.shamash.data.ServerHttp
import io.shamash.data.jArr
import io.shamash.data.jLong
import io.shamash.data.jObj
import io.shamash.data.jStr
import io.shamash.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A followed streamer who is live now. */
data class LiveStream(
    val service: String,     // "twitch" or "kick"
    val login: String,
    val name: String,
    val title: String,
    val category: String,
    val viewers: Long,
    val thumbnail: String?,
)

/** Reads the public answers of Twitch Helix `streams` and Kick's channel page JSON. SPEC: PEX-25 */
object LiveJson {
    private const val THUMB_W = "1280"
    private const val THUMB_H = "720"

    /** Helix `GET /helix/streams`: only `type == live` entries. */
    fun twitch(root: JsonElement): List<LiveStream> = root.jArr("data").mapNotNull { s ->
        val typed = (s as? kotlinx.serialization.json.JsonObject)?.containsKey("type") == true
        if (typed && s.jStr("type") != "live") return@mapNotNull null      // "" is a rerun or an error
        val login = s.jStr("user_login")?.lowercase() ?: return@mapNotNull null
        LiveStream(
            "twitch", login, s.jStr("user_name") ?: login, s.jStr("title").orEmpty().take(140), s.jStr("game_name").orEmpty(),
            s.jLong("viewer_count") ?: 0, s.jStr("thumbnail_url")?.replace("{width}", THUMB_W)?.replace("{height}", THUMB_H)?.takeIf { it.startsWith("https://") },
        )
    }

    /** Kick `GET /api/v2/channels/<slug>`: live when `livestream` is an object with `is_live` not false. */
    fun kick(slug: String, root: JsonElement): LiveStream? {
        val live = root.jObj("livestream") ?: return null
        if (live.jStr("is_live") == "false") return null
        val name = root.jObj("user").jStr("username") ?: slug
        return LiveStream(
            "kick", slug.lowercase(), name, live.jStr("session_title").orEmpty().take(140),
            live.jArr("categories").firstOrNull().jStr("name").orEmpty(), live.jLong("viewer_count") ?: 0,
            live.jObj("thumbnail").jStr("url")?.takeIf { it.startsWith("https://") },
        )
    }

    /** `client_credentials` answer: the app token and its lifetime in seconds. */
    fun appToken(body: String): Pair<String, Long>? = runCatching {
        val root = json.parseToJsonElement(body)
        val token = root.jStr("access_token") ?: return null
        token to (root.jLong("expires_in") ?: 3600)
    }.getOrNull()
}

/**
 * Twitch through Helix with an app token from the user's own Client ID and secret (client credentials: no account
 * sign-in). The token lives in memory only; requests carrying it are never cached. SPEC: PEX-25, PEX-04
 */
class TwitchApi(
    private val client: OkHttpClient,
    private val server: ServerHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val api: HttpUrl = "https://api.twitch.tv/".toHttpUrl(),
    private val auth: HttpUrl = "https://id.twitch.tv/".toHttpUrl(),
) {
    private val lock = Mutex()
    private var token: Triple<String, String, Long>? = null   // client id, token, valid until

    /** Which of [logins] are live; empty without a client or when nobody is. Throws on I/O. */
    suspend fun live(logins: List<String>, clientId: String?, secret: String?): List<LiveStream> {
        if (logins.isEmpty() || clientId == null || secret == null) return emptyList()
        val bearer = appToken(clientId, secret) ?: return emptyList()
        return logins.chunked(MAX_PER_CALL).flatMap { part ->
            val url = api.newBuilder().addPathSegments("helix/streams").apply { part.forEach { addQueryParameter("user_login", it) } }.build()
            LiveJson.twitch(server.json(url, mapOf("Client-Id" to clientId, "Authorization" to "Bearer $bearer"), TIMEOUT_MS, MAX_BYTES))
        }
    }

    private suspend fun appToken(clientId: String, secret: String): String? = lock.withLock {
        token?.takeIf { it.first == clientId && clock() < it.third }?.let { return@withLock it.second }
        val body = FormBody.Builder().add("client_id", clientId).add("client_secret", secret).add("grant_type", "client_credentials").build()
        val request = Request.Builder().url(auth.newBuilder().addPathSegments("oauth2/token").build()).post(body)
            .header(HttpPrivacy.PRIVATE, "1").build()
        val answer = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { r ->
                if (r.code == 400 || r.code == 401 || r.code == 403) return@use null
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} for ${request.url.host}")
                LiveJson.appToken(r.body?.string().orEmpty())
            }
        } ?: return@withLock null.also { token = null }
        token = Triple(clientId, answer.first, clock() + (answer.second - 60).coerceAtLeast(60) * 1000)
        answer.first
    }

    fun forget() { token = null }

    private companion object {
        const val MAX_PER_CALL = 100
        const val TIMEOUT_MS = 8_000L
        const val MAX_BYTES = 1L * 1024 * 1024
    }
}

/** Kick's public channel answer, one per followed slug. SPEC: PEX-25 */
class KickApi(private val server: ServerHttp, private val base: HttpUrl = "https://kick.com/".toHttpUrl()) {
    suspend fun live(slug: String): LiveStream? {
        val url = base.newBuilder().addPathSegments("api/v2/channels").addPathSegment(slug).build()
        return LiveJson.kick(slug, server.json(url, mapOf("Accept" to "application/json"), 6_000, 512L * 1024))
    }
}
