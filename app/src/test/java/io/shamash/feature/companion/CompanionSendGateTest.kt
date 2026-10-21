package io.shamash.feature.companion

import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.feature.profiles.Restrictions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/send` opens anything (a YouTube video, any link), so it would step around the content level: with a Kids or
 * Guest profile on the TV the phone remote is off (PRF-10), and their queue row never opens anything. SPEC: PRF-10,
 * CMP-03, CMP-11
 */
class CompanionSendGateTest {
    private fun send(pin: String = Kit.PIN, extra: Map<String, String> = emptyMap()) = HttpRequest(
        "POST", "/send", "pin=$pin&text=https://youtu.be/abc",
        mapOf("host" to "${Kit.TV}:${Kit.PORT}", "x-shamash" to "1") + extra,
    )

    private fun router(allowed: () -> Boolean, sent: MutableList<String>, auth: CompanionAuth = Kit.auth()) = CompanionRouter(
        auth, Kit.api(auth = auth), page = { _, n -> n }, send = { sent += it; "sent" },
        words = { w, lang -> "$w:$lang" }, nonce = { "n" }, sendAllowed = allowed,
    )

    @Test fun `PRF_10 a send while Kids or Guest is on the TV is refused and opens nothing`() {
        listOf(ProfileKind.KIDS, ProfileKind.GUEST).forEach { kind ->
            val sent = ArrayList<String>()
            val reply = router({ Restrictions.of(Profile(kind = kind)).phoneRemote }, sent).route(send(), Kit.TV, Kit.PORT)
            assertEquals("$kind", 403, reply.status)
            assertEquals("$kind", "${SendWord.NOT_ALLOWED}:en", reply.body)
            assertTrue("$kind", sent.isEmpty())
        }
    }

    @Test fun `PRF_10 an adult profile keeps the phone remote`() {
        val sent = ArrayList<String>()
        val reply = router({ Restrictions.of(Profile(kind = ProfileKind.ADULT)).phoneRemote }, sent).route(send(), Kit.TV, Kit.PORT)
        assertEquals(200, reply.status)
        assertEquals(listOf("https://youtu.be/abc"), sent)
    }

    @Test fun `CMP_03 the PIN and the door still come first, so a refusal tells a stranger nothing`() {
        val sent = ArrayList<String>()
        val r = router({ false }, sent)
        assertEquals(403, r.route(send(pin = "000000"), Kit.TV, Kit.PORT).status)
        assertEquals("${SendWord.WRONG_PIN}:en", r.route(send(pin = "000000"), Kit.TV, Kit.PORT).body)
        assertEquals("", r.route(send(extra = mapOf("origin" to "http://evil.example")), Kit.TV, Kit.PORT).body)
        assertTrue(sent.isEmpty())
    }

    @Test fun `CMP_11 the queue row is off where the phone remote is`() {
        val queue = QueueProvider(QueueStore(kotlin.io.path.createTempDirectory("queue").toFile()), "io.shamash")
        assertTrue(queue.isEnabled(Profile(kind = ProfileKind.ADULT)))
        assertFalse(queue.isEnabled(Profile(kind = ProfileKind.KIDS)))
        assertFalse(queue.isEnabled(Profile(kind = ProfileKind.GUEST)))
    }
}
