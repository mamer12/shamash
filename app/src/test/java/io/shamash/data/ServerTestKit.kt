package io.shamash.data

import io.shamash.data.youtube.SecretStore
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Test helpers for the own-server add-ons (PEX tests). */
class MemorySecrets : SecretStore {
    val map = mutableMapOf<String, String>()
    override suspend fun secret(key: String) = map[key]
    override suspend fun setSecret(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** A server answering by path (query ignored); unknown paths are 404. Requests are kept in [seen]. */
class FakeServer(private val answers: (RecordedRequest) -> MockResponse?) {
    val seen = mutableListOf<RecordedRequest>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                return answers(request) ?: MockResponse().setResponseCode(404)
            }
        }
        start()
    }

    fun url(path: String = "/") = server.url(path).toString()
    fun shutdown() = server.shutdown()
    fun paths() = synchronized(seen) { seen.map { it.requestUrl!!.encodedPath } }

    companion object {
        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
}

fun say(id: Int) = "s$id"
