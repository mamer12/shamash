package io.shamash.data.youtube

import io.shamash.data.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A sign-in in progress: the code the user types at [verificationUrl] on a phone. SPEC: PRV-06 */
data class DeviceCode(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val intervalSec: Int,
    val expiresAt: Long,
)

/** Google's answer to a token request. */
sealed interface TokenAnswer {
    data class Granted(val accessToken: String, val refreshToken: String?, val expiresInSec: Long) : TokenAnswer
    /** The user hasn't approved yet (or asks to poll slower). */
    data object Pending : TokenAnswer
    data class Rejected(val error: String) : TokenAnswer
}

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val description: String,
    val thumbnail: String?,
    val maxres: String?,
)

data class Upload(val videoId: String, val publishedAt: String)

/** Pure parsers of Google's OAuth and YouTube Data API answers. */
object YouTubeJson {
    private const val MIN_INTERVAL_SEC = 5

    @Serializable
    private data class Device(
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String = "https://www.google.com/device",
        @SerialName("device_code") val deviceCode: String,
        val interval: Int = MIN_INTERVAL_SEC,
        @SerialName("expires_in") val expiresIn: Long = 1800,
    )

    @Serializable
    private data class Token(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    @Serializable private data class Items<T>(val items: List<T> = emptyList())
    @Serializable private data class SearchHit(val id: SearchId? = null)
    @Serializable private data class SearchId(val videoId: String? = null)
    @Serializable private data class Video(val id: String, val snippet: Snippet? = null)
    @Serializable private data class Thumb(val url: String)
    @Serializable private data class Subscription(val snippet: SubscriptionSnippet? = null)
    @Serializable private data class SubscriptionSnippet(val resourceId: ResourceId? = null)
    @Serializable private data class ResourceId(val channelId: String? = null)
    @Serializable private data class PlaylistItem(val contentDetails: UploadDetails? = null)
    @Serializable private data class UploadDetails(val videoId: String? = null, val videoPublishedAt: String? = null)

    @Serializable
    private data class Snippet(
        val title: String = "",
        val channelTitle: String = "",
        val description: String = "",
        val thumbnails: Map<String, Thumb> = emptyMap(),
    )

    fun deviceCode(text: String, now: Long): DeviceCode {
        val d = json.decodeFromString<Device>(text)
        val interval = maxOf(MIN_INTERVAL_SEC, d.interval)
        return DeviceCode(d.userCode, d.verificationUrl, d.deviceCode, interval, now + d.expiresIn * 1000)
    }

    fun token(text: String): TokenAnswer {
        val error = json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        if (error != null) return if (error in WAITING) TokenAnswer.Pending else TokenAnswer.Rejected(error)
        val t = json.decodeFromString<Token>(text)
        val access = t.accessToken ?: return TokenAnswer.Rejected("no_access_token")
        return TokenAnswer.Granted(access, t.refreshToken, t.expiresIn)
    }

    /** `search?part=id&type=video`: the video ids in YouTube's order. */
    fun searchIds(text: String): List<String> =
        json.decodeFromString<Items<SearchHit>>(text).items.mapNotNull { it.id?.videoId }

    /** `videos?part=snippet`: medium (16:9) thumbnail for cards, maxres (when there is one) as wide art. SPEC: PRV-07 */
    fun videos(text: String): List<YouTubeVideo> =
        json.decodeFromString<Items<Video>>(text).items.mapNotNull { v ->
            val s = v.snippet ?: return@mapNotNull null
            YouTubeVideo(
                id = v.id,
                title = s.title,
                channel = s.channelTitle,
                description = s.description.lineSequence().firstOrNull().orEmpty(),
                thumbnail = (s.thumbnails["medium"] ?: s.thumbnails["default"])?.url,
                maxres = s.thumbnails["maxres"]?.url,
            )
        }

    /** `subscriptions?mine=true`: the channels' ids. */
    fun channels(text: String): List<String> =
        json.decodeFromString<Items<Subscription>>(text).items.mapNotNull { it.snippet?.resourceId?.channelId }

    /** `playlistItems?part=contentDetails`. */
    fun uploads(text: String): List<Upload> =
        json.decodeFromString<Items<PlaylistItem>>(text).items.mapNotNull { item ->
            val d = item.contentDetails ?: return@mapNotNull null
            d.videoId?.let { Upload(it, d.videoPublishedAt.orEmpty()) }
        }

    private val WAITING = setOf("authorization_pending", "slow_down")
}
