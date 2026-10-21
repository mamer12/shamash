package io.shamash.data.youtube

import io.shamash.data.Http
import io.shamash.data.Refresh
import io.shamash.data.TimedCache
import io.shamash.data.softly
import io.shamash.data.youtube.YouTubeMix.toItem
import io.shamash.domain.ContentLevel
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.WatchHistory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * "Recommended for you" on YouTube, Shamash's own picks from what the API lets apps read (likes, subscriptions, what
 * was opened from Shamash); YouTube's own recommendations and watch history aren't readable. Signed in only.
 * SPEC: PRV-07, PRV-08, HOME-04
 */
class YouTubeProvider(
    private val http: Http,
    private val auth: YouTubeAuth,
    private val history: WatchHistory?,
    private val isInstalled: (String) -> Boolean,
    clock: () -> Long = System::currentTimeMillis,
) : Provider {
    override val id = YouTubeMix.PROVIDER_ID

    private val cache = TimedCache<Pair<Int, ContentLevel>, List<MediaItem>>(clock, Refresh.every(3 * Refresh.HOUR))

    override fun isEnabled(profile: Profile) = profile.hasYoutubeClient && isInstalled(YouTubeMix.APP)

    override suspend fun shelves(profile: Profile): List<Shelf> {
        val picks = recommended(profile)
        return if (picks.isEmpty()) emptyList() else listOf(Shelf("yt_recs", "youtube_recommended", ShelfStyle.VIDEO, picks))
    }

    override suspend fun featured(profile: Profile): List<MediaItem> = recommended(profile).filter { it.backdropUrl != null }

    override suspend fun search(query: String, profile: Profile): List<MediaItem> {
        if (!isEnabled(profile) || query.isBlank()) return emptyList()
        return softly("YouTube search", emptyList()) {
            val token = auth.accessToken() ?: return@softly emptyList()
            val level = profile.contentLevel
            videos(token, searchIds(token, query.trim(), SEARCH_MAX, level), safeSearched = level != ContentLevel.ALL)
        }
    }

    private suspend fun recommended(profile: Profile): List<MediaItem> {
        if (!isEnabled(profile)) return emptyList()
        val account = auth.account() ?: return emptyList()
        return cache.get(account to profile.contentLevel) {
            softly("YouTube recommendations", emptyList()) {
                auth.accessToken()?.let { fetchRecommended(it, profile.contentLevel) }.orEmpty()
            }
        }
    }

    private suspend fun fetchRecommended(token: String, level: ContentLevel): List<MediaItem> = coroutineScope {
        val watched = watchedVideos()
        val liked = YouTubeJson.videos(api(token, "videos", "part" to "snippet", "myRating" to "like", "maxResults" to "10"))
        val seeds = YouTubeMix.seeds(watched.map { (_, title) -> title }, liked.map { it.title })
        val uploads = async { subscriptionUploads(token) }
        val perSeed = seeds.map { seed ->
            async { softly("YouTube seed search", emptyList()) { searchIds(token, YouTubeMix.searchWords(seed), PER_SEED, level) } }
        }
        val exclude = (watched.map { (id, _) -> id } + liked.map { it.id }).toSet()
        videos(token, YouTubeMix.pick(perSeed.awaitAll(), uploads.await(), exclude))
    }

    /** Videos opened from Shamash, newest first, as (id, title). */
    private suspend fun watchedVideos(): List<Pair<String, String>> =
        history?.items?.first().orEmpty().mapNotNull { item ->
            (item.open as? OpenTarget.Video)?.let { it.youtubeId to item.title }
        }

    /** The newest uploads of up to 25 subscriptions (a channel's uploads playlist is its id with UU for UC). */
    private suspend fun subscriptionUploads(token: String): List<String> = softly("YouTube subscriptions", emptyList()) {
        val subscriptions = api(
            token, "subscriptions", "part" to "snippet", "mine" to "true", "order" to "relevance", "maxResults" to "$CHANNELS",
        )
        val channels = YouTubeJson.channels(subscriptions).filter { it.startsWith("UC") }
        val uploads = coroutineScope { channels.map { async { uploads(token, it) } }.awaitAll().flatten() }
        YouTubeMix.newest(uploads)
    }

    private suspend fun uploads(token: String, channel: String): List<Upload> = softly("YouTube uploads $channel", emptyList()) {
        val playlist = "UU" + channel.removePrefix("UC")
        YouTubeJson.uploads(
            api(token, "playlistItems", "part" to "contentDetails", "maxResults" to "$PER_CHANNEL", "playlistId" to playlist),
        )
    }

    private suspend fun searchIds(token: String, query: String, max: Int, level: ContentLevel): List<String> =
        if (query.isBlank()) emptyList()
        else YouTubeJson.searchIds(
            api(
                token, "search", "part" to "id", "type" to "video", "maxResults" to "$max", "q" to query,
                "safeSearch" to safeSearch(level),   // SPEC: PRF-12
            ),
        )

    /** YouTube's own filter: strict for FAMILY and KIDS, which can't be switched off by the query. */
    private fun safeSearch(level: ContentLevel) = if (level == ContentLevel.ALL) "moderate" else "strict"

    private suspend fun videos(token: String, ids: List<String>, safeSearched: Boolean = false): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val answer = api(token, "videos", "part" to "snippet,contentDetails,status", "id" to ids.joinToString(","))
        val byId = YouTubeJson.videos(answer).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toItem(safeSearched) }
    }

    private suspend fun api(token: String, path: String, vararg params: Pair<String, String>): String {
        val url = "$API/$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.text(url.toString(), mapOf("Authorization" to "Bearer $token"))
    }

    private companion object {
        const val API = "https://www.googleapis.com/youtube/v3"
        const val PER_SEED = 6
        const val CHANNELS = 25
        const val PER_CHANNEL = 2
        const val SEARCH_MAX = 12
    }
}
