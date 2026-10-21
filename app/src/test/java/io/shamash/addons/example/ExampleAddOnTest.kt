package io.shamash.addons.example

import io.shamash.data.DataDeps
import io.shamash.data.FakeServer
import io.shamash.data.Fixtures
import io.shamash.data.Http
import io.shamash.data.HttpPrivacy
import io.shamash.data.MemorySecrets
import io.shamash.data.SecretCredentials
import io.shamash.data.ServerHttp
import io.shamash.data.Sources
import io.shamash.data.json
import io.shamash.data.say
import io.shamash.domain.ContentLevel
import io.shamash.domain.CredentialKind
import io.shamash.domain.Kind
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.RowSources
import io.shamash.domain.ShelfStyle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** The template add-on, end to end against a fake server serving the fixture catalog. SPEC: PLT-05 */
class ExampleAddOnTest {
    private val catalog = Fixtures.read("example/catalog.json")
    private val server = FakeServer { r -> if (r.requestUrl!!.encodedPath == "/catalog.json") FakeServer.json(catalog) else null }
    private val secrets = MemorySecrets()
    private val credentials = SecretCredentials(secrets, ExampleAddOn.ID, ExampleAddOn.fields) { "p1" }
    private val on = Profile(id = "p1").withAddon(ExampleAddOn.ID, true)

    private fun provider() = ExampleProvider(credentials, ServerHttp(Http(null)), ::say)

    private fun configured(token: String? = "synthetic-example-token") = runBlocking {
        credentials.set(ExampleAddOn.URL, server.url("/catalog.json"))
        credentials.set(ExampleAddOn.TOKEN, token)
    }

    @After fun stop() = server.shutdown()

    @Test fun `PLT_05 registered once, off by default, built only with an HTTP client`() {
        assertEquals(1, Sources.addOns.count { it.id == ExampleAddOn.ID })
        assertFalse(ExampleAddOn.ID in Profile().addons)
        assertTrue(Sources.addOnRegistry.enabled(Profile()).none { it.id == ExampleAddOn.ID })
        assertEquals(listOf(CredentialKind.URL, CredentialKind.TOKEN), ExampleAddOn.fields.map { it.kind })
        assertEquals(listOf(true, false), ExampleAddOn.fields.map { it.required })
        val bare = DataDeps(credentials, { on }, { false }, emptyMap())
        assertNull(ExampleAddOn.addOn.create(bare))
        val full = DataDeps(credentials, { on }, { false }, mapOf(Http::class.java to Http(null)))
        assertTrue(ExampleAddOn.addOn.create(full) is ExampleProvider)
    }

    @Test fun `PLT_05 the catalog keeps what can be shown and opened`() {
        val parsed = ExampleCatalog.parse(json.parseToJsonElement(catalog), listOf("new", "popular", "none"))
        val new = parsed.row("new")
        assertEquals(listOf("Night Train", "Little Owls", "Unrated Clip"), new.map { it.title })   // empty title, bad link, no id dropped
        val first = new[0]
        assertEquals("example:n1", first.id)
        assertEquals(ExampleAddOn.ID, first.providerId)
        assertEquals(Kind.MOVIE, first.kind)
        assertEquals(OpenTarget.AppPage("https://watch.example/title/n1", "com.example.player"), first.open)
        assertEquals("PG-13", first.rating)
        assertEquals(Kind.SERIES, new[1].kind)
        assertNull(new[2].backdropUrl)                    // http images are dropped
        assertNull(new[2].rating)
        assertTrue(parsed.row("none").isEmpty())
        assertEquals(4, parsed.size)                      // n1 counted once
        val many = """{"new": [${(1..40).joinToString(",") { """{"id":"$it","title":"t$it","link":"https://e.com/$it"}""" }}]}"""
        assertEquals(ExampleCatalog.MAX_PER_ROW, ExampleCatalog.parse(json.parseToJsonElement(many), listOf("new")).row("new").size)
        assertTrue(ExampleCatalog.parse(json.parseToJsonElement("[1,2]"), listOf("new")).row("new").isEmpty())
    }

    @Test fun `PEX_06 rows follow the profile's choices and carry provider ids`() = runBlocking {
        configured()
        val p = provider()
        assertEquals(listOf("example:new"), p.shelves(on).map { it.id })
        val both = RowSources.withOn(on, ExampleAddOn.ID, ExampleAddOn.rows[1], true)
        val shelves = provider().shelves(both)
        assertEquals(listOf("example:new", "example:popular"), shelves.map { it.id })
        assertEquals(listOf(ShelfStyle.WIDE, ShelfStyle.POSTER), shelves.map { it.style })
        assertEquals("s${io.shamash.R.string.example_row_new}", shelves[0].title)
    }

    @Test fun `PEX_07 the ContentGate applies to every card and featured needs wide art`() = runBlocking {
        configured()
        val kids = provider().shelves(on.copy(contentLevel = ContentLevel.KIDS))
        assertEquals(listOf("Little Owls"), kids.single().items.map { it.title })
        val family = provider().shelves(on.copy(contentLevel = ContentLevel.FAMILY))
        assertEquals(listOf("Night Train", "Little Owls"), family.single().items.map { it.title })   // unknown rating hidden
        assertEquals(listOf("Night Train", "Little Owls"), provider().featured(on).map { it.title })
        assertTrue(provider().search("night", on).isEmpty())
    }

    @Test fun `ARCH_05 off, unconfigured or failing gives nothing and never throws`() = runBlocking {
        configured()
        assertTrue(provider().shelves(Profile(id = "p1")).isEmpty())
        assertTrue(server.paths().isEmpty())               // off: no request at all
        credentials.set(ExampleAddOn.URL, "ftp://example.com/c.json")
        assertTrue(provider().shelves(on).isEmpty())
        credentials.set(ExampleAddOn.URL, server.url("/missing.json"))
        assertTrue(provider().shelves(on).isEmpty())
        assertTrue(provider().featured(on).isEmpty())
    }

    @Test fun `PEX_04 the token goes in a header, the request is private and messages echo nothing`() = runBlocking {
        val token = "synthetic-example-token"
        configured(token)
        assertTrue(provider().test().ok)
        val request = server.seen.single()
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertNull(request.getHeader(HttpPrivacy.PRIVATE))     // the private tag is stripped before sending
        assertFalse(request.requestUrl.toString().contains(token))
        assertFalse(credentials.toString().contains(token))
        credentials.set(ExampleAddOn.URL, server.url("/missing.json"))
        val failed = provider().test()
        assertFalse(failed.ok)
        assertFalse(failed.message.contains(token) || failed.message.contains("missing.json"))
        configured(token = null)
        provider().test()
        assertNull(server.seen.last().getHeader("Authorization"))
    }

    @Test fun `PEX_05 the connection test explains each outcome`() = runBlocking {
        assertEquals("s${io.shamash.R.string.example_test_bad_address}", provider().test().message)
        configured()
        val ok = provider().test()
        assertTrue(ok.ok)
        assertEquals("s${io.shamash.R.string.example_test_ok}", ok.message)   // say() stands in for the resource
        val empty = FakeServer { FakeServer.json("{}") }
        try {
            credentials.set(ExampleAddOn.URL, empty.url("/c.json"))
            assertEquals("s${io.shamash.R.string.example_test_empty}", provider().test().message)
        } finally {
            empty.shutdown()
        }
        val broken = FakeServer { MockResponse().setResponseCode(500) }
        try {
            credentials.set(ExampleAddOn.URL, broken.url("/c.json"))
            assertEquals("s${io.shamash.R.string.example_test_failed}", provider().test().message)
        } finally {
            broken.shutdown()
        }
    }
}
