package io.shamash.feature.tvcontrol.adb

import io.shamash.data.Logger
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Runs one shell command and returns its output (stdout and stderr together). Blocking: call it off the main thread. */
fun interface AdbShell {
    fun run(command: String): String
}

/**
 * A client for the TV's own adbd (127.0.0.1:5555 while network/USB debugging is on): CNXN, AUTH with [AdbKey], then
 * `shell:` streams. Only Shamash's own fixed commands go through it. SPEC: TVC-01
 */
class AdbClient internal constructor(private val link: Link) : AdbShell, Closeable {

    /** The byte pipe under the protocol (a socket on the TV, scripted streams in tests). */
    internal interface Link : Closeable {
        val input: InputStream
        val output: OutputStream
        fun setTimeout(millis: Int)
    }

    private val input = DataInputStream(link.input)
    private var nextLocalId = 1

    override fun run(command: String): String {
        Logger.w("adb> $command")
        val output = try {
            openAndRead(command)
        } catch (e: SocketTimeoutException) {
            throw AdbException(AdbError.NO_RESPONSE, e)
        }
        Logger.w("adb< ${output.trim().take(LOG_OUTPUT_CHARS)}")
        return output
    }

    override fun close() {
        runCatching { link.close() }
    }

    private fun openAndRead(command: String): String {
        val local = nextLocalId++
        send(AdbMessage(AdbMessage.OPEN, local, 0, "shell:$command\u0000".toByteArray(Charsets.UTF_8)))
        var remote = 0
        val result = ByteArrayOutputStream()
        while (true) {
            val m = AdbMessage.read(input)
            if (m.arg1 != local) continue // another stream's leftovers
            when (m.command) {
                AdbMessage.OKAY -> remote = m.arg0
                AdbMessage.WRTE -> {
                    result.write(m.data)
                    send(AdbMessage(AdbMessage.OKAY, local, m.arg0))
                }
                AdbMessage.CLSE -> {
                    if (remote == 0 && m.arg0 == 0) throw AdbException(AdbError.COMMAND_REFUSED)
                    send(AdbMessage(AdbMessage.CLSE, local, m.arg0))
                    return result.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    private fun send(message: AdbMessage) {
        link.output.write(message.encode())
        link.output.flush()
    }

    /** CNXN → AUTH(token) → our signature → (unknown key) our public key → the user's OK → CNXN. */
    private fun handshake(key: AdbKey, waitForApproval: Boolean, onApproval: () -> Unit) {
        send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_DATA, BANNER))
        var signed = false
        var offeredKey = false
        while (true) {
            val m = try {
                AdbMessage.read(input)
            } catch (e: SocketTimeoutException) {
                throw AdbException(if (offeredKey) AdbError.NOT_APPROVED else AdbError.NO_RESPONSE, e)
            } catch (e: EOFException) {
                throw AdbException(if (offeredKey) AdbError.NOT_APPROVED else AdbError.NO_RESPONSE, e)
            }
            when {
                m.command == AdbMessage.CNXN -> {
                    link.setTimeout(TIMEOUT_MS)
                    return
                }
                m.command == AdbMessage.STLS -> throw AdbException(AdbError.TLS_REQUIRED)
                m.command != AdbMessage.AUTH || m.arg0 != AdbMessage.AUTH_TOKEN -> throw AdbException(AdbError.PROTOCOL)
                !signed -> {
                    send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, key.sign(m.data)))
                    signed = true
                }
                offeredKey -> throw AdbException(AdbError.KEY_REJECTED)
                !waitForApproval -> throw AdbException(AdbError.NOT_APPROVED)
                else -> {
                    // The TV doesn't know our key: it now shows "Allow debugging?".
                    send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, key.publicKeyPayload()))
                    offeredKey = true
                    onApproval()
                    link.setTimeout(APPROVAL_MS)
                }
            }
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val TIMEOUT_MS = 10_000
        const val APPROVAL_MS = 60_000
        private const val LOG_OUTPUT_CHARS = 300
        private val BANNER = "host::\u0000".toByteArray(Charsets.US_ASCII)

        /**
         * Connects and authenticates. With [waitForApproval], an unknown key is offered and the TV's prompt is
         * awaited up to [APPROVAL_MS] ([onApproval] fires when the prompt shows); without it, that fails fast.
         */
        fun connect(
            key: AdbKey,
            waitForApproval: Boolean,
            onApproval: () -> Unit,
            host: String = HOST,
            port: Int = PORT,
        ): AdbClient {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            } catch (e: IOException) {
                socket.close()
                throw AdbException(AdbError.DEBUGGING_OFF, e)
            }
            socket.soTimeout = TIMEOUT_MS
            return open(SocketLink(socket), key, waitForApproval, onApproval)
        }

        internal fun open(link: Link, key: AdbKey, waitForApproval: Boolean, onApproval: () -> Unit): AdbClient {
            val client = AdbClient(link)
            try {
                client.handshake(key, waitForApproval, onApproval)
            } catch (e: IOException) {
                client.close()
                throw e as? AdbException ?: AdbException(AdbError.NO_RESPONSE, e)
            }
            return client
        }
    }

    private class SocketLink(private val socket: Socket) : Link {
        override val input: InputStream = socket.getInputStream()
        override val output: OutputStream = socket.getOutputStream()
        override fun setTimeout(millis: Int) {
            socket.soTimeout = millis
        }
        override fun close() = socket.close()
    }
}
