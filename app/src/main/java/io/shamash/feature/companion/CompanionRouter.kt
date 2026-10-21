package io.shamash.feature.companion

import java.util.Locale

/** Words `/send` answers with, in the phone's language. */
enum class SendWord { WRONG_PIN, LOCKED }

/**
 * Every request after it was read: the door checks first (Host is the TV, a browser Origin is the page's own),
 * then the page, `/send` (CMP-03) or the phone API (CMP-06..15). Pure apart from what it is given. SPEC: CMP-02,
 * CMP-03, CMP-06, CMP-16
 */
class CompanionRouter(
    private val auth: CompanionAuth,
    private val api: PhoneApi,
    private val page: (language: String, nonce: String) -> String,
    private val send: (text: String) -> String,
    private val words: (SendWord, language: String) -> String,
    private val nonce: () -> String,
) {
    fun route(request: HttpRequest, address: String, port: Int): HttpReply {
        if (!CompanionGate.hostAllowed(request.headers["host"], address, port)) return forbidden()
        if (!CompanionGate.originAllowed(request.headers, address, port)) return forbidden()
        val language = PageLanguage.of(request.headers["accept-language"])
        if (request.method == "GET" && !request.path.startsWith(ApiPaths.PREFIX)) {
            val n = nonce()
            return HttpReply.html(page(language, n), n)
        }
        if (CompanionGate.isApiCall(request)) return api.handle(request)
        if (!CompanionGate.isPageSend(request)) return forbidden()
        return when (auth.check(MiniHttp.field(request.body, "pin"))) {
            CompanionAuth.Verdict.OK -> HttpReply.text(200, "OK", send(MiniHttp.field(request.body, "text")))
            CompanionAuth.Verdict.WRONG -> HttpReply.text(403, "Forbidden", words(SendWord.WRONG_PIN, language))
            CompanionAuth.Verdict.LOCKED -> HttpReply.text(429, "Too Many Requests", words(SendWord.LOCKED, language))
        }
    }

    private fun forbidden() = HttpReply.text(403, "Forbidden", "")
}

/** The page follows the phone's language: Arabic when the phone prefers it, else English. SPEC: CMP-16 */
object PageLanguage {
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    fun of(acceptLanguage: String?): String {
        val tags = acceptLanguage.orEmpty().split(',').mapNotNull { part ->
            val bits = part.split(';').map { it.trim() }
            val q = bits.drop(1).firstOrNull { it.startsWith("q=") }?.removePrefix("q=")?.toDoubleOrNull() ?: 1.0
            bits.first().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && q > 0 }?.let { it to q }
        }.sortedByDescending { it.second }
        val first = tags.map { it.first.substringBefore('-') }.firstOrNull { it == ARABIC || it == ENGLISH }
        return first ?: ENGLISH
    }

    fun isRtl(language: String) = language == ARABIC
}
