package io.shamash.data.security

import io.shamash.data.myfeed.FeedItem
import io.shamash.data.myfeed.MyFeedProvider
import io.shamash.data.myfeed.UserFeed
import io.shamash.data.podcasts.Episode
import io.shamash.data.podcasts.Podcast
import io.shamash.data.podcasts.PodcastProvider
import io.shamash.domain.FeedStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A feed's art is loaded by the image loader, which has no reach check: art that points into the LAN is dropped. */
class PublicArtTest {
    private val inside = listOf(
        "http://192.168.1.1/admin.png", "http://10.0.0.5/x.jpg", "http://127.0.0.1:8080/a.png", "http://[::1]/a.png",
        "http://router.lan/a.png", "http://nas.local/a.png", "http://2130706433/a.png", "http://169.254.169.254/latest",
        "file:///sdcard/a.png", "ftp://x.example/a.png", "not a url", "",
    )

    @Test fun `CMP_10 art into the local network or off the web is dropped, public art kept`() {
        inside.forEach { assertNull(it, PublicArt.url(it)) }
        assertNull(PublicArt.url(null))
        assertEquals("https://cdn.example/a.jpg", PublicArt.url(" https://cdn.example/a.jpg "))
    }

    @Test fun `PEX_22 My feed and podcast cards carry no art that points into the LAN`() {
        inside.forEach { art ->
            val feed = UserFeed("f1", "Feed", "https://feed.example/rss", style = FeedStyle.POSTER)
            val card = MyFeedProvider.card(feed, FeedItem("T", "https://feed.example/1", art, "", null))
            assertNull(art, card.posterUrl ?: card.backdropUrl)
            val show = Podcast("Show", art, listOf(Episode("E", "https://pod.example/e.mp3", "audio/mpeg", art, null)))
            val ep = PodcastProvider.card(show, show.episodes.single(), "")
            assertNull(art, ep.posterUrl ?: ep.backdropUrl)
        }
        val wide = UserFeed("f2", "Feed", "https://feed.example/rss", style = FeedStyle.WIDE)
        assertEquals("https://cdn.example/a.jpg", MyFeedProvider.card(wide, FeedItem("T", "https://x.example/1", "https://cdn.example/a.jpg", "", null)).backdropUrl)
    }
}
