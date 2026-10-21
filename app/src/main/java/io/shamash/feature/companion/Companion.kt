package io.shamash.feature.companion

import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.data.Preferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Random
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The phone companion: a small page on the home network (http://TV:8765) where a link or a title is sent to the TV.
 * A 6-digit PIN, shown in Settings, keeps the rest of the network out; wrong PINs lock the page for a while. It
 * listens only on the TV's home-network address, once setup is done; a few worker threads answer, each request
 * read within [REQUEST_DEADLINE_MS]. SPEC: CMP-01..05
 */
object Companion {
    const val PORT = 8765
    private const val PIN_KEY = "phone_pin"
    private const val WORKERS = 4
    private const val QUEUE = 8
    private const val READ_TIMEOUT_MS = 1_000
    private const val REQUEST_DEADLINE_MS = 5_000L
    private const val ADDRESS_CHECK_MS = 60_000            // how often an idle server checks the TV kept its address
    private const val ADDRESS_WAIT_MS = 15_000L
    private const val ADDRESS_TRIES = 20                    // ~5 minutes for the network to come up after boot
    private const val WRONG_PIN_DELAY_MS = 1_000L           // slows down guessing the PIN
    private const val NONCE_BYTES = 16
    private val running = AtomicBoolean(false)
    private val guard = PinGuard()
    private val pinLock = Mutex()
    private val random = SecureRandom()

    /**
     * Starts the server once per process; call it only once setup is done (SPEC: CMP-01). Without a home-network
     * address it waits a few minutes for one, then stops; calling it again later retries.
     */
    fun start(locator: ServiceLocator) {
        if (!running.compareAndSet(false, true)) return
        thread(name = "companion", isDaemon = true) {
            try {
                listen(locator)
            } finally {
                running.set(false)
            }
        }
    }

    /** The TV's address on the home network (site-local IPv4, e.g. "192.168.1.20"), or null without one. */
    fun address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** What to open on the phone ("http://192.168.1.20:8765"), or null without a network. */
    fun url(): String? = address()?.let { "http://$it:$PORT" }

    /** The PIN the phone page asks for, made at random the first time and then kept. SPEC: CMP-03 */
    suspend fun pin(prefs: Preferences): String = pinLock.withLock {
        prefs.secret(PIN_KEY)?.takeIf(Pins::isWellFormed) ?: newPin(prefs)
    }

    private suspend fun newPin(prefs: Preferences): String = Pins.fresh(random).also { prefs.setSecret(PIN_KEY, it) }

    /** Serves on the TV's address; when the TV gets a new one, listens again there. */
    private fun listen(locator: ServiceLocator) {
        val tv = PhoneActions(locator)
        val workers = ThreadPoolExecutor(WORKERS, WORKERS, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE)) { r ->
            Thread(r, "companion-worker").apply { isDaemon = true }
        }
        try {
            while (true) {
                val address = waitForAddress()
                if (address == null) {
                    Logger.w("phone page: no home network")
                    return
                }
                if (!serve(locator, tv, workers, address)) return
            }
        } finally {
            workers.shutdownNow()
        }
    }

    private fun waitForAddress(): String? {
        repeat(ADDRESS_TRIES) {
            address()?.let { return it }
            Thread.sleep(ADDRESS_WAIT_MS)
        }
        return null
    }

    /** Accepts until the TV's address changes (true: listen again there) or the socket fails (false). */
    private fun serve(locator: ServiceLocator, tv: PhoneActions, workers: ThreadPoolExecutor, address: String): Boolean {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.soTimeout = ADDRESS_CHECK_MS
            server.bind(InetSocketAddress(InetAddress.getByName(address), PORT))
            while (true) {
                val socket = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    if (address() != address) return true
                    continue
                }
                val deadline = Deadline.after(REQUEST_DEADLINE_MS)
                dispatch(workers, socket) { answer(locator, tv, it, address, deadline) }
            }
        } catch (e: IOException) {
            Logger.w("phone page stopped", e)
            return false
        } finally {
            runCatching { server.close() }
        }
    }

    /** Hands [socket] to a worker; when all are busy and the queue is full, the connection is just closed. */
    private fun dispatch(workers: ThreadPoolExecutor, socket: Socket, work: (Socket) -> Unit) {
        try {
            workers.execute { socket.use(work) }
        } catch (e: RejectedExecutionException) {
            runCatching { socket.close() }
        }
    }

    private fun answer(locator: ServiceLocator, tv: PhoneActions, socket: Socket, address: String, deadline: Deadline) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val request = MiniHttp.read(input, deadline, CompanionGate::accepts) ?: return
            val reply = route(locator, tv, request, address)
            socket.getOutputStream().apply { write(reply.bytes()); flush() }
        } catch (e: Exception) {
            Logger.w("phone page", e)
        }
    }

    private fun route(locator: ServiceLocator, tv: PhoneActions, request: HttpRequest, address: String): HttpReply {
        if (!CompanionGate.hostAllowed(request.headers["host"], address, PORT)) return forbidden()
        if (request.method == "GET") {
            val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(NONCE_BYTES).also(random::nextBytes))
            return HttpReply.html(CompanionPage.render(locator.appContext.resources, nonce), nonce)
        }
        if (!CompanionGate.isPageSend(request)) return forbidden()
        return send(locator, tv, request.body)
    }

    private fun send(locator: ServiceLocator, tv: PhoneActions, form: String): HttpReply {
        // Authentication and rotation share one lock: concurrent workers cannot bypass a newly started lockout.
        val authenticated = synchronized(guard) {
            if (guard.lockedForMs() > 0) return locked(locator)
            val expected = runBlocking { pin(locator.prefs) }
            if (samePin(MiniHttp.field(form, "pin"), expected)) {
                guard.succeeded()
                true
            } else {
                if (guard.failed() == PinGuard.Verdict.ROTATE) runBlocking { pinLock.withLock { newPin(locator.prefs) } }
                false
            }
        }
        if (authenticated) {
            return HttpReply.text(200, "OK", tv.handle(PhoneLink.classify(MiniHttp.field(form, "text"))))
        }
        Thread.sleep(WRONG_PIN_DELAY_MS)
        if (guard.lockedForMs() > 0) return locked(locator)
        return HttpReply.text(403, "Forbidden", locator.appContext.getString(R.string.companion_wrong_pin))
    }

    private fun locked(locator: ServiceLocator) =
        HttpReply.text(429, "Too Many Requests", locator.appContext.getString(R.string.companion_locked))

    private fun forbidden() = HttpReply.text(403, "Forbidden", "")

    private fun samePin(given: String, expected: String) =
        MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
}

/** The PIN's shape: 6 random digits. SPEC: CMP-03 */
object Pins {
    const val DIGITS = 6
    private const val RANGE = 1_000_000

    fun fresh(random: Random): String = random.nextInt(RANGE).toString().padStart(DIGITS, '0')

    fun isWellFormed(pin: String) = pin.length == DIGITS && pin.all { it in '0'..'9' }
}
