package io.shamash.feature.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniHttpTest {
    private fun request(raw: String) = MiniHttp.read(raw.byteInputStream())

    @Test
    fun `CMP_02 reads a GET with the share target's query`() {
        val r = request("GET /?text=hello%20world HTTP/1.1\r\nHost: tv:8765\r\n\r\n")!!

        assertEquals("GET", r.method)
        assertEquals("/?text=hello%20world", r.path)
        assertEquals("", r.body)
    }

    @Test
    fun `CMP_03 reads the PIN and text of a POST form`() {
        val body = "pin=0420&text=https%3A%2F%2Fyoutu.be%2Fabc+def"
        val r = request("POST /send HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.length}\r\n\r\n$body")!!

        assertEquals("0420", MiniHttp.field(r.body, "pin"))
        assertEquals("https://youtu.be/abc def", MiniHttp.field(r.body, "text"))
        assertEquals("", MiniHttp.field(r.body, "missing"))
    }

    @Test
    fun `CMP_05 bodies over 4 KB are cut`() {
        val body = "text=" + "a".repeat(10_000)
        val r = request("POST /send HTTP/1.1\r\ncontent-length: ${body.length}\r\n\r\n$body")!!

        assertEquals(MiniHttp.MAX_BODY, r.body.length)
    }

    @Test
    fun `CMP_05 a body shorter than announced is read as far as it goes`() {
        val r = request("POST /send HTTP/1.1\r\nContent-Length: 500\r\n\r\npin=1")!!

        assertEquals("pin=1", r.body)
    }

    @Test
    fun `CMP_05 an empty or broken request is ignored`() {
        assertNull(request(""))
        assertNull(request("garbage\r\n\r\n"))
    }

    @Test
    fun `CMP_05 malformed percent-encoding falls back to the raw text`() {
        assertEquals("100%", MiniHttp.field("text=100%", "text"))
    }

    @Test
    fun `CMP_05 a reply closes the connection and announces its length`() {
        val bytes = HttpReply.text(403, "Forbidden", "Wrong PIN").bytes().toString(Charsets.UTF_8)

        assertTrue(bytes.startsWith("HTTP/1.1 403 Forbidden\r\n"))
        assertTrue(bytes.contains("Content-Length: 9\r\n"))
        assertTrue(bytes.contains("Connection: close\r\n"))
        assertTrue(bytes.endsWith("\r\n\r\nWrong PIN"))
    }

    @Test
    fun `CMP_02 the page escapes its words`() {
        val page = CompanionPage.fill(mapOf("title" to "Send <to> TV", "wrong" to "Bad \"PIN\"</script>"))

        assertTrue(page.contains("<title>Send &lt;to&gt; TV</title>"))
        assertTrue(page.contains("\"wrong\":\"Bad \\\"PIN\\\"\\u003c/script\\u003e\""))
        assertFalse(page.contains("{{title}}"))
        assertFalse(page.contains("{{messages}}"))
    }
}
