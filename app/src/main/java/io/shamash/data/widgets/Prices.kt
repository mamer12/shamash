package io.shamash.data.widgets

import io.shamash.data.ServerHttp
import io.shamash.data.jNum
import io.shamash.data.jObj
import io.shamash.data.jStr
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A symbol the user wants a price for: a coin (CoinGecko id) or a currency pair. */
sealed interface Symbol {
    val key: String

    data class Coin(val id: String, val label: String) : Symbol {
        override val key get() = "coin:$id"
    }

    data class Fx(val base: String, val quote: String) : Symbol {
        override val key get() = "fx:$base$quote"
    }

    companion object {
        private val FX = Regex("""^([A-Za-z]{3})\s*/\s*([A-Za-z]{3})$""")
        private val COIN = Regex("[a-z0-9-]{2,40}")
        private val TICKERS = mapOf(
            "btc" to "bitcoin", "eth" to "ethereum", "sol" to "solana", "xrp" to "ripple", "ada" to "cardano",
            "doge" to "dogecoin", "bnb" to "binancecoin", "usdt" to "tether", "ltc" to "litecoin", "dot" to "polkadot",
            "trx" to "tron", "ton" to "the-open-network", "avax" to "avalanche-2", "usdc" to "usd-coin",
        )

        /** "BTC", "bitcoin", "USD/EUR"; null when it is neither. */
        fun parse(raw: String): Symbol? {
            val text = raw.trim()
            FX.find(text)?.let { return Fx(it.groupValues[1].uppercase(), it.groupValues[2].uppercase()) }
            val lower = text.lowercase()
            val id = TICKERS[lower] ?: lower.takeIf { COIN.matches(it) } ?: return null
            val label = TICKERS.entries.firstOrNull { it.value == id }?.key?.uppercase() ?: text.uppercase().take(10)
            return Coin(id, label)
        }

        /** A comma or line separated list, each once, at most [max]. */
        fun list(raw: String, max: Int = 6): List<Symbol> =
            raw.split(',', '\n', ';').mapNotNull { parse(it) }.distinctBy { it.key }.take(max)
    }
}

/** A price at a time; [change] is the 24 h change in percent when known. */
data class Quote(val symbol: Symbol, val value: Double, val change: Double?, val at: Long)

/**
 * Public price endpoints, no key: CoinGecko `simple/price` for coins (in US dollars) and open.er-api.com for currency
 * rates. SPEC: PEX-27
 */
class PriceApi(
    private val server: ServerHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val coins: HttpUrl = "https://api.coingecko.com/api/v3/simple/price".toHttpUrl(),
    private val rates: HttpUrl = "https://open.er-api.com/v6/latest/".toHttpUrl(),
) {
    suspend fun coins(list: List<Symbol.Coin>): List<Quote> {
        if (list.isEmpty()) return emptyList()
        val url = coins.newBuilder().addQueryParameter("ids", list.joinToString(",") { it.id })
            .addQueryParameter("vs_currencies", "usd").addQueryParameter("include_24hr_change", "true").build()
        return PriceJson.coins(server.json(url, timeoutMs = TIMEOUT_MS, maxBytes = MAX_BYTES), list, clock())
    }

    suspend fun pairs(list: List<Symbol.Fx>): List<Quote> = list.groupBy { it.base }.flatMap { (base, pairs) ->
        val url = rates.newBuilder().addPathSegment(base).build()
        PriceJson.rates(server.json(url, timeoutMs = TIMEOUT_MS, maxBytes = MAX_BYTES), pairs, clock())
    }

    private companion object {
        const val TIMEOUT_MS = 6_000L
        const val MAX_BYTES = 512L * 1024
    }
}

object PriceJson {
    fun coins(root: JsonElement, wanted: List<Symbol.Coin>, now: Long): List<Quote> = wanted.mapNotNull { c ->
        val o = root.jObj(c.id) ?: return@mapNotNull null
        val usd = o.jNum("usd")?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
        Quote(c, usd, o.jNum("usd_24h_change")?.takeIf { it.isFinite() }, now)
    }

    fun rates(root: JsonElement, wanted: List<Symbol.Fx>, now: Long): List<Quote> {
        if (root.jStr("result") != null && root.jStr("result") != "success") return emptyList()
        val rates = root.jObj("rates") ?: return emptyList()
        return wanted.mapNotNull { p ->
            if (root.jStr("base_code")?.uppercase() != p.base) return@mapNotNull null
            rates.jNum(p.quote)?.takeIf { it.isFinite() && it > 0 }?.let { Quote(p, it, null, now) }
        }
    }
}
