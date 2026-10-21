package io.shamash.data.radio

import io.shamash.data.ServerHttp
import io.shamash.data.jLong
import io.shamash.data.jStr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A station as Radio Browser lists it; [url] is the resolved stream address. */
@Serializable
data class Station(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val tags: String = "",
    val country: String = "",
)

/**
 * Radio Browser (all.api.radio-browser.info, no key): stations by country and tag, most clicked first, broken ones
 * left out. Stream addresses must be http(s); a station without one is dropped. SPEC: PEX-24
 */
class RadioBrowser(private val server: ServerHttp, private val base: HttpUrl = BASE.toHttpUrl()) {
    suspend fun search(country: String?, tag: String?, name: String? = null, limit: Int = 30): List<Station> {
        val url = base.newBuilder().addPathSegments("json/stations/search").apply {
            country?.trim()?.uppercase()?.takeIf { COUNTRY.matches(it) }?.let { addQueryParameter("countrycode", it) }
            tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("tag", it.take(40)) }
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("name", it.take(60)) }
            addQueryParameter("hidebroken", "true")
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
            addQueryParameter("limit", limit.coerceIn(1, 100).toString())
        }.build()
        return parse(server.json(url, mapOf("User-Agent" to AGENT), TIMEOUT_MS, MAX_BYTES))
    }

    companion object {
        const val BASE = "https://all.api.radio-browser.info/"
        private const val AGENT = "Shamash/0.1"
        private const val TIMEOUT_MS = 8_000L
        private const val MAX_BYTES = 2L * 1024 * 1024
        private val COUNTRY = Regex("[A-Z]{2}")

        fun parse(root: JsonElement): List<Station> = (root as? JsonArray).orEmpty().mapNotNull { s ->
            val id = s.jStr("stationuuid") ?: return@mapNotNull null
            val name = s.jStr("name")?.trim()?.take(80) ?: return@mapNotNull null
            val stream = safe(s.jStr("url_resolved")) ?: safe(s.jStr("url")) ?: return@mapNotNull null
            if ((s.jLong("lastcheckok") ?: 1L) == 0L) return@mapNotNull null
            Station(id, name, stream, safe(s.jStr("favicon")), s.jStr("tags").orEmpty().take(80), s.jStr("countrycode").orEmpty())
        }.distinctBy { it.id }

        private fun safe(raw: String?) = raw?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }
}
