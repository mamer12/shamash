package io.shamash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import io.shamash.data.security.PrivateNet
import io.shamash.data.security.PublicSockets
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Which addresses may use plain http. SPEC: PEX-12, PEX-22 */
enum class HttpPolicy { NONE, PRIVATE_ONLY, ANY }

/**
 * Where a request may connect. [ANY] for the user's own media servers (PEX-12); [PUBLIC] for every other user-supplied
 * URL (feeds, podcasts, news): checked on the connected address at fetch time, every hop. SPEC: CMP-10, PEX-22
 */
enum class Reach { ANY, PUBLIC }

/** Checks an address the user typed. Logs show the host only (see [Redact]). SPEC: PEX-04, PEX-12 */
object ServerUrl {
    /** https always; http only as [policy] allows. Null when the address isn't usable. */
    fun parse(raw: String, policy: HttpPolicy = HttpPolicy.NONE): HttpUrl? {
        val url = raw.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        if (url.isHttps) return url
        return url.takeIf {
            policy == HttpPolicy.ANY || (policy == HttpPolicy.PRIVATE_ONLY && isPrivateHost(it.host))
        }
    }

    /** RFC 1918, loopback, link-local, `.local` and single-label names. No DNS lookup. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h == "::1") return true
        if (h.startsWith("fc") && ':' in h || h.startsWith("fd") && ':' in h || h.startsWith("fe80:")) return true
        val v4 = h.split('.').mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
        if (v4.size == 4 && h.split('.').size == 4) {
            return v4[0] == 10 || v4[0] == 127 || (v4[0] == 172 && v4[1] in 16..31) ||
                (v4[0] == 192 && v4[1] == 168) || (v4[0] == 169 && v4[1] == 254)
        }
        return '.' !in h && ':' !in h
    }
}

/**
 * Requests to a user's own servers and feeds: tagged private (never cached, SPEC: PEX-04), bounded in time and size,
 * streamed, gzip understood, [Reach.PUBLIC] ones kept off the local network. Errors name the host only.
 * [refused] is the public reach's address rule (tests that serve feeds from localhost relax it). SPEC: PEX-04, PEX-22
 */
class ServerHttp(
    private val http: Http,
    refused: (InetAddress) -> Boolean = PrivateNet::isLocalAddress,
) {
    private val publicSockets = PublicSockets(refused)

    suspend fun <T> read(
        url: HttpUrl,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 8_000,
        maxBytes: Long = DEFAULT_MAX,
        body: String? = null,
        reach: Reach = Reach.ANY,
        block: (InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        val builder = http.client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        if (reach == Reach.PUBLIC) PublicSockets.applyTo(builder, publicSockets)
        val client = builder.build()
        val request = Request.Builder().url(url).header(HttpPrivacy.PRIVATE, "1")
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (body != null) post(body.toRequestBody("application/json".toMediaType()))
            }.build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for ${url.host}")
            val stream = r.body?.byteStream() ?: throw IOException("empty answer from ${url.host}")
            block(Limited(unzipped(stream), maxBytes))
        }
    }

    suspend fun text(
        url: HttpUrl, headers: Map<String, String> = emptyMap(), timeoutMs: Long = 8_000,
        maxBytes: Long = DEFAULT_MAX, body: String? = null, reach: Reach = Reach.ANY,
    ): String = read(url, headers, timeoutMs, maxBytes, body, reach) { it.readBytes().toString(Charsets.UTF_8) }

    suspend fun json(
        url: HttpUrl, headers: Map<String, String> = emptyMap(), timeoutMs: Long = 8_000,
        maxBytes: Long = DEFAULT_MAX, body: String? = null, reach: Reach = Reach.ANY,
    ): JsonElement = json.parseToJsonElement(text(url, headers, timeoutMs, maxBytes, body, reach))

    private fun unzipped(raw: InputStream): InputStream {
        val buffered = BufferedInputStream(raw)
        buffered.mark(2)
        val a = buffered.read()
        val b = buffered.read()
        buffered.reset()
        return if (a == 0x1f && b == 0x8b) GZIPInputStream(buffered) else buffered
    }

    private class Limited(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L
        private fun add(n: Int): Int { if (n > 0) { count += n; if (count > max) throw IOException("answer too large") }; return n }
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = add(super.read(b, off, len))
    }

    companion object {
        const val DEFAULT_MAX = 8L * 1024 * 1024
    }
}

/** Optional trailing slash and path join for a server's base address. */
fun HttpUrl.child(path: String, query: Map<String, String> = emptyMap()): HttpUrl =
    newBuilder().apply {
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        query.forEach { (k, v) -> addQueryParameter(k, v) }
    }.build()
