package io.shamash.feature.companion

import io.shamash.domain.Layout
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.RowPref
import io.shamash.feature.companion.Kit.code
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

// SPEC: CMP-07, CMP-08, CMP-09, CMP-10, CMP-11
class PhoneEditsTest {
    private fun rows(n: Int, title: String? = null) =
        (0 until n).joinToString(",", "{\"rows\":[", "]}") { i -> "{\"id\":\"r$i\",\"order\":$i" + (title?.let { ",\"title\":\"$it\"" } ?: "") + "}" }

    @Test fun `CMP_07 rows list the editor model with default titles`() {
        val backend = FakeBackend(active = Profile(id = "p", layout = Layout(rows = listOf(RowPref("continue", pinned = true, title = "Mine", order = -1)))))
        val body = Kit.api(backend).handle(Kit.call("GET", ApiPaths.ROWS)).body
        assertTrue(body.indexOf("\"continue\"") < body.indexOf("\"matches\""))
        assertTrue(body.contains("\"title\":\"Mine\""))
        assertTrue(body.contains("\"defaultTitle\":\"Matches\""))
    }

    @Test fun `CMP_07 a valid save replaces the list and keeps prefs for rows it does not name`() {
        val backend = FakeBackend(active = Profile(id = "p", layout = Layout(rows = listOf(RowPref("gone:row", hidden = true)))))
        val body = """{"rows":[{"id":"matches","title":"  Footy ","hidden":false,"pinned":true,"order":0},{"id":"continue","title":"","hidden":true,"order":1}]}"""
        val reply = Kit.api(backend).handle(Kit.call("POST", ApiPaths.ROWS, body))
        assertEquals(200, reply.status)
        val saved = backend.saved.single()
        assertEquals(Changed.ROWS, saved.second)
        val layout = saved.first.layout
        assertEquals(listOf("matches", "continue", "gone:row"), layout.rows.map { it.rowId })
        assertEquals("Footy", layout.rows[0].title)
        assertNull(layout.rows[1].title)
        assertTrue(layout.rows[1].hidden)
    }

    @Test fun `CMP_07 rows save validation refuses the whole list`() {
        val api = Kit.api()
        val refused = listOf(
            rows(RowsCheck.MAX_ROWS + 1),
            rows(2, "x".repeat(41)),
            """{"rows":[{"id":"a"},{"id":"a"}]}""",
            """{"rows":[{"id":" "}]}""",
            """{"rows":[{"id":"a","title":"bad\nline"}]}""",
            """{"rows":[{"id":"a","order":5000}]}""",
        )
        refused.forEach { assertEquals(it.take(60), "rows_invalid", api.handle(Kit.call("POST", ApiPaths.ROWS, it)).code()) }
        val malformed = listOf(
            """{"rows":[{"id":"a","colour":"red"}]}""",       // unknown field
            """{"rows":[{"id":"a"}],"extra":1}""",
            """{"rows":[{"id":"a","hidden":"yes"}]}""",
            """{"rows":"none"}""", "not json", "",
        )
        malformed.forEach { assertEquals(it, "invalid", api.handle(Kit.call("POST", ApiPaths.ROWS, it)).code()) }
        assertEquals(200, api.handle(Kit.call("POST", ApiPaths.ROWS, rows(RowsCheck.MAX_ROWS, "x".repeat(40)))).status)
    }

    @Test fun `CMP_08 a secret typed on the phone reaches the field and is never in any answer`() {
        val fields = PhoneFields()
        var got: String? = null
        val code = fields.offer("Plex token", secret = true) { got = it }!!
        val api = Kit.api(fields = fields)
        val secret = "s3cr3t-T0KEN-value"
        val reply = api.handle(Kit.call("POST", ApiPaths.FIELDS, """{"code":"$code","value":"$secret"}"""))
        assertEquals(200, reply.status)
        assertEquals(secret, got)
        assertFalse(reply.body.contains(secret))
        val list = api.handle(Kit.call("GET", ApiPaths.FIELDS)).body
        assertTrue(list.contains("Plex token") && list.contains("\"secret\":true"))
        assertFalse(list.contains(secret))
        fields.withdraw(code)
        val late = api.handle(Kit.call("POST", ApiPaths.FIELDS, """{"code":"$code","value":"$secret"}"""))
        assertEquals("no_field", late.code())
        assertFalse(late.body.contains(secret))
    }

    @Test fun `CMP_08 field codes are unique, short and bounded`() {
        val fields = PhoneFields(java.util.Random(1))
        val codes = (0 until PhoneFields.MAX_FIELDS).map { fields.offer("f$it", false) {}!! }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.length == PhoneFields.CODE_LENGTH })
        assertNull(fields.offer("one too many", false) {})
        assertFalse(fields.fill(codes[0], "x".repeat(PhoneFields.MAX_VALUE + 1)))
    }

    @Test fun `CMP_09 teams and competitions are validated`() {
        val backend = FakeBackend(active = Profile(id = "p", competitions = setOf("custom.league")))
        val api = Kit.api(backend)
        val get = api.handle(Kit.call("GET", ApiPaths.TEAMS)).body
        assertTrue(get.contains("\"id\":\"f1\"") && get.contains("\"maxTeams\":20"))
        val ok = """{"teams":["Real Madrid"," real madrid ","","Al Hilal"],"competitions":["eng.1","nba","custom.league"]}"""
        assertEquals(200, api.handle(Kit.call("POST", ApiPaths.TEAMS, ok)).status)
        assertEquals(setOf("Real Madrid", "Al Hilal"), backend.active.teams)
        assertEquals(setOf("eng.1", "nba", "custom.league"), backend.active.competitions)
        val tooMany = (0..20).joinToString(",", "{\"teams\":[", "],\"competitions\":[]}") { "\"t$it\"" }
        listOf(
            tooMany,
            """{"teams":["${"x".repeat(41)}"],"competitions":[]}""",
            """{"teams":[],"competitions":["made.up"]}""",
        ).forEach { assertEquals("teams_invalid", api.handle(Kit.call("POST", ApiPaths.TEAMS, it)).code()) }
    }

    @Test fun `CMP_10 private and local feed addresses are refused`() {
        val backend = FakeBackend()
        backend.dns = mapOf("example.org" to listOf("93.184.216.34"), "rebind.example.com" to listOf("10.0.0.5"))
        val api = Kit.api(backend)
        val local = listOf(
            "https://192.168.1.10/feed", "https://10.1.2.3/rss", "https://172.20.0.1/", "https://127.0.0.1/", "https://localhost/",
            "https://nas.local/feed", "https://router.lan/", "https://printer/", "https://[::1]/", "https://[fd00::1]/",
            "https://[::ffff:192.168.1.1]/", "https://100.64.0.1/", "https://169.254.169.254/latest", "https://0.0.0.0/",
            "https://0x7f.1/", "https://2130706433/", "https://rebind.example.com/feed",
        )
        local.forEach { url ->
            val reply = api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"add","title":"T","url":"$url"}"""))
            assertEquals(url, "feed_private", reply.code())
            assertEquals(url, "feed_private", api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"try","title":"T","url":"$url"}""")).code())
        }
        assertEquals(0, backend.feedList!!.size)
        assertEquals("feed_url_bad", api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"add","title":"T","url":"http://example.org/f"}""")).code())
        assertEquals("feed_private", api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"add","title":"T","url":"http://192.168.1.5/f","allowHttp":true}""")).code())
        assertEquals("feed_private", api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"add","title":"T","url":"https://unknown.invalid/f"}""")).code())
    }

    @Test fun `CMP_10 a public feed can be tried, added and removed, and the list shows hosts only`() {
        val backend = FakeBackend()
        val api = Kit.api(backend)
        val tried = api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"try","title":"News","url":"https://example.org/rss?token=abc"}"""))
        assertTrue(tried.body.contains("\"titles\":[\"One\",\"Two\",\"Three\"]"))
        assertEquals(200, api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"add","title":"News","url":"https://example.org/rss?token=abc","kind":"ATOM","style":"POSTER","level":"KIDS"}""")).status)
        val feed = backend.feedList!!.single()
        assertEquals("News", feed.title)
        val list = api.handle(Kit.call("GET", ApiPaths.FEEDS)).body
        assertTrue(list.contains("\"host\":\"example.org\""))
        assertFalse(list.contains("token=abc"))
        assertEquals(200, api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"remove","id":"${feed.id}"}""")).status)
        assertTrue(backend.feedList!!.isEmpty())
        assertEquals("feed_missing", api.handle(Kit.call("POST", ApiPaths.FEEDS, """{"action":"remove","id":"nope"}""")).code())
        backend.feedList = null
        assertEquals("no_feeds", api.handle(Kit.call("GET", ApiPaths.FEEDS)).code())
    }

    @Test fun `CMP_11 queue keeps 30 per profile, newest first, once each`() {
        var list = emptyList<QueueEntry>()
        repeat(35) { i -> list = QueueOps.add(list, QueueOps.entry("https://example.org/$i", i.toLong(), "id$i")!!) }
        assertEquals(QueueOps.MAX, list.size)
        assertEquals("id34", list.first().id)
        list = QueueOps.add(list, QueueOps.entry("https://example.org/20", 99, "again")!!)
        assertEquals(1, list.count { it.text == "https://example.org/20" })
        assertEquals("again", list.first().id)
        assertNull(QueueOps.entry("   ", 0))
        assertNull(QueueOps.entry("x".repeat(QueueOps.MAX_TEXT + 1), 0))
        assertEquals("Great goal", QueueOps.titleOf("Great goal https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("YouTube", QueueOps.titleOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("example.org", QueueOps.titleOf("https://www.example.org/a/b"))
        val card = QueueOps.card(QueueOps.entry("https://youtu.be/dQw4w9WgXcQ", 0, "e1")!!, "io.shamash")
        assertEquals("e1", QueueOps.idOf((card.open as io.shamash.domain.OpenTarget.AppPage).uri))
        assertTrue(card.backdropUrl!!.contains("dQw4w9WgXcQ"))
    }

    @Test fun `CMP_11 queue store survives a restart and a corrupt file is an empty queue`() {
        val dir = Files.createTempDirectory("queue").toFile()
        try {
            val store = QueueStore(dir)
            assertTrue(store.add("p1", QueueOps.entry("Dune", 1, "a")!!))
            assertTrue(store.add("p2", QueueOps.entry("Other", 1, "b")!!))
            assertEquals(listOf("Dune"), QueueStore(dir).list("p1").map { it.text })
            store.remove("p1", "a")
            assertTrue(QueueStore(dir).list("p1").isEmpty())
            File(dir, "queue_p2.json").writeText("{broken")
            assertTrue(QueueStore(dir).list("p2").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `CMP_11 add to queue goes to the active profile and never for a guest`() {
        val backend = FakeBackend()
        val api = Kit.api(backend)
        assertEquals("queued", api.handle(Kit.call("POST", ApiPaths.QUEUE, """{"text":"Watch https://example.org/x"}""")).code())
        assertEquals(1, backend.queued["p-owner"]!!.size)
        assertEquals("queue_invalid", api.handle(Kit.call("POST", ApiPaths.QUEUE, """{"text":"  "}""")).code())
        val guest = FakeBackend(active = Profile(id = "g", kind = ProfileKind.GUEST), ownerProfile = Profile(id = "o", pinHash = "h"))
        guest.pins["o"] = "1234"
        assertEquals("refused", Kit.api(guest).handle(Kit.call("POST", ApiPaths.QUEUE, """{"text":"x"}""", profilePin = "1234")).code())
    }
}
