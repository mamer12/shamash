package io.shamash.feature.cinemana

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class ByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

/** A single range only; malformed, overflowing and unsatisfiable requests all produce 416. */
internal fun parseByteRange(header: String?, total: Long): ByteRange? {
    if (total <= 0) return null
    if (header == null) return ByteRange(0, total - 1)
    val match = Regex("bytes=(\\d*)-(\\d*)", RegexOption.IGNORE_CASE).matchEntire(header.trim()) ?: return null
    val (first, last) = match.destructured
    if (first.isEmpty()) {
        val suffix = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return ByteRange((total - suffix).coerceAtLeast(0), total - 1)
    }
    val start = first.toLongOrNull()?.takeIf { it < total } ?: return null
    val end = if (last.isEmpty()) total - 1 else last.toLongOrNull()?.coerceAtMost(total - 1) ?: return null
    return if (end >= start) ByteRange(start, end) else null
}

/** Loopback transport. 24 × 2 MiB cache slots, four downloads and four readers; no disk cache. SPEC: CIN-04 */
class RangeStreamer(source: OkHttpClient, private val remote: String) : Closeable {
    private class Chunk(val pinned: Boolean = false) {
        var loading = false
        var data: ByteArray? = null
        var error: IOException? = null
        var used = System.nanoTime()
    }
    private val client = source.newBuilder().cache(null).callTimeout(25, TimeUnit.SECONDS).build()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val lock = Object()
    private val chunks = linkedMapOf<Long, Chunk>()
    private val readers = mutableMapOf<Socket, Long>()
    private val workers = mutableListOf<Thread>()
    private val outputs = ThreadPoolExecutor(0, 4, 15, TimeUnit.SECONDS, SynchronousQueue(), { r ->
        Thread(r, "cinema-reader").apply { isDaemon = true }
    })
    @Volatile private var closed = false
    private var server: ServerSocket? = null
    var total: Long = 0; private set
    val address: String get() = "http://127.0.0.1:${checkNotNull(server).localPort}/video.mp4"

    /** Call on I/O. Keeping construction separate lets cancellation close an in-flight probe. */
    fun start(): RangeStreamer {
        try {
            total = probe()
            synchronized(lock) {
                if (closed) throw IOException("Closed")
                server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
                chunks[0] = Chunk(true)
                chunks[(total - 1) / CHUNK] = Chunk(true)
                repeat(4) { workers += thread(name = "cinema-range-$it", isDaemon = true) { downloadLoop() } }
                workers += thread(name = "cinema-loopback", isDaemon = true) { acceptLoop() }
            }
            return this
        } catch (e: Exception) { close(); throw e }
    }

    private fun request(range: String): Call {
        val call = client.newCall(Request.Builder().url(remote).header("Range", range)
            .header("Accept-Encoding", "identity").header("Cache-Control", "no-store").build())
        calls += call
        if (closed) { call.cancel(); calls -= call; throw IOException("Closed") }
        return call
    }

    private fun probe(): Long {
        val call = request("bytes=0-0")
        try {
            return call.execute().use { response ->
                val parsed = contentRange(response.header("Content-Range"))
                if (response.code != 206 || parsed == null || parsed.first != ByteRange(0, 0) || parsed.second <= 0)
                    throw IOException("Server does not support byte ranges")
                val body = response.body ?: throw IOException("Missing video")
                if (body.contentLength() !in listOf(-1L, 1L)) throw IOException("Invalid probe size")
                body.byteStream().use { if (it.read() < 0 || it.read() != -1) throw IOException("Invalid probe body") }
                parsed.second
            }
        } finally { calls -= call }
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try { server?.accept() ?: return } catch (_: IOException) { return }
            sockets += socket
            if (closed) { socket.close(); sockets -= socket; return }
            try { outputs.execute { serve(socket) } }
            catch (_: java.util.concurrent.RejectedExecutionException) { sockets -= socket; socket.close() }
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                socket.soTimeout = 8_000
                val input = socket.getInputStream()
                val head = StringBuilder()
                while (head.length < 8192 && !head.endsWith("\r\n\r\n")) {
                    val byte = input.read(); if (byte < 0) return
                    head.append(byte.toChar())
                }
                if (!head.endsWith("\r\n\r\n")) return
                val lines = head.toString().split("\r\n")
                val method = lines.first().substringBefore(' ')
                val headers = lines.drop(1).filter { it.substringBefore(':').equals("Range", true) }
                val rangeHeader = headers.singleOrNull()?.substringAfter(':')?.trim()
                val range = if (headers.size > 1) null else parseByteRange(rangeHeader, total)
                val out = socket.getOutputStream().buffered(64 * 1024)
                val status = when { method !in listOf("GET", "HEAD") -> "405 Method Not Allowed"
                    range == null -> "416 Range Not Satisfiable"
                    rangeHeader != null -> "206 Partial Content"; else -> "200 OK" }
                val length = if (method in listOf("GET", "HEAD")) range?.length ?: 0 else 0
                val content = when { range == null -> "Content-Range: bytes */$total\r\n"
                    rangeHeader != null -> "Content-Range: bytes ${range.start}-${range.end}/$total\r\n"; else -> "" }
                out.write(("HTTP/1.1 $status\r\n${content}Content-Length: $length\r\n" +
                    "Content-Type: video/mp4\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                out.flush()
                if (method != "GET" || range == null) return
                var position = range.start
                while (position <= range.end && !closed) {
                    val index = position / CHUNK
                    val data = await(socket, index)
                    val offset = (position % CHUNK).toInt()
                    val count = minOf(data.size - offset.toLong(), range.end - position + 1).toInt()
                    out.write(data, offset, count)
                    position += count
                }
                out.flush()
            }
        } catch (_: IOException) { /* A seek commonly closes the old connection. */ }
        finally { sockets -= socket; synchronized(lock) { readers.remove(socket); lock.notifyAll() } }
    }

    private fun await(socket: Socket, index: Long): ByteArray = synchronized(lock) {
        readers[socket] = index
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55)
        while (!closed) {
            val chunk = reserve(index) ?: throw IOException("Video buffer full")
            for (ahead in 1..8) {
                if (index + ahead > (total - 1) / CHUNK) break
                reserve(index + ahead) ?: break
            }
            lock.notifyAll()
            chunk.data?.let { chunk.used = System.nanoTime(); return@synchronized it }
            chunk.error?.let { throw it }
            if (System.nanoTime() > deadline) throw IOException("Video range timed out")
            try { lock.wait(500) } catch (_: InterruptedException) { throw IOException("Closed") }
        }
        throw IOException("Closed")
    }

    private fun reserve(index: Long): Chunk? {
        chunks[index]?.let { return it }
        if (chunks.size >= BUDGET) {
            val victim = chunks.entries.filter { !it.value.pinned && !it.value.loading && it.key !in readers.values }
                .minByOrNull { it.value.used } ?: return null
            chunks.remove(victim.key)
        }
        return Chunk().also { chunks[index] = it }
    }

    private fun downloadLoop() {
        while (!closed) {
            val entry = synchronized(lock) {
                val next = chunks.entries.filter { !it.value.loading && it.value.data == null && it.value.error == null }
                    .minByOrNull { if (it.value.pinned) -1L else readers.values.minOfOrNull { r ->
                        if (it.key >= r) it.key - r else Long.MAX_VALUE / 2
                    } ?: Long.MAX_VALUE }
                if (next == null) {
                    try { lock.wait(500) } catch (_: InterruptedException) { return }
                    null
                } else { next.value.loading = true; next.key to next.value }
            } ?: continue
            var data: ByteArray? = null
            var error: IOException? = null
            for (attempt in 0..1) {
                if (closed) return
                try { data = fetch(entry.first); break } catch (e: IOException) { error = e }
            }
            synchronized(lock) {
                entry.second.loading = false
                entry.second.data = data
                entry.second.error = if (data == null) error else null
                lock.notifyAll()
            }
        }
    }

    private fun fetch(index: Long): ByteArray {
        val start = index * CHUNK
        val range = ByteRange(start, start + minOf(CHUNK, total - start) - 1)
        val call = request("bytes=${range.start}-${range.end}")
        try {
            return call.execute().use { response ->
                if (response.code != 206 || contentRange(response.header("Content-Range")) != range to total)
                    throw IOException("Incorrect upstream Content-Range")
                val body = response.body ?: throw IOException("Missing video bytes")
                if (body.contentLength() != -1L && body.contentLength() != range.length) throw IOException("Incorrect byte count")
                val bytes = ByteArray(range.length.toInt())
                body.byteStream().use { input ->
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(bytes, offset, bytes.size - offset)
                        if (count < 0) throw IOException("Truncated video range")
                        offset += count
                    }
                    if (input.read() != -1) throw IOException("Oversized video range")
                }
                bytes
            }
        } finally { calls -= call }
    }

    override fun close() {
        closed = true
        runCatching { server?.close() }
        calls.forEach { it.cancel() }
        sockets.forEach { runCatching { it.close() } }
        outputs.shutdownNow()
        synchronized(lock) { chunks.clear(); readers.clear(); lock.notifyAll() }
        workers.forEach { it.interrupt() }
    }

    companion object {
        private const val CHUNK = 2L * 1024 * 1024
        private const val BUDGET = 24
        private fun contentRange(value: String?): Pair<ByteRange, Long>? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value ?: return null) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            val size = match.groupValues[3].toLongOrNull() ?: return null
            return if (start <= end && end < size) ByteRange(start, end) to size else null
        }
    }
}
