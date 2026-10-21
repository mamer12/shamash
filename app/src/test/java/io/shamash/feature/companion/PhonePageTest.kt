package io.shamash.feature.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// SPEC: CMP-02, CMP-03, CMP-16
class PhonePageTest {
    @Test fun `CMP_16 the page follows the phone's language`() {
        assertEquals("ar", PageLanguage.of("ar-IQ,ar;q=0.9,en;q=0.8"))
        assertEquals("en", PageLanguage.of("en-US,ar;q=0.8"))
        assertEquals("ar", PageLanguage.of("fr-FR, ar;q=0.5, en;q=0.4"))
        assertEquals("en", PageLanguage.of("fr-FR"))
        assertEquals("en", PageLanguage.of(null))
        assertEquals("en", PageLanguage.of("ar;q=0, en"))
        assertTrue(PageLanguage.isRtl("ar"))
        assertFalse(PageLanguage.isRtl("en"))
    }

    @Test fun `CMP_16 the router renders the page in the phone's language with a fresh nonce and its CSP`() {
        val router = Kit.router(Kit.api())
        val request = HttpRequest("GET", "/?url=https%3A%2F%2Fexample.org", "", mapOf("host" to "${Kit.TV}:${Kit.PORT}", "accept-language" to "ar"))
        val reply = router.route(request, Kit.TV, Kit.PORT)
        assertEquals("page:ar:n0nce", reply.body)
        assertTrue(reply.headers.getValue("Content-Security-Policy").contains("'nonce-n0nce'"))
    }

    @Test fun `CMP_16 page words are escaped in the markup and in the script, and the script keeps the nonce`() {
        val page = CompanionPage.fill(mapOf("title" to "<b>\"x\"</b>", "dir" to "rtl", "lang" to "ar", "wrong" to "</script><script>alert(1)"), "abc")
        assertTrue(page.contains("dir=\"rtl\""))
        assertTrue(page.contains("<title>&lt;b&gt;&quot;x&quot;&lt;/b&gt;</title>"))
        assertFalse(page.contains("</script><script>alert(1)"))
        assertTrue(page.contains("<script nonce=\"abc\">"))
        assertEquals(1, Regex("<script").findAll(page).count())
        assertTrue(page.contains("X-Shamash-Pin"))
    }

    @Test fun `CMP_03 send through the router keeps its PIN rules and answers in the phone's language`() {
        val sent = ArrayList<String>()
        val auth = Kit.auth()
        val router = Kit.router(Kit.api(auth = auth), auth, sent)
        val headers = mapOf("host" to "${Kit.TV}:${Kit.PORT}", "x-shamash" to "1", "accept-language" to "ar")
        val wrong = router.route(HttpRequest("POST", "/send", "pin=000000&text=hi", headers), Kit.TV, Kit.PORT)
        assertEquals(403, wrong.status)
        assertEquals("WRONG_PIN:ar", wrong.body)
        val ok = router.route(HttpRequest("POST", "/send", "pin=${Kit.PIN}&text=Dune%202", headers), Kit.TV, Kit.PORT)
        assertEquals(200, ok.status)
        assertEquals(listOf("Dune 2"), sent)
        val noHeader = router.route(HttpRequest("POST", "/send", "pin=${Kit.PIN}&text=x", headers - "x-shamash"), Kit.TV, Kit.PORT)
        assertEquals(403, noHeader.status)
    }
}
