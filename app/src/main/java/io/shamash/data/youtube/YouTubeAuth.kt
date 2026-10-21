package io.shamash.data.youtube

import io.shamash.data.Http
import io.shamash.data.softly
import io.shamash.domain.Profile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Where the sign-in is kept (the app's DataStore on the TV). SPEC: ARCH-06 */
interface SecretStore {
    suspend fun secret(key: String): String?
    suspend fun setSecret(key: String, value: String?)
}

/**
 * Read-only YouTube sign-in on the TV with Google's device flow: the TV shows a code, the user approves on a phone at
 * google.com/device. Only the refresh token is stored; access tokens live in memory. SPEC: PRV-06
 */
class YouTubeAuth(
    private val http: Http,
    private val secrets: SecretStore,
    private val profile: suspend () -> Profile,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Access(val token: String, val refresh: String, val until: Long)

    private val lock = Mutex()
    private var access: Access? = null

    suspend fun isSignedIn(): Boolean = profile().hasYoutubeClient && secrets.secret(REFRESH) != null

    /** Starts a sign-in; null without a YouTube client in the profile or when Google can't be reached. */
    suspend fun start(): DeviceCode? {
        val p = profile()
        if (!p.hasYoutubeClient) return null
        return softly("YouTube sign-in", null) {
            val (status, body) = http.postForm(DEVICE_URL, mapOf("client_id" to p.youtubeClientId.trim(), "scope" to SCOPE))
            if (status != 200) throw IOException("device code: HTTP $status")
            YouTubeJson.deviceCode(body, clock())
        }
    }

    /** One check, at [DeviceCode.intervalSec]: true once the user approved (the sign-in is then saved). */
    suspend fun poll(code: DeviceCode): Boolean {
        val answer = softly("YouTube sign-in poll", TokenAnswer.Pending) {
            requestToken(profile(), mapOf("device_code" to code.deviceCode, "grant_type" to DEVICE_GRANT))
        }
        val granted = answer as? TokenAnswer.Granted ?: return false
        val refresh = granted.refreshToken ?: return false
        secrets.setSecret(REFRESH, refresh)
        lock.withLock { access = Access(granted.accessToken, refresh, expiry(granted)) }
        return true
    }

    suspend fun signOut() {
        secrets.setSecret(REFRESH, null)
        lock.withLock { access = null }
    }

    /** Changes when the user signs in or out (also through another instance), so cached rows can follow. */
    suspend fun account(): Int? = secrets.secret(REFRESH)?.hashCode()

    /** A current access token, renewed from the saved sign-in when needed; null when signed out. Throws on I/O. */
    suspend fun accessToken(): String? = lock.withLock {
        val p = profile()
        val refresh = secrets.secret(REFRESH)
        if (!p.hasYoutubeClient || refresh == null) return@withLock null
        access?.takeIf { it.refresh == refresh && clock() < it.until }?.let { return@withLock it.token }
        when (val answer = requestToken(p, mapOf("refresh_token" to refresh, "grant_type" to "refresh_token"))) {
            is TokenAnswer.Granted -> Access(answer.accessToken, refresh, expiry(answer)).also { access = it }.token
            is TokenAnswer.Rejected -> null.also { if (answer.error == "invalid_grant") secrets.setSecret(REFRESH, null) }
            TokenAnswer.Pending -> null
        }
    }

    private suspend fun requestToken(p: Profile, grant: Map<String, String>): TokenAnswer {
        val form = mapOf("client_id" to p.youtubeClientId.trim(), "client_secret" to p.youtubeClientSecret.trim()) + grant
        return YouTubeJson.token(http.postForm(TOKEN_URL, form).second)
    }

    private fun expiry(granted: TokenAnswer.Granted) = clock() + (granted.expiresInSec - EARLY_SEC) * 1000

    private companion object {
        const val REFRESH = "youtube_refresh"
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        const val DEVICE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val EARLY_SEC = 60
    }
}
