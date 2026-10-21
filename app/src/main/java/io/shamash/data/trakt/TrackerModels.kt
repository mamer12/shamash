package io.shamash.data.trakt

import io.shamash.data.HttpPrivacy
import io.shamash.domain.Kind
import io.shamash.domain.MyListEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** What the TV shows while the user approves on a phone; [deviceCode] is never shown or logged. SPEC: PEX-26 */
data class DeviceCode(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val intervalSec: Int,
    val expiresAt: Long,
) {
    override fun toString() = "DeviceCode(user=$userCode, url=$verificationUrl, device=redacted)"
}

/** A tracker sign-in. Kept as one secret per profile; [toString] redacts. */
@Serializable
data class Session(val access: String, val refresh: String? = null, val expiresAt: Long? = null) {
    override fun toString() = "Session(redacted)"
}

sealed interface PollAnswer {
    data object Pending : PollAnswer
    data object SlowDown : PollAnswer
    data object Failed : PollAnswer          // expired, denied or already used: start again
    data class Granted(val session: Session) : PollAnswer
}

sealed interface RefreshAnswer {
    data class Ok(val session: Session) : RefreshAnswer
    data object SignedOut : RefreshAnswer   // invalid_grant: the sign-in is gone
    data object Unavailable : RefreshAnswer  // try later
}

/** A movie or show on the user's tracker. [at] is when it was listed or watched (epoch ms). */
data class TrackedTitle(
    val kind: Kind,
    val key: String,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
    val at: Long? = null,
)

/** One sync's answer: the watchlist, and what was watched since the last sync (newest first). */
data class Library(val watchlist: List<TrackedTitle>, val watched: List<TrackedTitle>)

/** The access token was refused (HTTP 401): refresh once, then sign out. */
class TrackerUnauthorized : IOException("unauthorized")

/** The app's own client for the tracker, as the user entered it (like YouTube's). */
data class Client(val id: String, val secret: String?) {
    override fun toString() = "Client(redacted)"
}

/** A tracker's API: device sign-in, refresh, read the library, write back finished movies. SPEC: PEX-26 */
interface TrackerApi {
    suspend fun start(client: Client): DeviceCode
    suspend fun poll(client: Client, code: DeviceCode): PollAnswer
    suspend fun refresh(client: Client, session: Session): RefreshAnswer
    suspend fun library(client: Client, session: Session, sinceMs: Long): Library
    /** Marks movies (TMDB id, watched at) as watched; true when the tracker took them. */
    suspend fun pushWatched(client: Client, session: Session, movies: List<Pair<Int, Long>>): Boolean
}

/** What a tracker add-on keeps per profile besides the sign-in. */
@Serializable
data class TrackerState(
    val list: List<MyListEntry> = emptyList(),
    val dismissed: List<String> = emptyList(),
    val since: Long = 0,
    val pushedUntil: Long = 0,
    val writeBack: Boolean = false,
)

/**
 * The tracker's requests: never cached (they carry tokens), bounded in size, answered as (status, body) because the
 * device flow speaks in status codes. Errors name the host only. SPEC: PEX-04
 */
class TrackerHttp(private val client: OkHttpClient) {
    suspend fun call(url: HttpUrl, headers: Map<String, String> = emptyMap(), jsonBody: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header(HttpPrivacy.PRIVATE, "1").apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (jsonBody != null) post(jsonBody.toRequestBody("application/json".toMediaType()))
            }.build()
            client.newCall(request).execute().use { r ->
                val body = r.body ?: throw IOException("empty answer from ${url.host}")
                if (body.contentLength() > MAX_BYTES) throw IOException("answer too large from ${url.host}")
                val bytes = body.source().let { src -> src.request(MAX_BYTES + 1); src.buffer.size }
                if (bytes > MAX_BYTES) throw IOException("answer too large from ${url.host}")
                r.code to body.string()
            }
        }

    companion object {
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
