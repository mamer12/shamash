package io.shamash.feature.companion

import android.content.res.Resources

/**
 * The phone's page: the companion PIN asked once (then kept in the phone's localStorage), Send (open now or add to
 * the queue), Type for the TV, Rows, Teams, Feeds and Backup for the active profile, whose name it shows. `?text=` /
 * `?url=` fill the box, for the phone's share target. Words come in the phone's language, right to left for Arabic.
 * Only the script carrying the request's nonce runs. SPEC: CMP-02, CMP-06..16
 */
internal object CompanionPage {
    fun render(res: Resources, nonce: String): String = fill(PhoneWords.page(res), nonce)

    /**
     * Puts [texts] into the template: HTML-escaped in the markup, as a JS object for the script. [nonce] marks the one
     * script the page's Content-Security-Policy lets run.
     */
    fun fill(texts: Map<String, String>, nonce: String = ""): String {
        val messages = texts.entries.joinToString(",", "{", "}") { (k, v) -> "\"${jsEscape(k)}\":\"${jsEscape(v)}\"" }
        val script = PageScript.JS
            .replace("{{messages}}", messages)
            .replace("{{digits}}", Pins.DIGITS.toString())
        return texts.entries.fold(PageMarkup.HTML) { page, (k, v) -> page.replace("{{$k}}", htmlEscape(v)) }
            .replace("{{nonce}}", htmlEscape(nonce))
            .replace("{{digits}}", Pins.DIGITS.toString())
            .replace("{{script}}", script)
    }

    private fun htmlEscape(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun jsEscape(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> append(c)
            }
        }
    }
}
