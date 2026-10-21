package io.shamash.ui

import coil3.Uri
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import io.shamash.data.security.SecretUrls

/**
 * Images whose address carries a credential (a Plex photo URL with its `X-Plex-Token`) are never written to Coil's disk
 * cache: they stay in memory only, like the request itself (OkHttp's cache already skips them). SPEC: PEX-04
 */
object PrivateImages : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (!isPrivate(request.data)) return chain.proceed()
        val memoryOnly = request.newBuilder()
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        return chain.withRequest(memoryOnly).proceed()
    }

    /** Coil's model for a network image is a String, a Coil Uri or an OkHttp-style URL. */
    fun isPrivate(data: Any?): Boolean = when (data) {
        is String -> SecretUrls.carriesSecret(data)
        is Uri -> SecretUrls.carriesSecret(data.toString())
        is android.net.Uri -> SecretUrls.carriesSecret(data.toString())
        is okhttp3.HttpUrl -> SecretUrls.hasSecretParam(data)
        else -> false
    }
}
