package io.shamash.data.youtube

import io.shamash.data.interleave
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget

/** Pure rules for "Recommended for you". SPEC: PRV-07, PRV-08 */
object YouTubeMix {
    const val PROVIDER_ID = "youtube"
    const val APP = "com.google.android.youtube.tv"
    const val PICKS = 20
    private const val SEEDS = 4
    private const val SEEDS_WATCHED = 2
    private const val SEARCH_WORDS = 6

    /** The last 2 videos opened from Shamash, then recent likes: ≤ 4 titles to find similar videos for. */
    fun seeds(watchedTitles: List<String>, likedTitles: List<String>): List<String> =
        (watchedTitles.take(SEEDS_WATCHED) + likedTitles).filter { it.isNotBlank() }.take(SEEDS)

    /** A title's first few words without hashtags, emoji or punctuation: what to search for. */
    fun searchWords(title: String): String =
        title.replace(Regex("#\\S+"), " ")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(SEARCH_WORDS)
            .joinToString(" ")

    /** Each seed's results and the subscriptions' uploads in turns, without what was liked or watched, ≤ 20. */
    fun pick(perSeed: List<List<String>>, uploads: List<String>, exclude: Set<String>): List<String> =
        interleave((perSeed + listOf(uploads)).map { ids -> ids.filter { it.isNotBlank() && it !in exclude } }, PICKS)

    /** The newest uploads first. */
    fun newest(uploads: List<Upload>): List<String> = uploads.sortedByDescending { it.publishedAt }.map { it.videoId }

    /** A video opens in the YouTube app on its watch page. SPEC: PRV-08 */
    fun YouTubeVideo.toItem() = MediaItem(
        id = "$PROVIDER_ID:$id",
        providerId = PROVIDER_ID,
        title = title,
        kind = Kind.VIDEO,
        open = OpenTarget.Video(id),
        subtitle = channel,
        overview = description,
        posterUrl = thumbnail,
        backdropUrl = maxres,
        appPackage = APP,
    )
}
