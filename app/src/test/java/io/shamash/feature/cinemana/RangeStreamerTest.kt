package io.shamash.feature.cinemana

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RangeStreamerTest {
    @Test fun CIN04_boundedOpenSuffixAndInvalidRanges() {
        assertEquals(ByteRange(0, 99), parseByteRange(null, 100))
        assertEquals(ByteRange(12, 99), parseByteRange("bytes=12-", 100))
        assertEquals(ByteRange(90, 99), parseByteRange("bytes=-10", 100))
        assertEquals(ByteRange(0, 99), parseByteRange("bytes=-1000", 100))
        assertEquals(ByteRange(90, 99), parseByteRange("bytes=90-1000", 100))
        listOf("bytes=-0", "bytes=-", "bytes=100-", "bytes=20-10", "bytes=0-1,3-4", "bytes=99999999999999999999999-", "nonsense")
            .forEach { assertNull(it, parseByteRange(it, 100)) }
    }

    @Test fun CIN04_loopbackServesExactBytesHeadAnd416() {
        val bytes = ByteArray(2 * 1024 * 1024 + 77) { (it % 251).toByte() }
        MockWebServer().use { upstream ->
            upstream.dispatcher = video(bytes)
            upstream.start()
            val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
            RangeStreamer(client, upstream.url("/file").toString()).start().use { stream ->
                assertTrue(stream.address.startsWith("http://127.0.0.1:"))
                fun get(range: String?, method: String = "GET") = client.newCall(Request.Builder().url(stream.address)
                    .method(method, null).apply { range?.let { header("Range", it) } }.build()).execute()
                get("bytes=2097132-2097190").use { response ->
                    assertEquals(206, response.code)
                    assertEquals("bytes 2097132-2097190/${bytes.size}", response.header("Content-Range"))
                    assertArrayEquals(bytes.copyOfRange(2097132, 2097191), response.body!!.bytes())
                }
                get("bytes=-23").use { assertArrayEquals(bytes.takeLast(23).toByteArray(), it.body!!.bytes()) }
                get("bytes=0-9", "HEAD").use {
                    assertEquals(206, it.code); assertEquals("10", it.header("Content-Length")); assertEquals(0, it.body!!.bytes().size)
                }
                get(null, "HEAD").use { assertEquals(200, it.code); assertEquals(bytes.size.toString(), it.header("Content-Length")) }
                get("bytes=44-2").use { assertEquals(416, it.code); assertEquals("bytes */${bytes.size}", it.header("Content-Range")) }
                get("bytes=-0").use { assertEquals(416, it.code) }
            }
        }
    }

    @Test fun CIN04_rejectsLyingProbe() {
        MockWebServer().use { upstream ->
            upstream.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 1-1/100").setBody("x"))
            upstream.start()
            assertThrows(java.io.IOException::class.java) { RangeStreamer(OkHttpClient(), upstream.url("/file").toString()).start() }
        }
    }

    @Test fun CIN04_rejectsWrongChunkContentRange() {
        MockWebServer().use { upstream ->
            upstream.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/100").setBody("x"))
            repeat(2) { upstream.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 1-99/100").setBody("x".repeat(99))) }
            upstream.start()
            val client = OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build()
            RangeStreamer(client, upstream.url("/file").toString()).start().use { stream ->
                assertThrows(java.io.IOException::class.java) {
                    client.newCall(Request.Builder().url(stream.address).build()).execute().use { it.body!!.bytes() }
                }
            }
        }
    }

    @Test fun CIN04_closeStopsBlockedDownloads() {
        MockWebServer().use { upstream ->
            upstream.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.getHeader("Range") == "bytes=0-0")
                    MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/100").setBody("x")
                else MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-99/100").setBody("x".repeat(100))
                    .setBodyDelay(2, TimeUnit.SECONDS)
            }
            upstream.start()
            val stream = RangeStreamer(OkHttpClient(), upstream.url("/file").toString()).start()
            upstream.takeRequest(1, TimeUnit.SECONDS)
            upstream.takeRequest(1, TimeUnit.SECONDS)
            val address = stream.address
            stream.close()
            assertThrows(java.io.IOException::class.java) { OkHttpClient().newCall(Request.Builder().url(address).build()).execute() }
        }
    }

    private fun video(bytes: ByteArray) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = parseByteRange(request.getHeader("Range"), bytes.size.toLong()) ?: return MockResponse().setResponseCode(416)
            return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes ${range.start}-${range.end}/${bytes.size}")
                .setBody(Buffer().write(bytes, range.start.toInt(), range.length.toInt()))
        }
    }
}
