package io.shamash.data.security

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Art addresses taken from a user-supplied feed (My feed, podcasts): the image loader fetches them with no reach
 * check (it also loads a home media server's art), so a feed could otherwise make the TV request any address on the
 * home network. Only http(s) art on a host that doesn't name the local network is kept. Pure, no DNS. SPEC: CMP-10,
 * PEX-22
 */
object PublicArt {
    fun url(raw: String?): String? {
        val url = raw?.trim()?.toHttpUrlOrNull() ?: return null
        return raw.trim().takeUnless { PrivateNet.isLocalName(url.host) }
    }
}
