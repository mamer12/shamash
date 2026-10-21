package io.shamash.data.security

import io.shamash.data.HttpPrivacy
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * A network interceptor on the one HTTP client: (1) a guarded call (one carrying a credential, or a request to a
 * user's own server or feed, tagged [HttpPrivacy.PRIVATE]) that started on https never continues over
 * plain http, whatever a redirect says; (2) on a hop to another host, the headers the app set (tokens, API keys, a
 * feed's auth header) are dropped from every call; OkHttp itself only drops `Authorization`. SPEC: PEX-04, PEX-22
 */
object RedirectGuard : Interceptor {
    const val DOWNGRADE = "insecure redirect refused"

    /** Headers that carry no credential and that a redirected hop still needs (a Range for streaming, say). */
    private val KEPT = setOf(
        "accept", "accept-language", "accept-encoding", "user-agent", "range", "if-range", "cache-control",
        "content-type", "content-length",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.call().request()
        val hop = chain.request()
        val guarded = isGuarded(original)
        if (guarded && isDowngrade(original.url, hop.url)) throw IOException(DOWNGRADE)
        val response = chain.proceed(stripped(original, hop))
        val next = response.header("Location")?.let { response.request.url.resolve(it) }
        if (guarded && response.isRedirect && next != null && isDowngrade(original.url, next)) {
            response.close()
            throw IOException(DOWNGRADE)
        }
        return response
    }

    /** The call as the app made it (before interceptors) carries a credential or the private tag. */
    fun isGuarded(original: Request): Boolean =
        original.header(HttpPrivacy.PRIVATE) != null || SecretUrls.hasSecretParam(original.url) ||
            SecretUrls.hasSecretHeader(original)

    /** True when a call that started on https would go on over http. */
    fun isDowngrade(start: HttpUrl, next: HttpUrl): Boolean = start.isHttps && !next.isHttps

    /** [hop] without the headers [original] set, when [hop] goes to another host than [original]. */
    fun stripped(original: Request, hop: Request): Request {
        if (sameHost(original.url, hop.url)) return hop
        val set = original.headers.names().map { it.lowercase(Locale.ROOT) }.filter { it !in KEPT }
        val secret = SecretUrls.HEADERS.map { it.lowercase(Locale.ROOT) }
        val drop = (set + secret).filter { hop.header(it) != null }
        if (drop.isEmpty()) return hop
        return hop.newBuilder().apply { drop.forEach { removeHeader(it) } }.build()
    }

    private fun sameHost(a: HttpUrl, b: HttpUrl) = a.host.equals(b.host, ignoreCase = true)
}
