package io.shamash.feature.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `/api` route, not a sample of them: the door (Host, Origin, Sec-Fetch-Site, `X-Shamash: 1`), the companion
 * PIN and its lockout, and the body limits, all enforced through the router. SPEC: CMP-03, CMP-05, CMP-06
 */
class CompanionDoorTest {
    private val small = "{}"

    /** Every route constant in [ApiPaths], read by reflection so a new route can't be left out of the lists. */
    private fun declared(): Set<String> = ApiPaths::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }
        .map { it.get(null) as String }
        .filter { it.startsWith(ApiPaths.PREFIX) && it != ApiPaths.PREFIX }
        .toSet()

    @Test fun `CMP_06 every declared route is in the read or write list and the reader accepts it`() {
        val routes = declared()
        assertTrue(routes.toString(), routes.size >= 8)
        assertEquals(routes, ApiPaths.GETS + ApiPaths.POSTS)
        ApiPaths.GETS.forEach { assertTrue(it, CompanionGate.accepts("GET", it)) }
        ApiPaths.POSTS.forEach { assertTrue(it, CompanionGate.accepts("POST", it)) }
        listOf("/api", "/api/", "/api/state/", "/api/rows?x=1", "/api/../send", "/API/state").forEach { path ->
            assertTrue(path, !CompanionGate.accepts("GET", path) && !CompanionGate.accepts("POST", path))
        }
    }

    @Test fun `CMP_06 the door refuses every route from another host, origin or site, or without the header`() {
        val backend = FakeBackend()
        val api = Kit.api(backend)
        val router = Kit.router(api)
        val doors = mapOf(
            "host" to mapOf("host" to "evil.example:${Kit.PORT}"),
            "rebound host" to mapOf("host" to "tv.attacker.example"),
            "origin" to mapOf("origin" to "http://evil.example"),
            "null origin" to mapOf("origin" to "null"),
            "cross-site" to mapOf("sec-fetch-site" to "cross-site"),
            "same-site" to mapOf("sec-fetch-site" to "same-site"),
            "header" to mapOf("x-shamash" to "0"),
        )
        Kit.ENDPOINTS.forEach { (method, path) ->
            doors.forEach { (why, extra) ->
                val reply = router.route(Kit.call(method, path, small, extra = extra), Kit.TV, Kit.PORT)
                assertEquals("$method $path $why", 403, reply.status)
            }
            val bare = Kit.call(method, path, small).let { it.copy(headers = it.headers - "x-shamash") }
            assertEquals("$method $path bare", 403, router.route(bare, Kit.TV, Kit.PORT).status)
        }
        assertTrue(backend.saved.isEmpty())
        assertEquals(0, backend.exports)
    }

    @Test fun `CMP_03 a wrong PIN on any route counts toward the one lockout`() {
        Kit.ENDPOINTS.forEach { (method, path) ->
            var now = 0L
            val auth = Kit.auth({ now })
            val router = Kit.router(Kit.api(auth = auth), auth)
            repeat(PinGuard.LIMIT - 1) {
                assertEquals("$method $path", 403, router.route(Kit.call(method, path, small, pin = "000000"), Kit.TV, Kit.PORT).status)
            }
            router.route(Kit.call(method, path, small, pin = "000000"), Kit.TV, Kit.PORT)   // the one that locks
            val locked = router.route(Kit.call(method, path, small), Kit.TV, Kit.PORT)
            assertEquals("$method $path", 429, locked.status)
            now += PinGuard.FIRST_LOCK_MS
            assertTrue("$method $path", router.route(Kit.call(method, path, small), Kit.TV, Kit.PORT).status != 429)
        }
    }

    @Test fun `CMP_06 every write route refuses a body over its limit unread, through the reader and the router`() {
        val router = Kit.router(Kit.api())
        ApiPaths.POSTS.forEach { path ->
            val max = if (path == ApiPaths.BACKUP_IMPORT) CompanionGate.IMPORT_BODY else CompanionGate.EDIT_BODY
            val raw = "POST $path HTTP/1.1\r\nHost: ${Kit.TV}:${Kit.PORT}\r\nX-Shamash: 1\r\nX-Shamash-Pin: ${Kit.PIN}\r\n" +
                "Content-Length: ${max + 1}\r\n\r\n" + "x".repeat(16)
            val read = MiniHttp.read(raw.byteInputStream(), accept = CompanionGate::accepts, limit = CompanionGate::bodyLimit)!!
            assertEquals(path, "", read.body)
            assertEquals(path, 413, router.route(read, Kit.TV, Kit.PORT).status)
        }
    }

    @Test fun `CMP_08 no answer on any route repeats the companion PIN`() {
        val router = Kit.router(Kit.api())
        Kit.ENDPOINTS.forEach { (method, path) ->
            val reply = router.route(Kit.call(method, path, small), Kit.TV, Kit.PORT)
            assertTrue("$method $path", Kit.PIN !in reply.body)
            assertNull(reply.headers.keys.firstOrNull { it.equals("Access-Control-Allow-Origin", ignoreCase = true) })
        }
    }
}
