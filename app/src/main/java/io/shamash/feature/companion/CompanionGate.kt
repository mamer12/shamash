package io.shamash.feature.companion

import java.net.SocketTimeoutException
import java.util.Locale

/**
 * Who gets past the phone page's door. Pure: no sockets, no Android; the clock is passed in. SPEC: CMP-03, CMP-05,
 * CMP-06
 */
object CompanionGate {
    /** The header the page's fetch sends: a cross-site form can't set it, and a cross-site fetch needs a preflight. */
    const val SEND_HEADER = "x-shamash"
    const val SEND_HEADER_VALUE = "1"
    const val SEND_PATH = "/send"
    const val EDIT_BODY = 64 * 1024                         // CMP-06: edits
    const val IMPORT_BODY = 1 shl 20                        // CMP-06: backup import

    /** Only the page, `/send` and the phone API are read at all; anything else is dropped before its headers. */
    fun accepts(method: String, path: String): Boolean = when (method) {
        "GET" -> path == "/" || path.startsWith("/?") || path in ApiPaths.GETS
        "POST" -> path == SEND_PATH || path in ApiPaths.POSTS
        else -> false
    }

    /** `/send` keeps cutting at 4 KB (CMP-05); API bodies over their limit are refused unread (CMP-06). */
    fun bodyLimit(method: String, path: String): BodyLimit = when {
        method != "POST" -> BodyLimit(0, cut = true)
        path == SEND_PATH -> BodyLimit(MiniHttp.MAX_BODY, cut = true)
        path == ApiPaths.BACKUP_IMPORT -> BodyLimit(IMPORT_BODY, cut = false)
        else -> BodyLimit(EDIT_BODY, cut = false)
    }

    /** The Host header names the TV itself (`192.168.1.20:8765`), so a rebound DNS name can't reach the page. */
    fun hostAllowed(host: String?, address: String, port: Int): Boolean =
        host != null && host.trim().lowercase(Locale.ROOT) == "$address:$port"

    /**
     * A browser that says where a request comes from must say it comes from the page itself: an `Origin` other than
     * the TV's, or a `Sec-Fetch-Site` other than same-origin, is another site trying its luck. SPEC: CMP-06
     */
    fun originAllowed(headers: Map<String, String>, address: String, port: Int): Boolean {
        val origin = headers["origin"]?.trim()?.lowercase(Locale.ROOT)
        if (origin != null && origin != "http://$address:$port") return false
        val site = headers["sec-fetch-site"]?.trim()?.lowercase(Locale.ROOT)
        return site == null || site == "same-origin" || site == "none"
    }

    /** A send from the page itself: POST `/send` carrying [SEND_HEADER]. */
    fun isPageSend(request: HttpRequest): Boolean =
        request.method == "POST" && request.path == SEND_PATH && request.headers[SEND_HEADER] == SEND_HEADER_VALUE

    /** A call to the phone API from the page itself (the header is required on reads too). SPEC: CMP-06 */
    fun isApiCall(request: HttpRequest): Boolean =
        request.path.startsWith(ApiPaths.PREFIX) && request.headers[SEND_HEADER] == SEND_HEADER_VALUE

    /** True when the client announced a body longer than this route reads. */
    fun tooLarge(request: HttpRequest): Boolean {
        val rule = bodyLimit(request.method, request.path)
        return !rule.cut && request.declaredLength > rule.max
    }
}

/** The phone API's routes. SPEC: CMP-06..15 */
object ApiPaths {
    const val PREFIX = "/api/"
    const val STATE = "/api/state"
    const val ROWS = "/api/rows"
    const val TEAMS = "/api/teams"
    const val FEEDS = "/api/feeds"
    const val FIELDS = "/api/fields"
    const val QUEUE = "/api/queue"
    const val BACKUP_EXPORT = "/api/backup/export"
    const val BACKUP_IMPORT = "/api/backup/import"

    val GETS = setOf(STATE, ROWS, TEAMS, FEEDS, FIELDS)
    val POSTS = setOf(ROWS, TEAMS, FEEDS, FIELDS, QUEUE, BACKUP_EXPORT, BACKUP_IMPORT)
}

/**
 * Wrong PINs, counted for everyone together: [LIMIT] wrong ones lock the page for [FIRST_LOCK_MS], each lock after
 * that twice as long (up to [MAX_LOCK_MS]); after [ROTATE_AFTER] locks the PIN should change. While locked, even the
 * right PIN is refused. Thread-safe. SPEC: CMP-03
 */
class PinGuard(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    enum class Verdict { WRONG, LOCKED, ROTATE }

    private var failures = 0
    private var locks = 0
    private var lockedUntil = 0L

    /** Milliseconds until the page opens again; 0 when it is open. */
    @Synchronized
    fun lockedForMs(): Long = (lockedUntil - clock()).coerceAtLeast(0)

    @Synchronized
    fun succeeded() {
        failures = 0
        locks = 0
    }

    /** One more wrong PIN: [Verdict.ROTATE] means the caller should make a new PIN now. */
    @Synchronized
    fun failed(): Verdict {
        failures++
        if (failures < LIMIT) return Verdict.WRONG
        failures = 0
        locks++
        lockedUntil = clock() + lockMs(locks)
        if (locks < ROTATE_AFTER) return Verdict.LOCKED
        locks = 0 // a new PIN starts the count again
        return Verdict.ROTATE
    }

    private fun lockMs(lock: Int): Long =
        (FIRST_LOCK_MS shl (lock - 1).coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_LOCK_MS)

    companion object {
        const val LIMIT = 5
        const val ROTATE_AFTER = 3
        const val FIRST_LOCK_MS = 5 * 60_000L
        const val MAX_LOCK_MS = 24 * 60 * 60_000L
        private const val MAX_DOUBLINGS = 10
    }
}

/**
 * An absolute time limit for reading one request, so a client trickling bytes can't hold a worker past it.
 * [clock] is in nanoseconds.
 */
class Deadline(private val endNanos: Long, private val clock: () -> Long = System::nanoTime) {
    fun check() {
        if (clock() - endNanos >= 0) throw SocketTimeoutException("request took too long")
    }

    companion object {
        fun after(ms: Long, clock: () -> Long = System::nanoTime) = Deadline(clock() + ms * 1_000_000, clock)
    }
}
