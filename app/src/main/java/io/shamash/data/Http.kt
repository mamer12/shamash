package io.shamash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import io.shamash.data.security.RedirectGuard
import io.shamash.data.security.SecretUrls
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Logs under one tag. SPEC: ARCH-05 */
object Logger {
    private const val TAG = "Shamash"
    fun w(message: String, error: Throwable? = null) {
        runCatching { android.util.Log.w(TAG, line(message, error)) }
    }

    // Exception messages can contain credential-bearing URLs or response bodies.
    internal fun line(message: String, error: Throwable?) =
        message + (error?.let { ": ${it.javaClass.simpleName}" } ?: "")
}

/** The JSON settings every parser uses. SPEC: ARCH-04 */
val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

/**
 * One HTTP client for the whole app (shared connection pool and disk cache). Every hop of every call goes through
 * [RedirectGuard]: no https-to-http downgrade, no app headers sent on to another host. SPEC: ARCH-04, PEX-04
 */
class Http(cacheDir: File?) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(HttpPrivacy.request(chain.request())) }
        .addNetworkInterceptor(RedirectGuard)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply { if (cacheDir != null) cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024)) }
        .build()

    /** GET as text; throws on HTTP errors (callers turn errors into empty results). */
    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for ${request.url.host}")
            r.body?.string().orEmpty()
        }
    }

    suspend fun json(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        json.parseToJsonElement(text(url, headers))

    /** POST a form; returns the status code and body (OAuth answers errors as JSON bodies). */
    suspend fun postForm(url: String, form: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            r.code to r.body?.string().orEmpty()
        }
    }

    /** POST JSON (GraphQL). */
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} for ${request.url.host}")
                r.body?.string().orEmpty()
            }
        }
}

/** Runs a source call, turning any failure into [fallback] and a log line. SPEC: ARCH-05 */
suspend fun <T> softly(what: String, fallback: T, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Logger.w(what, e)
        fallback
    }

/** Credentials must not become disk-cache metadata, even when a server says its answer is cacheable. SPEC: ARCH-06 */
internal object HttpPrivacy {
    /** A request carrying this header (any value) is never cached; the header is not sent. SPEC: PEX-04 */
    const val PRIVATE = "X-Shamash-Private"

    fun request(request: Request): Request {
        val tagged = request.header(PRIVATE) != null
        val sensitive = tagged || SecretUrls.hasSecretParam(request.url) || SecretUrls.hasSecretHeader(request)
        if (!sensitive) return request
        return request.newBuilder().removeHeader(PRIVATE)
            .cacheControl(CacheControl.Builder().noCache().noStore().build()).build()
    }
}
