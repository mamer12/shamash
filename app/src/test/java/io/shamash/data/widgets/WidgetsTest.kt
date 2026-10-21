package io.shamash.data.widgets

import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.HttpPolicy
import io.shamash.data.MemorySecrets
import io.shamash.data.ServerHttp
import io.shamash.data.UnsafeXmlException
import io.shamash.data.json
import io.shamash.domain.ContentLevel
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class WidgetsTest {
    @Test fun `PEX_27 symbols are coins by ticker or id and currency pairs`() {
        assertEquals(Symbol.Coin("bitcoin", "BTC"), Symbol.parse("btc"))
        assertEquals(Symbol.Coin("ethereum", "ETH"), Symbol.parse("ethereum"))
        assertEquals(Symbol.Coin("pepe", "PEPE"), Symbol.parse("pepe"))
        assertEquals(Symbol.Fx("USD", "IQD"), Symbol.parse("usd / iqd"))
        assertNull(Symbol.parse("not a symbol!"))
        assertNull(Symbol.parse(""))
        assertEquals(listOf("coin:bitcoin", "fx:USDEUR"), Symbol.list("BTC, bitcoin, USD/EUR, ???").map { it.key })
        assertEquals(6, Symbol.list((1..10).joinToString(",") { "coin$it" }).size)
    }

    @Test fun `PEX_27 CoinGecko and rate answers become quotes, odd values dropped`() {
        val coins = listOf(Symbol.Coin("bitcoin", "BTC"), Symbol.Coin("ethereum", "ETH"), Symbol.Coin("broken", "X"), Symbol.Coin("missing", "M"))
        val q = PriceJson.coins(json.parseToJsonElement(Fixtures.read("widgets/coingecko.json")), coins, 5)
        assertEquals(listOf(67123.45, 3201.5), q.map { it.value })
        assertEquals(2.3456, q[0].change!!, 1e-9)
        val rates = PriceJson.rates(json.parseToJsonElement(Fixtures.read("widgets/rates_usd.json")), listOf(Symbol.Fx("USD", "EUR"), Symbol.Fx("USD", "BAD"), Symbol.Fx("EUR", "USD")), 5)
        assertEquals(listOf(0.9213), rates.map { it.value })                // a negative rate and another base are dropped
        assertTrue(PriceJson.rates(json.parseToJsonElement("""{"result":"error"}"""), listOf(Symbol.Fx("USD", "EUR")), 5).isEmpty())
        assertEquals("67,123", WidgetsProvider.format(67123.45))
        assertEquals("0.9213", WidgetsProvider.format(0.9213))
        assertEquals("+2.3%", WidgetsProvider.change(2.3456))
        assertEquals("-1.0%", WidgetsProvider.change(-1.04))
    }

    @Test fun `PEX_27 headlines are plain, linked, at most per feed, and DTDs refused`() {
        val h = Headlines.parse(Fixtures.read("widgets/news.xml").byteInputStream(), 3)
        assertEquals(listOf("Headline one", "Headline two", "Headline three"), h.map { it.title })
        assertEquals("World News", h[0].source)
        assertEquals(2, Headlines.parse(Fixtures.read("widgets/news.xml").byteInputStream(), 2).size)
        assertThrows(UnsafeXmlException::class.java) {
            Headlines.parse("""<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x "y">]><rss/>""".byteInputStream())
        }
    }

    @Test fun `PEX_27 values are fresh for 2 h, then stale, then gone after a day`() {
        val h = 3_600_000L
        assertFalse(Freshness.isStale(0, 2 * h))
        assertTrue(Freshness.isStale(0, 2 * h + 1))
        assertFalse(Freshness.isGone(0, 24 * h))
        assertTrue(Freshness.isGone(0, 24 * h + 1))
        val last = LastGood<Int>()
        last.put("k", 7, 0)
        assertEquals(7 to 0L, last.get("k", 3 * h))
        assertNull(last.get("k", 25 * h))
    }

    private class Fake {
        var up = true
        val server = FakeServer { r ->
            if (!up) MockResponse().setResponseCode(503)
            else when (r.requestUrl!!.encodedPath) {
                "/simple/price" -> FakeServer.json(Fixtures.read("widgets/coingecko.json"))
                "/rates/USD" -> FakeServer.json(Fixtures.read("widgets/rates_usd.json"))
                "/news.xml" -> MockResponse().setBody(Fixtures.read("widgets/news.xml"))
                else -> null
            }
        }
    }

    private fun provider(f: Fake, store: WidgetStore = WidgetStore(MemorySecrets()) { "p1" }, clock: () -> Long): WidgetsProvider {
        val server = ServerHttp(Http(null))
        val prices = PriceApi(server, clock, f.server.url("/simple/price").toHttpUrl(), f.server.url("/rates/").toHttpUrl())
        return WidgetsProvider(store, prices, server, say = { "s$it" }, clock = clock, policy = HttpPolicy.ANY)
    }

    private val profile = Profile(id = "p1", addons = setOf(WidgetsProvider.ID))

    @Test fun `PEX_27 the Today row mixes prices and headlines, at most 6 tiles`() = runBlocking {
        val f = Fake()
        try {
            var now = 1_000_000L
            val p = provider(f) { now }
            p.save(WidgetConfig(symbols = listOf("BTC", "ETH", "USD/EUR", "USD/IQD"), feeds = listOf(f.server.url("/news.xml"))))
            val shelf = p.shelves(profile).single()
            assertEquals("widgets:today", shelf.id)
            assertEquals(6, shelf.items.size)
            assertTrue(shelf.items[0].title.startsWith("BTC"))
            assertEquals("Headline one", shelf.items[1].title)                       // interleaved
            assertEquals(OpenTarget.AppPage("https://www.coingecko.com/en/coins/bitcoin", ""), shelf.items[0].open)
            assertTrue(shelf.items[0].subtitle.startsWith("+2.3%"))
            // Kids see the prices (rated G) but not the headlines.
            val kids = p.shelves(profile.copy(contentLevel = ContentLevel.KIDS)).single().items
            assertTrue(kids.none { it.title.startsWith("Headline") })
            assertEquals(4, kids.size)
        } finally { f.server.shutdown() }
    }

    @Test fun `PEX_27 each tile type is removable and a failed refresh keeps the last good value, then shows it stale`() = runBlocking {
        val f = Fake()
        try {
            var now = 1_000_000L
            val p = provider(f) { now }
            val c = p.save(WidgetConfig(symbols = listOf("BTC", "USD/EUR"), feeds = listOf(f.server.url("/news.xml"))))
            assertEquals(4, p.tiles(c).size)                                       // 2 prices + 2 headlines
            assertTrue(p.tiles(c.copy(hidden = setOf("news"))).none { it.title.startsWith("Headline") })
            assertEquals(listOf("USD/EUR"), p.tiles(c.copy(hidden = setOf("news", "crypto"))).map { it.title.substringBefore(' ') })
            assertTrue(p.tiles(c.copy(hidden = setOf("news", "crypto", "fx"))).isEmpty())

            f.up = false
            now += 60 * 60_000L                                                    // 1 h later: still fresh
            val kept = p.tiles(c)
            assertEquals(4, kept.size)
            assertFalse(kept.any { it.subtitle.startsWith("s${io.shamash.R.string.more_w_stale}") })
            now += 2 * 60 * 60_000L                                                // 3 h later: stale
            assertTrue(p.tiles(c).all { it.subtitle == "s${io.shamash.R.string.more_w_stale}" })
            now += 24 * 60 * 60_000L                                               // a day later: gone
            assertTrue(p.tiles(c).isEmpty())
            assertFalse(p.test().ok)
        } finally { f.server.shutdown() }
    }

    @Test fun `PEX_27 config is per profile, capped, and plain http feeds are not read`() = runBlocking {
        val f = Fake()
        try {
            val secrets = MemorySecrets()
            val a = WidgetStore(secrets) { "a" }
            val saved = a.save(WidgetConfig(symbols = (1..9).map { "c$it" } + " ", feeds = (1..5).map { "https://n$it.example/rss" }))
            assertEquals(6, saved.symbols.size)
            assertEquals(WidgetConfig.MAX_FEEDS, saved.feeds.size)
            assertEquals(WidgetConfig(), WidgetStore(secrets) { "b" }.get())
            val strict = WidgetsProvider(
                WidgetStore(MemorySecrets()) { "p1" }, PriceApi(ServerHttp(Http(null)), { 0L }, f.server.url("/simple/price").toHttpUrl(), f.server.url("/rates/").toHttpUrl()),
                ServerHttp(Http(null)), say = { "s$it" }, clock = { 0L },
            )
            val tiles = strict.tiles(WidgetConfig(symbols = emptyList(), feeds = listOf(f.server.url("/news.xml"))))
            assertTrue(tiles.isEmpty())
            assertTrue(f.server.paths().none { it == "/news.xml" })
        } finally { f.server.shutdown() }
    }
}
