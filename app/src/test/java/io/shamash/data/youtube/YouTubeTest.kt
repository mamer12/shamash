package io.shamash.data.youtube

import io.shamash.data.Fixtures
import io.shamash.data.youtube.YouTubeMix.toItem
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTest {
    @Test
    fun PRV_06_device_code_gives_what_the_tv_shows() {
        val code = YouTubeJson.deviceCode(Fixtures.read("youtube/device_code.json"), now = 1_000L)

        assertEquals("GQVQ-JKEC", code.userCode)
        assertEquals("https://www.google.com/device", code.verificationUrl)
        assertEquals(5, code.intervalSec)
        assertEquals(1_000L + 1_800_000L, code.expiresAt)
    }

    @Test
    fun PRV_06_token_answers_granted_pending_and_revoked() {
        val granted = YouTubeJson.token(Fixtures.read("youtube/token_granted.json"))
        assertEquals(TokenAnswer.Granted("ya29.example-access", "1//example-refresh", 3599), granted)
        assertEquals(TokenAnswer.Pending, YouTubeJson.token(Fixtures.read("youtube/token_pending.json")))
        assertEquals(TokenAnswer.Pending, YouTubeJson.token("{\"error\":\"slow_down\"}"))
        assertEquals(TokenAnswer.Rejected("invalid_grant"), YouTubeJson.token(Fixtures.read("youtube/token_invalid_grant.json")))
    }

    @Test
    fun PRV_07_seeds_are_two_watched_then_likes_at_most_four() {
        val seeds = YouTubeMix.seeds(listOf("w1", "w2", "w3"), listOf("l1", "", "l2", "l3"))
        assertEquals(listOf("w1", "w2", "l1", "l2"), seeds)
    }

    @Test
    fun PRV_07_search_words_drop_hashtags_and_symbols() {
        val words = YouTubeMix.searchWords("lofi hip hop radio 📚 beats to relax/study to #lofi #chill")
        assertEquals("lofi hip hop radio beats to", words)
    }

    @Test
    fun PRV_07_picks_interleave_seeds_and_uploads_without_liked_or_watched() {
        val picks = YouTubeMix.pick(
            perSeed = listOf(listOf("a1", "a2", "liked"), listOf("b1", "a1")),
            uploads = listOf("u1", "u2"),
            exclude = setOf("liked"),
        )
        assertEquals(listOf("a1", "b1", "u1", "a2", "u2"), picks)
    }

    @Test
    fun PRV_07_picks_stop_at_20() {
        val lists = (1..5).map { s -> (1..10).map { "s$s-$it" } }
        assertEquals(YouTubeMix.PICKS, YouTubeMix.pick(lists, emptyList(), emptySet()).size)
    }

    @Test
    fun PRV_07_subscriptions_uploads_newest_first() {
        val channels = YouTubeJson.channels(Fixtures.read("youtube/subscriptions.json"))
        assertEquals(listOf("UCSJ4gkVC6NrvII8umztf0Ow", "UCHnyfMqiRRG1u-2MsSQLbXA"), channels)

        val uploads = YouTubeJson.uploads(Fixtures.read("youtube/playlist_items.json")) + Upload("old", "2020-01-01T00:00:00Z")
        assertEquals(listOf("wXSbAW0B2FQ", "il_MMI2MOYM", "old"), YouTubeMix.newest(uploads))
    }

    @Test
    fun PRV_07_maxres_thumbnail_is_the_wide_art() {
        val videos = YouTubeJson.videos(Fixtures.read("youtube/videos.json"))

        assertEquals(4, videos.size)
        assertEquals("https://i.ytimg.com/vi/rFZHOHl-L8A/maxresdefault_live.jpg", videos[0].maxres)
        assertTrue(videos[0].thumbnail!!.contains("mqdefault"))
        assertNull("the fixture's last video has no maxres", videos[3].maxres)
    }

    @Test
    fun PRV_08_videos_open_in_the_youtube_app() {
        val item = YouTubeJson.videos(Fixtures.read("youtube/videos.json")).first().toItem()

        assertEquals(OpenTarget.Video("rFZHOHl-L8A"), item.open)
        assertEquals(Kind.VIDEO, item.kind)
        assertEquals("com.google.android.youtube.tv", item.appPackage)
        assertEquals("Lofi Girl", item.subtitle)
    }

    @Test
    fun search_ids_in_youtube_order() {
        val ids = YouTubeJson.searchIds(Fixtures.read("youtube/search.json"))
        assertEquals(listOf("rFZHOHl-L8A", "n61ULEU7CO0", "b0_Tlq_eBnc", "lTRiuFIWV54", "ZioKFmUhmtY", "JD-kMIpDfnY"), ids)
    }
}
