package io.shamash.data

import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Profile
import io.shamash.domain.RowSource
import io.shamash.domain.RowSources
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AddOnsTest {
    @get:Rule val folder = TemporaryFolder()

    private class MemorySecrets : SecretStore {
        val map = mutableMapOf<String, String>()
        override suspend fun secret(key: String) = map[key]
        override suspend fun setSecret(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private val fields = listOf(
        CredentialField("url", 1, CredentialKind.URL),
        CredentialField("token", 2, CredentialKind.TOKEN),
        CredentialField("user", 3, CredentialKind.TEXT, required = false),
    )

    private fun creds(store: SecretStore, profile: String = "p1", addOn: String = "plex") =
        SecretCredentials(store, addOn, fields) { profile }

    private fun addOn(id: String, built: Boolean = true) = AddOn(id, 0, 0) { if (built) error("x") else null }

    @Test fun `PEX_01 registry lists add-ons and one that fails to build is left out`() {
        val registry = AddOnRegistry(listOf(addOn("a"), addOn("b", built = false)))
        val deps = DataDeps(creds(MemorySecrets()), { Profile() }, { false }, emptyMap())
        assertTrue(registry.providers { deps }.isEmpty())
        assertEquals("b", registry.find("b")?.id)
        assertThrows(IllegalArgumentException::class.java) { AddOnRegistry(listOf(addOn("a"), addOn("a"))) }
    }

    @Test fun `PEX_02 enabled add-ons follow the profile`() {
        val registry = AddOnRegistry(listOf(addOn("a"), addOn("b")))
        val p = Profile().withAddon("b", true)
        assertEquals(listOf("b"), registry.enabled(p).map { it.id })
        assertFalse(registry.isOn(registry.find("a")!!, p))
    }

    @Test fun `PEX_03 credentials are stored per profile and add-on under secret keys`() = runTest {
        val store = MemorySecrets()
        creds(store).set("token", "  abc ")
        assertEquals("abc", creds(store).get("token"))
        assertNull(creds(store, profile = "p2").get("token"))
        assertNull(creds(store, addOn = "jellyfin").get("token"))
        assertEquals(setOf("p1_plex_token"), store.map.keys)
        assertNull(creds(store).get("nope"))
        assertTrue(runCatching { creds(store).set("nope", "x") }.exceptionOrNull() is IllegalArgumentException)
        assertFalse(creds(store).isComplete())
        creds(store).set("url", "http://10.0.0.2:32400")
        assertTrue(creds(store).isComplete())
        creds(store).clear()
        assertTrue(store.map.isEmpty())
    }

    @Test fun `PEX_03 blank value clears and toString redacts`() = runTest {
        val store = MemorySecrets()
        val c = creds(store)
        c.set("token", "super-secret-value")
        c.set("token", "  ")
        assertNull(c.get("token"))
        c.set("token", "super-secret-value")
        assertFalse(c.toString().contains("super-secret-value"))
        assertTrue(c.toString().contains("redacted"))
    }

    @Test fun `PEX_04 secrets are not in logs or the HTTP cache`() = runTest {
        val secret = "synthetic-plex-token-123"
        val line = Logger.line("Plex ${Redact.host("http://192.168.1.5:32400/library?X-Plex-Token=$secret")}",
            IOException("http://192.168.1.5/?X-Plex-Token=$secret body"))
        assertFalse(line.contains(secret))
        assertEquals("192.168.1.5", Redact.host("http://192.168.1.5:32400/x?token=$secret"))

        val server = MockWebServer()
        val http = Http(folder.root)
        try {
            server.start()
            repeat(2) { server.enqueue(MockResponse().setHeader("Cache-Control", "public, max-age=3600").setBody("r$it")) }
            val url = server.url("/library?X-Plex-Token=$secret").toString()
            assertEquals("r0", http.text(url))
            assertEquals("r1", http.text(url))
            val tagged = server.url("/playlist.m3u").toString()
            server.enqueue(MockResponse().setHeader("Cache-Control", "public, max-age=3600").setBody("a"))
            server.enqueue(MockResponse().setHeader("Cache-Control", "public, max-age=3600").setBody("b"))
            assertEquals("a", http.text(tagged, mapOf(HttpPrivacy.PRIVATE to "1")))
            assertEquals("b", http.text(tagged, mapOf(HttpPrivacy.PRIVATE to "1")))
            http.client.cache?.flush()
            assertFalse(folder.root.walkTopDown().filter { it.isFile }.any { it.readText().contains(secret) })
            assertFalse(http.client.cache?.urls()?.hasNext() ?: false)
            val header = HttpPrivacy.request(Request.Builder().url(server.url("/x")).header("X-Emby-Token", "t").build())
            assertTrue(header.cacheControl.noStore)
            val sent = HttpPrivacy.request(Request.Builder().url(server.url("/x")).header(HttpPrivacy.PRIVATE, "1").build())
            assertNull(sent.header(HttpPrivacy.PRIVATE))
        } finally {
            http.client.cache?.close()
            server.shutdown()
        }
    }

    @Test fun `PEX_06 row sources default on or off and are saved as row prefs`() {
        val on = RowSource("recent", titleText = "Recently added")
        val off = RowSource("libs", titleText = "Libraries", defaultOn = false)
        var p = Profile()
        assertTrue(RowSources.isOn(p, "plex", on))
        assertFalse(RowSources.isOn(p, "plex", off))
        p = RowSources.withOn(p, "plex", on, false)
        assertFalse(RowSources.isOn(p, "plex", on))
        assertEquals("plex:recent", p.layout.rows.single().rowId)
        p = RowSources.withOn(p, "plex", on, true)
        assertTrue(p.layout.rows.isEmpty())
        p = RowSources.withTitle(RowSources.withOn(p, "plex", off, true), "plex", off, "Mine")
        assertTrue(RowSources.isOn(p, "plex", off))
        assertEquals("Mine", RowSources.customTitle(p, "plex", off))
        assertTrue(RowSources.cleared(p, "plex").layout.rows.isEmpty())
        assertEquals(RowSources.MAX, RowSources.capped((1..20).map { RowSource("s$it", titleText = "t") }).size)
        assertThrows(IllegalArgumentException::class.java) { RowSource("a:b", titleText = "t") }
    }
}
