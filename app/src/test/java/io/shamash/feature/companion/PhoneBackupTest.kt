package io.shamash.feature.companion

import io.shamash.data.backup.BackupAttempts
import io.shamash.feature.companion.Kit.code
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// SPEC: CMP-13, CMP-14, CMP-15
class PhoneBackupTest {
    private val pass = "correct horse battery"

    private fun exportBody(p: String = pass, keys: Boolean = false) =
        buildJsonObject { put("passphrase", p); put("includeKeys", keys); put("includePins", false) }.toString()

    private fun importBody(file: String, p: String = pass) =
        buildJsonObject { put("passphrase", p); put("file", file) }.toString()

    private fun api(backend: FakeBackend, attempts: BackupAttempts = BackupAttempts()) =
        PhoneApi(Kit.auth(), backend, PhoneFields(), PhoneBackupRoutes(backend, attempts))

    @Test fun `CMP_13 nothing is exported until the TV says yes`() {
        val backend = FakeBackend()
        val api = api(backend)
        backend.confirm = Confirm.NO
        assertEquals("declined", api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).code())
        backend.confirm = Confirm.BUSY
        assertEquals(409, api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).status)
        assertEquals(0, backend.exports)
        backend.confirm = Confirm.YES
        val reply = api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody()))
        assertEquals(200, reply.status)
        assertEquals("attachment; filename=\"shamash-backup-2026-10-21.shamash\"", reply.headers["Content-Disposition"])
        val envelope = Json.parseToJsonElement(reply.body) as JsonObject
        assertEquals(JsonPrimitive(1), envelope["v"])
        assertFalse(reply.body.contains(pass))
        assertFalse(reply.body.contains("Lina"))
    }

    @Test fun `CMP_14 a short passphrase is refused before the TV is even asked`() {
        val backend = FakeBackend()
        backend.confirm = Confirm.NO
        val reply = api(backend).handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody("short")))
        assertEquals("backup_weak_passphrase", reply.code())
        assertEquals(0, backend.exports)
    }

    @Test fun `CMP_14 export then import through the phone round-trips, and tampering is detected`() {
        val backend = FakeBackend()
        val api = api(backend)
        val file = api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).body
        val reply = api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file)))
        assertEquals("imported", reply.code())
        assertTrue(reply.body.contains("\"added\":2"))
        assertEquals(backend.payload.profiles, backend.importsShown.single().profiles)
        assertFalse(reply.body.contains(pass))

        val sealed = (Json.parseToJsonElement(file) as JsonObject)["ct"]!!.toString().trim('"')
        val flipped = sealed.replaceRange(10, 11, if (sealed[10] == 'A') "B" else "A")
        val tampered = file.replace(sealed, flipped)
        assertEquals("backup_wrong_passphrase_or_damaged", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(tampered))).code())
        assertEquals(1, backend.importsShown.size)
    }

    @Test fun `CMP_15 a failed open never reaches the TV, three wrong passphrases mean a wait`() {
        var now = 0L
        val backend = FakeBackend()
        val api = api(backend, BackupAttempts(clock = { now }))
        val file = api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).body
        repeat(3) {
            assertEquals("backup_wrong_passphrase_or_damaged", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file, "wrong passphrase"))).code())
        }
        val waiting = api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file)))
        assertEquals(429, waiting.status)
        assertTrue(waiting.body.contains("\"seconds\":60"))
        assertTrue(backend.importsShown.isEmpty())
        now += 60_000
        assertEquals("imported", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file))).code())
    }

    @Test fun `CMP_15 refused files change nothing and newer versions say so`() {
        val backend = FakeBackend()
        val api = api(backend)
        val file = api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).body
        val newer = file.replaceFirst("\"v\":1", "\"v\":9")
        assertEquals("backup_newer_version", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(newer))).code())
        assertEquals("backup_not_a_backup", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody("hello"))).code())
        assertEquals("invalid", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, """{"file":"x"}""")).code())
        assertTrue(backend.importsShown.isEmpty())
        backend.importChoice = ImportOutcome.Declined
        assertEquals("declined", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file))).code())
        backend.importChoice = ImportOutcome.Failed
        assertEquals("import_failed", api.handle(Kit.call("POST", ApiPaths.BACKUP_IMPORT, importBody(file))).code())
    }

    @Test fun `CMP_13 backup needs the owner's PIN when the owner has one`() {
        val backend = FakeBackend(active = io.shamash.domain.Profile(id = "p-owner", pinHash = "h"))
        backend.pins["p-owner"] = "1234"
        val api = api(backend)
        assertEquals("profile_pin", api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody())).code())
        assertEquals(0, backend.exports)
        assertEquals(200, api.handle(Kit.call("POST", ApiPaths.BACKUP_EXPORT, exportBody(), profilePin = "1234")).status)
    }

    @Test fun `CMP_15 the TV prompt allows one question at a time and times out as no`() = runBlocking {
        val first = PhonePrompt.Export()
        val waiting = async { PhonePrompts.ask(first, first.answer, waitMs = 5_000) }
        yield()
        val second = PhonePrompt.Export()
        assertEquals(PhonePrompts.Asked.Busy, PhonePrompts.ask(second, second.answer, waitMs = 10))
        first.answer.complete(true)
        assertEquals(PhonePrompts.Asked.Answered(true), waiting.await())
        val late = PhonePrompt.Export()
        assertEquals(PhonePrompts.Asked.Answered<Boolean>(null), PhonePrompts.ask(late, late.answer, waitMs = 10))
        assertEquals(null, PhonePrompts.current.value)
    }
}
