package io.shamash.feature.companion

import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.feature.companion.Kit.code
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// SPEC: CMP-06 (all new endpoints keep CMP-03's PIN, X-Shamash, Host and lockout rules and CMP-05's limits)
class PhoneAuthTest {
    private val small = "{}"

    @Test fun `CMP_06 every new endpoint refuses a missing or wrong companion PIN`() {
        Kit.ENDPOINTS.forEach { (method, path) ->
            listOf(null, "000000", "12345", "").forEach { pin ->
                val reply = Kit.api().handle(Kit.call(method, path, small, pin = pin))
                assertEquals("$method $path pin=$pin", 403, reply.status)
                assertEquals("pin", reply.code())
            }
        }
    }

    @Test fun `CMP_06 one lockout covers the API and send alike and refuses even the right PIN`() {
        var now = 0L
        val auth = Kit.auth({ now })
        val api = Kit.api(auth = auth)
        val router = Kit.router(api, auth)
        repeat(PinGuard.LIMIT) { api.handle(Kit.call("GET", ApiPaths.STATE, pin = "999999")) }
        Kit.ENDPOINTS.forEach { (method, path) ->
            val reply = api.handle(Kit.call(method, path, small))
            assertEquals("$method $path", 429, reply.status)
            assertEquals("locked", reply.code())
        }
        val send = HttpRequest("POST", "/send", "pin=${Kit.PIN}&text=hi", mapOf("host" to "${Kit.TV}:${Kit.PORT}", "x-shamash" to "1"))
        assertEquals(429, router.route(send, Kit.TV, Kit.PORT).status)
        now += PinGuard.FIRST_LOCK_MS
        assertEquals(200, api.handle(Kit.call("GET", ApiPaths.STATE)).status)
    }

    @Test fun `CMP_06 the third lockout rotates the PIN for the API too`() {
        var now = 0L
        val pins = Kit.Pins()
        val api = Kit.api(auth = Kit.auth({ now }, pins))
        repeat(PinGuard.ROTATE_AFTER) {
            repeat(PinGuard.LIMIT) { api.handle(Kit.call("GET", ApiPaths.STATE, pin = "999999")) }
            now += PinGuard.MAX_LOCK_MS
        }
        assertEquals(1, pins.rotations)
        assertEquals(403, api.handle(Kit.call("GET", ApiPaths.STATE)).status)
    }

    @Test fun `CMP_06 router refuses other hosts, other origins, cross-site fetches and calls without the header`() {
        val router = Kit.router(Kit.api())
        fun route(r: HttpRequest) = router.route(r, Kit.TV, Kit.PORT).status
        assertEquals(200, route(Kit.call("GET", ApiPaths.STATE)))
        assertEquals(200, route(Kit.call("GET", ApiPaths.STATE, extra = mapOf("origin" to "http://${Kit.TV}:${Kit.PORT}", "sec-fetch-site" to "same-origin"))))
        assertEquals(403, route(Kit.call("GET", ApiPaths.STATE, extra = mapOf("host" to "evil.example:8765"))))
        assertEquals(403, route(Kit.call("POST", ApiPaths.ROWS, "{\"rows\":[]}", extra = mapOf("origin" to "http://evil.example"))))
        assertEquals(403, route(Kit.call("POST", ApiPaths.ROWS, "{\"rows\":[]}", extra = mapOf("origin" to "null"))))
        assertEquals(403, route(Kit.call("POST", ApiPaths.QUEUE, "{\"text\":\"x\"}", extra = mapOf("sec-fetch-site" to "cross-site"))))
        val bare = Kit.call("POST", ApiPaths.QUEUE, "{\"text\":\"x\"}").let { it.copy(headers = it.headers - "x-shamash") }
        assertEquals(403, route(bare))
        val wrongValue = Kit.call("GET", ApiPaths.STATE, extra = mapOf("x-shamash" to "true"))
        assertEquals(403, route(wrongValue))
    }

    @Test fun `CMP_06 reads only accept the API paths and other methods stop before headers`() {
        listOf("PUT /api/rows", "DELETE /api/feeds", "OPTIONS /api/rows", "GET /api/queue", "GET /api/backup/export", "POST /api/state", "GET /api/nope")
            .forEach { first ->
                val input = "$first HTTP/1.1\r\nrest".byteInputStream()
                assertEquals(first, null, MiniHttp.read(input, accept = CompanionGate::accepts, limit = CompanionGate::bodyLimit))
            }
    }

    @Test fun `CMP_06 bodies over 64 KB for edits and 1 MB for import are refused unread`() {
        val api = Kit.api()
        ApiPaths.POSTS.filter { it != ApiPaths.BACKUP_IMPORT }.forEach { path ->
            val reply = api.handle(Kit.call("POST", path, "", length = CompanionGate.EDIT_BODY + 1))
            assertEquals(path, 413, reply.status)
        }
        assertEquals(413, api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, "", length = CompanionGate.IMPORT_BODY + 1)).status)
        val raw = "POST /api/rows HTTP/1.1\r\nContent-Length: ${CompanionGate.EDIT_BODY + 1}\r\n\r\n" + "x".repeat(10)
        val read = MiniHttp.read(raw.byteInputStream(), accept = CompanionGate::accepts, limit = CompanionGate::bodyLimit)!!
        assertEquals("", read.body)
        assertTrue(CompanionGate.tooLarge(read))
    }

    @Test fun `CMP_05 send still cuts its body at 4 KB`() {
        val raw = "POST /send HTTP/1.1\r\nContent-Length: 5000\r\n\r\n" + "y".repeat(5000)
        val read = MiniHttp.read(raw.byteInputStream(), accept = CompanionGate::accepts, limit = CompanionGate::bodyLimit)!!
        assertEquals(MiniHttp.MAX_BODY, read.body.length)
        assertFalse(CompanionGate.tooLarge(read))
    }

    @Test fun `CMP_06 a profile PIN is asked for edits and checked against that profile`() {
        val backend = FakeBackend(active = Profile(id = "p1", name = "Sam", pinHash = "h"))
        backend.pins["p1"] = "4321"
        val api = Kit.api(backend)
        val edits = listOf("GET" to ApiPaths.ROWS, "GET" to ApiPaths.TEAMS, "GET" to ApiPaths.FEEDS, "POST" to ApiPaths.QUEUE)
        edits.forEach { (m, p) ->
            val none = api.handle(Kit.call(m, p, "{\"text\":\"x\"}"))
            assertEquals(401, none.status)
            assertEquals("profile_pin", none.code())
            assertTrue(none.body.contains("\"who\":\"profile\""))
            assertEquals("profile_pin_wrong", api.handle(Kit.call(m, p, "{\"text\":\"x\"}", profilePin = "1111")).code())
            assertEquals(200, api.handle(Kit.call(m, p, "{\"text\":\"x\"}", profilePin = "4321")).status)
        }
        assertTrue(backend.checked.all { it == "p1" })
        assertEquals(200, api.handle(Kit.call("GET", ApiPaths.STATE)).status)
        assertEquals(200, api.handle(Kit.call("GET", ApiPaths.FIELDS)).status)
    }

    @Test fun `CMP_06 kids and guest need the owner's PIN and without one cannot be edited`() {
        val owner = Profile(id = "o", name = "Owner")
        listOf(ProfileKind.KIDS, ProfileKind.GUEST).forEach { kind ->
            val backend = FakeBackend(active = Profile(id = "k", kind = kind, pinHash = "h"), ownerProfile = owner)
            val api = Kit.api(backend)
            assertEquals(403, api.handle(Kit.call("GET", ApiPaths.ROWS)).status)
            assertEquals("refused", api.handle(Kit.call("GET", ApiPaths.ROWS, profilePin = "1234")).code())
            assertEquals("refused", api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, "{\"passphrase\":\"long enough\"}")).code())

            backend.ownerProfile = owner.copy(pinHash = "h")
            backend.pins["o"] = "2468"
            backend.pins["k"] = "1357"
            assertTrue(api.handle(Kit.call("GET", ApiPaths.ROWS)).body.contains("\"who\":\"owner\""))
            assertEquals(401, api.handle(Kit.call("GET", ApiPaths.ROWS, profilePin = "1357")).status)
            assertEquals(200, api.handle(Kit.call("GET", ApiPaths.ROWS, profilePin = "2468")).status)
        }
    }

    @Test fun `CMP_06 state names the active profile and what editing needs, without secrets`() {
        val backend = FakeBackend(active = Profile(id = "p1", name = "Sam", pinHash = "pbkdf2-secret-hash", tmdbKey = "TMDB-KEY"))
        val reply = Kit.api(backend).handle(Kit.call("GET", ApiPaths.STATE))
        assertTrue(reply.body.contains("\"profile\":\"Sam\""))
        assertTrue(reply.body.contains("\"edit\":\"PROFILE_PIN\""))
        assertFalse(reply.body.contains("pbkdf2-secret-hash"))
        assertFalse(reply.body.contains("TMDB-KEY"))
        assertFalse(reply.body.contains(Kit.PIN))
    }

    @Test fun `CMP_06 edit access rules`() {
        val owner = Profile(id = "o", pinHash = "h")
        assertEquals(Need.NONE, EditAccess.forProfile(Profile(id = "a"), owner))
        assertEquals(Need.PROFILE_PIN, EditAccess.forProfile(Profile(id = "a", pinHash = "x"), owner))
        assertEquals(Need.OWNER_PIN, EditAccess.forProfile(Profile(id = "k", kind = ProfileKind.KIDS), owner))
        assertEquals(Need.REFUSED, EditAccess.forProfile(Profile(id = "k", kind = ProfileKind.KIDS), owner.copy(pinHash = null)))
        assertEquals(Need.OWNER_PIN, EditAccess.forBackup(Profile(id = "a"), owner))
        assertEquals(Need.NONE, EditAccess.forBackup(Profile(id = "a"), owner.copy(pinHash = null)))
        assertEquals(Need.REFUSED, EditAccess.forBackup(Profile(id = "g", kind = ProfileKind.GUEST), null))
    }
}
