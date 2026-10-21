package io.shamash.data.security

import io.shamash.data.HttpPrivacy
import io.shamash.data.plex.PlexJson
import io.shamash.ui.PrivateImages
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretUrlsTest {
    @Test fun `PEX_04 Plex image URLs carry the token, so they skip every disk cache`() {
        val url = PlexJson.image("http://192.168.1.5:32400/".toHttpUrl(), "/library/1/thumb", "SECRET-TOKEN", 400, 600)
        assertTrue(SecretUrls.carriesSecret(url))
        assertTrue(PrivateImages.isPrivate(url))
        assertTrue(PrivateImages.isPrivate(url.toHttpUrl()))
        assertTrue(HttpPrivacy.request(Request.Builder().url(url).build()).cacheControl.noStore)
    }

    @Test fun `PEX_04 ordinary art stays cacheable`() {
        listOf("https://image.tmdb.org/t/p/w500/a.jpg", "https://cnth2.shabakaty.cc/poster/1.jpg", "not a url", "")
            .forEach { assertFalse(it, PrivateImages.isPrivate(it)) }
        assertFalse(PrivateImages.isPrivate(null))
        assertFalse(PrivateImages.isPrivate(42))
    }

    @Test fun `PEX_16 playlist and API credentials in the query are secrets`() {
        listOf(
            "http://iptv.example/get.php?username=u&password=p", "https://www.googleapis.com/youtube/v3/x?key=K",
            "https://api.simkl.com/sync?client_id=C", "https://a.example/x?access_token=T",
        ).forEach { assertTrue(it, SecretUrls.carriesSecret(it)) }
        val trakt = Request.Builder().url("https://api.trakt.tv/sync").header("trakt-api-key", "C").build()
        assertTrue(SecretUrls.hasSecretHeader(trakt))
    }
}
