package io.shamash.feature.companion

import io.shamash.data.backup.BackupAttempts
import io.shamash.data.backup.BackupCodec
import io.shamash.data.backup.BackupError
import io.shamash.data.backup.BackupLimits
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupResult
import io.shamash.feature.companion.PhoneApi.Companion.answer
import io.shamash.feature.companion.PhoneApi.Companion.fail

/**
 * Backup from the phone, always passphrase-encrypted (there is no other kind). Export needs a "yes" on the TV's
 * screen before a file is made, so a stolen phone PIN can't take the data quietly; import is opened and checked
 * whole (version, authenticated decryption, schema, sizes), then previewed on the TV where Merge or Replace (owner
 * PIN) is chosen, and a failure changes nothing. Three wrong passphrases mean a one-minute wait. The passphrase is
 * never stored, logged or sent back. SPEC: CMP-13, CMP-14, CMP-15
 */
class PhoneBackupRoutes(
    private val backend: PhoneBackend,
    private val attempts: BackupAttempts = BackupAttempts(),
) {
    /** SPEC: CMP-13, CMP-14 */
    suspend fun export(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(ExportIn.serializer(), body)
        if (input.passphrase.length !in BackupLimits.MIN_PASSPHRASE..BackupLimits.MAX_PASSPHRASE) {
            return fail(400, code(BackupError.WEAK_PASSPHRASE))
        }
        when (backend.confirmExport()) {
            Confirm.BUSY -> return fail(409, "busy")
            Confirm.NO -> return fail(403, "declined")
            Confirm.YES -> Unit
        }
        val options = BackupOptions(includeSecrets = input.includeKeys, includePins = input.includePins)
        return when (val r = backend.exportFile(input.passphrase, options)) {
            is BackupResult.Ok -> HttpReply.attachment(BackupCodec.fileName(backend.today()), r.value.toString(Charsets.UTF_8))
            is BackupResult.Failed -> fail(400, code(r.error))
        }
    }

    /** SPEC: CMP-15 */
    suspend fun import(body: String): HttpReply {
        val input = PhoneJson.strict.decodeFromString(ImportIn.serializer(), body)
        val wait = attempts.waitLeftMs()
        if (wait > 0) return answer(Answer(false, "wait", seconds = (wait + MS - 1) / MS), 429)
        val file = input.file.toByteArray(Charsets.UTF_8)
        if (file.size > BackupLimits.MAX_FILE_BYTES) return fail(413, code(BackupError.TOO_LARGE))
        val payload = when (val r = backend.openFile(file, input.passphrase)) {
            is BackupResult.Ok -> r.value.also { attempts.succeeded() }
            is BackupResult.Failed -> {
                if (r.error == BackupError.WRONG_PASSPHRASE_OR_DAMAGED) attempts.failed()
                return fail(400, code(r.error))
            }
        }
        return when (val outcome = backend.confirmImport(payload)) {
            is ImportOutcome.Done -> answer(Answer(true, "imported", added = outcome.added, replaced = outcome.replaced))
            ImportOutcome.Declined -> fail(403, "declined")
            ImportOutcome.Busy -> fail(409, "busy")
            ImportOutcome.Failed -> fail(500, "import_failed")
        }
    }

    private fun code(e: BackupError) = "backup_" + e.name.lowercase()

    private companion object {
        const val MS = 1_000L
    }
}
