package io.shamash.data.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.Locale

/**
 * Which requests carry a credential: a known secret query parameter (Plex image URLs carry `X-Plex-Token`, M3U URLs a
 * username and password) or a known secret header. Those never reach a disk cache (OkHttp's or Coil's) and their
 * headers never follow a redirect to another host. SPEC: PEX-04, ARCH-06
 */
object SecretUrls {
    val PARAMS: Set<String> = setOf(
        "api_key", "api-key", "apikey", "key", "token", "x-plex-token", "access_token", "refresh_token", "auth_token",
        "client_secret", "client_id", "password", "passwd", "pw", "pass", "username", "user", "auth", "session", "sid",
        "sig", "signature",
    )
    val HEADERS: List<String> = listOf(
        "Authorization", "Proxy-Authorization", "Cookie", "X-Plex-Token", "X-Emby-Token", "X-MediaBrowser-Token",
        "X-Emby-Authorization", "X-Api-Key", "trakt-api-key", "simkl-api-key", "Client-Id",
    )

    fun hasSecretParam(url: HttpUrl): Boolean = url.queryParameterNames.any { it.lowercase(Locale.ROOT) in PARAMS }

    fun hasSecretHeader(request: Request): Boolean = HEADERS.any { request.header(it) != null }

    /** For image URLs and any other plain string: true when the address carries a credential in its query. */
    fun carriesSecret(url: String?): Boolean = url?.trim()?.toHttpUrlOrNull()?.let(::hasSecretParam) ?: false
}
