package io.shamash.feature.tvcontrol.adb

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One ADB protocol v1 message: a 24-byte little-endian header (command, arg0, arg1, length, checksum, magic) and its
 * payload. Protocol: android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt
 * SPEC: TVC-01
 */
class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray = EMPTY) {

    fun encode(): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(data.size)
            .putInt(checksum(data)).putInt(command.inv())
            .put(data)
            .array()

    override fun toString() = "${name(command)}($arg0, $arg1, ${data.size} bytes)"

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val STLS = 0x534c5453

        const val VERSION = 0x01000001
        const val MAX_DATA = 256 * 1024

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val HEADER_SIZE = 24
        private const val MAX_PAYLOAD = 1024 * 1024
        private val EMPTY = ByteArray(0)

        /** Sum of the payload's unsigned bytes (adbd ≥ 1.0.1 no longer checks it, older ones do). */
        fun checksum(data: ByteArray): Int = data.sumOf { it.toInt() and 0xff }

        /** Reads one message; throws [AdbException] on a bad magic or an absurd length. */
        fun read(input: InputStream): AdbMessage {
            val stream = input as? DataInputStream ?: DataInputStream(input)
            val header = ByteArray(HEADER_SIZE)
            stream.readFully(header)
            val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int
            val arg0 = b.int
            val arg1 = b.int
            val length = b.int
            b.int // checksum: not verified, as adbd itself doesn't since protocol 1.0.1
            val magic = b.int
            if (magic != command.inv()) throw AdbException(AdbError.PROTOCOL)
            if (length !in 0..MAX_PAYLOAD) throw AdbException(AdbError.PROTOCOL)
            val data = ByteArray(length)
            stream.readFully(data)
            return AdbMessage(command, arg0, arg1, data)
        }

        private fun name(command: Int): String =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array()
                .toString(Charsets.US_ASCII)
    }
}

/** Why talking to the TV's ADB failed; the Settings screen explains each one. */
enum class AdbError {
    /** Port 5555 closed: network/USB debugging is off. */
    DEBUGGING_OFF,
    /** Connected, but adbd didn't answer in time. */
    NO_RESPONSE,
    /** The TV asked "Allow debugging?" and nobody accepted in time (or we were told not to wait). */
    NOT_APPROVED,
    /** The TV refused our key outright. */
    KEY_REJECTED,
    /** adbd asked for TLS (Android 11+ wireless debugging): only the classic port works. */
    TLS_REQUIRED,
    /** A malformed or unexpected message. */
    PROTOCOL,
    /** adbd closed the shell stream without opening it. */
    COMMAND_REFUSED,
    /** The app's key couldn't be created or read. */
    KEY_STORAGE,
}

class AdbException(val error: AdbError, cause: Throwable? = null) :
    java.io.IOException(error.name, cause)
