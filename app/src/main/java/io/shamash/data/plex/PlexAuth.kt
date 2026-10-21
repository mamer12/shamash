package io.shamash.data.plex

import io.shamash.data.ServerHttp
import io.shamash.data.jLong
import io.shamash.data.jStr
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A sign-in code the user types at plex.tv/link. */
data class PlexPin(val id: Long, val code: String)

/**
 * Plex sign-in by PIN: ask for a code, show it, poll until the user has entered it on another device, then list the
 * account's servers. The account token is a secret: it is returned, never logged. SPEC: PEX-10
 */
class PlexAuth(
    private val server: ServerHttp,
    private val clientId: String,
    private val base: HttpUrl = "https://plex.tv".toHttpUrl(),
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val headers = mapOf(
        "Accept" to "application/json", "X-Plex-Client-Identifier" to clientId,
        "X-Plex-Product" to "Shamash", "X-Plex-Version" to "1",
    )

    suspend fun start(): PlexPin {
        val url = base.newBuilder().addPathSegments("api/v2/pins").addQueryParameter("strong", "false").build()
        val answer = server.json(url, headers, body = "")
        return PlexPin(answer.jLong("id") ?: error("no pin"), answer.jStr("code") ?: error("no code"))
    }

    /** The account token once the code is entered; null when it expires first ([maxWaitMs] or the server says so). */
    suspend fun poll(pin: PlexPin, everyMs: Long = 2_000, maxWaitMs: Long = 15 * 60_000): String? {
        var waited = 0L
        val url = base.newBuilder().addPathSegments("api/v2/pins/${pin.id}").build()
        while (waited <= maxWaitMs) {
            val answer = runCatching { server.json(url, headers) }.getOrNull()
            answer.jStr("authToken")?.let { return it }
            wait(everyMs)
            waited += everyMs
        }
        return null
    }

    suspend fun servers(accountToken: String): List<PlexJson.Server> {
        val url = base.newBuilder().addPathSegments("api/v2/resources").addQueryParameter("includeHttps", "1").build()
        return PlexJson.servers(server.json(url, headers + ("X-Plex-Token" to accountToken)))
    }
}
