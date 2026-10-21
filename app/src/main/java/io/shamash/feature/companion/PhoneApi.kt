package io.shamash.feature.companion

import io.shamash.data.Logger
import io.shamash.domain.ProfileKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * The phone API (`/api/…`). The door checks (Host, Origin, `X-Shamash: 1`) are done by the caller; here every call
 * then needs the companion PIN (header `X-Shamash-Pin`, the one lockout of CMP-03), and every change to the active
 * profile its edit access (header `X-Shamash-Profile-Pin`, CMP-06). PINs travel in headers and secrets in bodies,
 * never in a URL, and no answer repeats either. Runs on a server worker. SPEC: CMP-06..15
 */
class PhoneApi(
    private val auth: CompanionAuth,
    private val backend: PhoneBackend,
    private val fields: PhoneFields,
    private val backup: PhoneBackupRoutes = PhoneBackupRoutes(backend),
) {
    private val edits = PhoneEditRoutes(backend)

    fun handle(request: HttpRequest): HttpReply = runBlocking {
        if (CompanionGate.tooLarge(request)) return@runBlocking fail(413, "too_large")
        when (auth.check(request.headers[PIN_HEADER])) {
            CompanionAuth.Verdict.WRONG -> return@runBlocking fail(403, "pin")
            CompanionAuth.Verdict.LOCKED -> return@runBlocking fail(429, "locked")
            CompanionAuth.Verdict.OK -> Unit
        }
        try {
            route(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            fail(400, "invalid")
        } catch (e: IllegalArgumentException) {
            fail(400, "invalid")
        } catch (e: Exception) {
            Logger.w("phone api ${request.path}", e)
            fail(500, "failed")
        }
    }

    private suspend fun route(r: HttpRequest): HttpReply {
        val get = r.method == "GET"
        return when (r.path) {
            ApiPaths.STATE -> state()
            ApiPaths.FIELDS -> if (get) ok(FieldsOut.serializer(), FieldsOut(fields.list())) else fill(r)
            ApiPaths.BACKUP_EXPORT -> guarded(r, backupNeed()) { backup.export(r.body) }
            ApiPaths.BACKUP_IMPORT -> guarded(r, backupNeed()) { backup.import(r.body) }
            else -> guarded(r, profileNeed()) { edits.route(r) }
        }
    }

    private suspend fun state(): HttpReply {
        val p = backend.active()
        val out = StateOut(
            profile = p.name, kind = p.kind.name, edit = profileNeed().name, backup = backupNeed().name,
            queue = backend.queue(p.id).size, fields = fields.size(),
        )
        return ok(StateOut.serializer(), out)
    }

    /** Hands a value to a TV field; the answer never carries the value. SPEC: CMP-08 */
    private fun fill(r: HttpRequest): HttpReply {
        val input = PhoneJson.strict.decodeFromString(FieldIn.serializer(), r.body)
        return if (fields.fill(input.code, input.value)) answer(Answer(true, "filled")) else fail(404, "no_field")
    }

    private suspend fun profileNeed(): Need = EditAccess.forProfile(backend.active(), backend.owner())

    private suspend fun backupNeed(): Need = EditAccess.forBackup(backend.active(), backend.owner())

    /** Runs [block] once the phone has shown the access [need] asks for. SPEC: CMP-06 */
    private suspend fun guarded(r: HttpRequest, need: Need, block: suspend () -> HttpReply): HttpReply {
        val profileId = when (need) {
            Need.NONE -> return block()
            Need.REFUSED -> return fail(403, "refused")
            Need.PROFILE_PIN -> backend.active().id
            Need.OWNER_PIN -> backend.owner()?.id ?: return fail(403, "refused")
        }
        val who = if (need == Need.OWNER_PIN) "owner" else "profile"
        val pin = r.headers[PROFILE_PIN_HEADER]?.trim()?.takeIf { p -> p.length == PIN_LENGTH && p.all { it in '0'..'9' } }
            ?: return answer(Answer(false, "profile_pin", who = who), 401)
        return when (backend.checkPin(profileId, pin)) {
            PinAnswer.OK -> block()
            PinAnswer.WRONG -> answer(Answer(false, "profile_pin_wrong", who = who), 401)
            PinAnswer.LOCKED -> answer(Answer(false, "profile_locked", who = who), 423)
        }
    }

    companion object {
        const val PIN_HEADER = "x-shamash-pin"
        const val PROFILE_PIN_HEADER = "x-shamash-profile-pin"
        private const val PIN_LENGTH = 4

        internal fun <T> ok(serializer: KSerializer<T>, value: T) = HttpReply.json(200, PhoneJson.out.encodeToString(serializer, value))

        internal fun answer(a: Answer, status: Int = if (a.ok) 200 else 400) =
            HttpReply.json(status, PhoneJson.out.encodeToString(Answer.serializer(), a))

        internal fun fail(status: Int, code: String) = answer(Answer(false, code), status)

        /** Guest saves nothing (PRF-11): its queue and edits would vanish anyway. */
        internal fun savesNothing(kind: ProfileKind) = kind == ProfileKind.GUEST
    }
}
