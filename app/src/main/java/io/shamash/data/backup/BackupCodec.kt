package io.shamash.data.backup

import io.shamash.data.backup.BackupLimits as L
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** Why a file was not read or written. None of these carries file content. */
enum class BackupError { WEAK_PASSPHRASE, TOO_LARGE, NOT_A_BACKUP, NEWER_VERSION, WRONG_PASSPHRASE_OR_DAMAGED, INVALID }

sealed interface BackupResult<out T> {
    data class Ok<T>(val value: T) : BackupResult<T>
    data class Failed(val error: BackupError) : BackupResult<Nothing>
}

@Serializable
private data class Envelope(
    val v: Int,
    val kdf: String = "",
    val iter: Int = 0,
    val salt: String = "",
    val nonce: String = "",
    val ct: String = "",
)

/**
 * The backup file (CMP-13..15, PLT-02): `{"v":1,...}` JSON holding a PBKDF2 salt and iteration count, a GCM nonce and
 * the sealed payload. There is no unencrypted form. Opening a file checks size, envelope, version, authenticated
 * decryption and schema, in that order, and never throws: every problem is a [BackupError]. The caller applies the
 * result in one swap, so a refused file changes nothing. Pure and thread-safe. SPEC: PLT-02, CMP-12..15
 */
class BackupCodec(
    private val iterations: Int = L.MIN_ITERATIONS,
    private val currentVersion: Int = VERSION,
    private val migrations: BackupMigrations = BackupMigrations(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init { require(iterations in L.MIN_ITERATIONS..L.MAX_ITERATIONS) { "iterations" } }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    /** Seals [payload] (limited to [options]) with [passphrase]. */
    fun export(payload: BackupPayload, passphrase: String, options: BackupOptions = BackupOptions()): BackupResult<ByteArray> {
        if (passphrase.length < L.MIN_PASSPHRASE || passphrase.length > L.MAX_PASSPHRASE) return failed(BackupError.WEAK_PASSPHRASE)
        val body = BackupSnapshot.restrict(payload.copy(createdAt = clock()), options)
        val checked = BackupValidator.check(body).getOrElse { return failed(BackupError.INVALID) }
        val plain = json.encodeToString(BackupPayload.serializer(), checked).toByteArray(Charsets.UTF_8)
        if (plain.size > L.MAX_FILE_BYTES / 2) return failed(BackupError.TOO_LARGE)
        val salt = BackupCrypto.salt()
        val nonce = BackupCrypto.nonce()
        val key = BackupCrypto.key(passphrase, salt, iterations)
        val sealed = BackupCrypto.encrypt(key, nonce, aad(currentVersion, iterations, b64.encodeToString(salt)), plain)
        val envelope = Envelope(currentVersion, KDF, iterations, b64.encodeToString(salt), b64.encodeToString(nonce), b64.encodeToString(sealed))
        val bytes = json.encodeToString(Envelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
        return if (bytes.size > L.MAX_FILE_BYTES) failed(BackupError.TOO_LARGE) else BackupResult.Ok(bytes)
    }

    /** Opens [file]; the payload comes back validated and migrated to the current shape. */
    fun open(file: ByteArray, passphrase: String): BackupResult<BackupPayload> {
        if (file.size > L.MAX_FILE_BYTES) return failed(BackupError.TOO_LARGE)
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), file.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            return failed(BackupError.NOT_A_BACKUP)
        } catch (e: IllegalArgumentException) {
            return failed(BackupError.NOT_A_BACKUP)
        }
        if (envelope.v > currentVersion) return failed(BackupError.NEWER_VERSION)
        if (envelope.v < 1 || envelope.kdf != KDF || envelope.iter !in L.MIN_ITERATIONS..L.MAX_ITERATIONS) {
            return failed(BackupError.NOT_A_BACKUP)
        }
        val salt = decode(envelope.salt)?.takeIf { it.size == BackupCrypto.SALT_BYTES } ?: return failed(BackupError.NOT_A_BACKUP)
        val nonce = decode(envelope.nonce)?.takeIf { it.size == BackupCrypto.NONCE_BYTES } ?: return failed(BackupError.NOT_A_BACKUP)
        val sealed = decode(envelope.ct)?.takeIf { it.isNotEmpty() } ?: return failed(BackupError.NOT_A_BACKUP)
        val key = BackupCrypto.key(passphrase, salt, envelope.iter)
        val plain = BackupCrypto.decrypt(key, nonce, aad(envelope.v, envelope.iter, envelope.salt), sealed)
            ?: return failed(BackupError.WRONG_PASSPHRASE_OR_DAMAGED)
        return payload(plain, envelope.v)
    }

    private fun payload(plain: ByteArray, version: Int): BackupResult<BackupPayload> = try {
        val raw = json.parseToJsonElement(plain.toString(Charsets.UTF_8)) as? JsonObject
            ?: return failed(BackupError.INVALID)
        val upgraded = migrations.upgrade(raw, version, currentVersion) ?: return failed(BackupError.INVALID)
        val payload = json.decodeFromJsonElement(BackupPayload.serializer(), upgraded)
        BackupValidator.check(payload).fold({ BackupResult.Ok(it) }, { failed(BackupError.INVALID) })
    } catch (e: SerializationException) {
        failed(BackupError.INVALID)
    } catch (e: IllegalArgumentException) {
        failed(BackupError.INVALID)
    }

    private fun decode(text: String): ByteArray? = try { unb64.decode(text) } catch (e: IllegalArgumentException) { null }

    private fun aad(v: Int, iter: Int, salt: String) = "shamash-backup|$v|$KDF|$iter|$salt".toByteArray(Charsets.UTF_8)

    private fun failed(e: BackupError): BackupResult.Failed = BackupResult.Failed(e)

    companion object {
        const val VERSION = 1
        const val KDF = "pbkdf2-sha256"
        const val EXTENSION = "shamash"

        fun fileName(day: String) = "shamash-backup-$day.$EXTENSION"
    }
}
