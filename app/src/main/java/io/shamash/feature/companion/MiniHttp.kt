package io.shamash.feature.companion

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale

/** One HTTP request as the phone page sends it; [headers] are keyed in lower case (the first of each kept). */
data class HttpRequest(
    val method: String,
    val path: String,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

data class HttpReply(
    val status: Int,
    val reason: String,
    val contentType: String,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    fun bytes(): ByteArray {
        val payload = body.toByteArray(Charsets.UTF_8)
        val extra = headers.entries.joinToString("") { (k, v) -> "$k: $v\r\n" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${payload.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            extra +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.UTF_8) + payload
    }

    companion object {
        fun text(status: Int, reason: String, body: String) = HttpReply(status, reason, "text/plain; charset=utf-8", body)

        /** The page, allowed to run only its own inline script (the one carrying [nonce]). */
        fun html(body: String, nonce: String) = HttpReply(
            200, "OK", "text/html; charset=utf-8", body,
            mapOf("Content-Security-Policy" to csp(nonce), "Referrer-Policy" to "no-referrer"),
        )

        fun csp(nonce: String) = "default-src 'none'; script-src 'nonce-$nonce'; style-src 'unsafe-inline'; " +
            "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    }
}

/**
 * Just enough HTTP/1.1 for one small request per connection; bodies are cut at [MAX_BODY]. A request [accept] turns
 * down is dropped right after its first line; one that runs past [deadline] throws. SPEC: CMP-05
 */
object MiniHttp {
    const val MAX_BODY = 4096
    private const val MAX_LINE = 8192
    private const val MAX_HEADERS = 64
    private const val CONTENT_LENGTH = "content-length"

    fun read(
        input: InputStream,
        deadline: Deadline? = null,
        accept: (method: String, path: String) -> Boolean = { _, _ -> true },
    ): HttpRequest? {
        val parts = readLine(input, deadline)?.split(' ') ?: return null
        if (parts.size < 2 || !accept(parts[0], parts[1])) return null
        val headers = readHeaders(input, deadline)
        val length = headers[CONTENT_LENGTH]?.toIntOrNull() ?: 0
        val body = readUpTo(input, length.coerceIn(0, MAX_BODY), deadline)
        return HttpRequest(parts[0], parts[1], body.toString(Charsets.UTF_8), headers)
    }

    private fun readHeaders(input: InputStream, deadline: Deadline?): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        for (i in 0 until MAX_HEADERS) {
            val line = readLine(input, deadline) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            headers.putIfAbsent(name, line.substring(colon + 1).trim())
        }
        return headers
    }

    /** A field of an `application/x-www-form-urlencoded` body, or "" when it isn't there. */
    fun field(form: String, name: String): String {
        val raw = form.split('&').firstNotNullOfOrNull { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == name) pair.substring(eq + 1) else null
        } ?: return ""
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun readLine(input: InputStream, deadline: Deadline?): String? {
        val line = ByteArrayOutputStream()
        while (line.size() < MAX_LINE) {
            deadline?.check()
            val b = input.read()
            deadline?.check()
            if (b < 0) return if (line.size() == 0) null else line.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) line.write(b)
        }
        return line.toString(Charsets.UTF_8.name())
    }

    private fun readUpTo(input: InputStream, length: Int, deadline: Deadline?): ByteArray {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            deadline?.check()
            val n = input.read(body, read, length - read)
            deadline?.check()
            if (n < 0) break
            read += n
        }
        return body.copyOf(read)
    }
}
