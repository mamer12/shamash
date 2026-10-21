package io.shamash.data.backup

import io.shamash.data.backup.BackupFixtures.PASS
import io.shamash.data.backup.BackupFixtures.opened
import io.shamash.data.backup.BackupFixtures.sealed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

// SPEC: PLT-02, CMP-12, CMP-13, CMP-14, CMP-15
class BackupCodecTest {
    private fun ok(r: BackupResult<BackupPayload>) = (r as BackupResult.Ok).value
    private fun err(r: BackupResult<*>) = (r as BackupResult.Failed).error
    private fun obj(bytes: ByteArray) = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject
    private fun with(bytes: ByteArray, key: String, value: String) =
        JsonObject(obj(bytes) + (key to JsonPrimitive(value))).toString().toByteArray()

    @Test fun PLT_02_roundtrip_keeps_profiles_layout_follows_and_feeds() {
        val back = ok(opened(sealed(BackupOptions(includeSecrets = true, includePins = true))))
        val full = BackupFixtures.payload()
        assertEquals(1_700_000_000_000, back.createdAt)
        assertEquals(full.profiles, back.profiles)
        assertEquals(full.secrets, back.secrets)
    }

    @Test fun CMP_13_envelope_is_versioned_json_and_hides_content() {
        val bytes = sealed()
        val text = bytes.toString(Charsets.UTF_8)
        assertEquals(1, (obj(bytes)["v"] as JsonPrimitive).content.toInt())
        listOf("Mustafa", "Real Madrid", "Baghdad", "example.org", "p-owner").forEach { assertFalse(it, it in text) }
    }

    @Test fun CMP_14_each_export_uses_fresh_salt_and_nonce() {
        val a = obj(sealed()); val b = obj(sealed())
        assertNotEquals(a["salt"], b["salt"])
        assertNotEquals(a["nonce"], b["nonce"])
        assertNotEquals(a["ct"], b["ct"])
    }

    @Test fun CMP_14_iterations_are_at_least_210k_and_cannot_be_lowered() {
        assertEquals(210_000, (obj(sealed())["iter"] as JsonPrimitive).content.toInt())
        runCatching { BackupCodec(iterations = 1000) }.also { assertTrue(it.isFailure) }
        assertEquals(BackupError.NOT_A_BACKUP, err(opened(with(sealed(), "iter", "1000").let { patchInt(it, "iter", 1000) })))
    }

    private fun patchInt(bytes: ByteArray, key: String, v: Int) =
        JsonObject(obj(bytes) + (key to JsonPrimitive(v))).toString().toByteArray()

    @Test fun CMP_14_changing_the_iteration_count_to_another_valid_value_fails_authentication() {
        assertEquals(BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(patchInt(sealed(), "iter", 210_001))))
    }

    @Test fun CMP_14_wrong_passphrase_is_refused() {
        assertEquals(BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(sealed(), "wrong passphrase")))
        assertEquals(BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(sealed(), PASS + " ")))
    }

    @Test fun CMP_14_passphrase_is_unicode_normalised() {
        val composed = "passéword-1"; val decomposed = "passéword-1"
        val bytes = (BackupFixtures.codec().export(BackupFixtures.payload(), composed) as BackupResult.Ok).value
        ok(BackupFixtures.codec().open(bytes, decomposed))
    }

    @Test fun CMP_14_weak_or_huge_passphrase_is_refused_and_nothing_is_written() {
        assertEquals(BackupError.WEAK_PASSPHRASE, err(BackupFixtures.codec().export(BackupFixtures.payload(), "short")))
        assertEquals(BackupError.WEAK_PASSPHRASE, err(BackupFixtures.codec().export(BackupFixtures.payload(), "x".repeat(300))))
    }

    @Test fun CMP_14_every_changed_field_is_detected() {
        val bytes = sealed()
        val b64 = Base64.getEncoder(); val un = Base64.getDecoder()
        fun flip(field: String): ByteArray {
            val raw = un.decode((obj(bytes)[field] as JsonPrimitive).content)
            raw[raw.size / 2] = (raw[raw.size / 2].toInt() xor 1).toByte()
            return with(bytes, field, b64.encodeToString(raw))
        }
        listOf("ct", "nonce", "salt").forEach {
            assertEquals(it, BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(flip(it))))
        }
    }

    @Test fun CMP_14_flipping_any_single_ciphertext_byte_is_detected() {
        val bytes = sealed()
        val raw = Base64.getDecoder().decode((obj(bytes)["ct"] as JsonPrimitive).content)
        for (i in raw.indices step 37) {
            val bad = raw.clone().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
            val patched = with(bytes, "ct", Base64.getEncoder().encodeToString(bad))
            assertEquals("byte $i", BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(patched)))
        }
    }

    @Test fun CMP_15_tampered_file_bytes_never_open() {
        val bytes = sealed()
        for (i in bytes.indices step 29) {
            val bad = bytes.clone().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertTrue("byte $i", opened(bad) is BackupResult.Failed)
        }
    }

    @Test fun CMP_15_truncated_files_are_refused() {
        val bytes = sealed()
        listOf(0, 1, 10, bytes.size / 2, bytes.size - 1).forEach {
            assertTrue("cut $it", opened(bytes.copyOf(it)) is BackupResult.Failed)
        }
        val short = with(bytes, "ct", Base64.getEncoder().encodeToString(ByteArray(5)))
        assertEquals(BackupError.WRONG_PASSPHRASE_OR_DAMAGED, err(opened(short)))
    }

    @Test fun CMP_15_garbage_and_wrong_shapes_are_not_backups() {
        listOf("", "not json", "[]", "{}", """{"v":1}""", """{"v":1,"kdf":"pbkdf2-sha256","iter":210000,"salt":"!!","nonce":"!!","ct":"!!"}""")
            .forEach { assertEquals(it, BackupError.NOT_A_BACKUP, err(opened(it.toByteArray()))) }
        assertEquals(BackupError.NOT_A_BACKUP, err(opened(with(sealed(), "kdf", "scrypt"))))
        assertEquals(BackupError.NOT_A_BACKUP, err(opened(patchInt(sealed(), "v", 0))))
    }

    @Test fun CMP_15_oversize_file_is_refused_before_any_work() {
        val big = ByteArray(BackupLimits.MAX_FILE_BYTES + 1) { 'a'.code.toByte() }
        assertEquals(BackupError.TOO_LARGE, err(opened(big)))
    }

    @Test fun CMP_15_oversize_export_is_refused() {
        val fat = BackupFixtures.owner.copy(extras = (1..20).associate { "k$it" to "x".repeat(BackupLimits.MAX_EXTRA_VALUE) })
        val payload = BackupFixtures.payload().copy(profiles = List(6) { fat.copy(id = "p$it") }, ownerId = "p0")
        assertEquals(BackupError.TOO_LARGE, err(BackupFixtures.codec().export(payload, PASS)))
    }

    @Test fun CMP_15_newer_version_is_refused_with_its_own_error() {
        assertEquals(BackupError.NEWER_VERSION, err(opened(patchInt(sealed(), "v", 2))))
    }

    @Test fun CMP_15_older_version_is_migrated_to_the_current_shape() {
        val v1 = sealed()
        val step: (JsonObject) -> JsonObject = { JsonObject(it + ("createdAt" to JsonPrimitive(42))) }
        val v2 = BackupCodec(currentVersion = 2, migrations = BackupMigrations(mapOf(1 to step)))
        assertEquals(42, ok(v2.open(v1, PASS)).createdAt)
        val sealed2 = (v2.export(BackupFixtures.payload(), PASS) as BackupResult.Ok).value
        assertEquals(2, (obj(sealed2)["v"] as JsonPrimitive).content.toInt())
        ok(v2.open(sealed2, PASS))
    }

    @Test fun CMP_15_missing_or_failing_migration_step_is_refused_not_guessed() {
        val v1 = sealed()
        val gap = BackupCodec(currentVersion = 2)
        assertEquals(BackupError.INVALID, err(gap.open(v1, PASS)))
        val boom = BackupCodec(currentVersion = 2, migrations = BackupMigrations(mapOf(1 to { _: JsonObject -> error("x") })))
        assertEquals(BackupError.INVALID, err(boom.open(v1, PASS)))
    }

    @Test fun CMP_15_authentic_but_invalid_payload_is_refused_by_schema() {
        val gap = BackupCodec(currentVersion = 2, migrations = BackupMigrations(mapOf(1 to { _: JsonObject -> JsonObject(mapOf("profiles" to JsonPrimitive("x"))) })))
        assertEquals(BackupError.INVALID, err(gap.open(sealed(), PASS)))
        val noOwner = BackupFixtures.payload().copy(ownerId = "ghost")
        assertEquals(BackupError.INVALID, err(BackupFixtures.codec().export(noOwner, PASS)))
        val twice = BackupFixtures.payload().copy(profiles = listOf(BackupFixtures.owner, BackupFixtures.owner))
        assertEquals(BackupError.INVALID, err(BackupFixtures.codec().export(twice, PASS)))
        val seven = BackupFixtures.payload().copy(profiles = List(7) { BackupFixtures.kid.copy(id = "k$it") }, ownerId = "k0")
        assertEquals(BackupError.INVALID, err(BackupFixtures.codec().export(seven, PASS)))
    }

    @Test fun CMP_15_unknown_fields_in_the_payload_are_ignored() {
        val extra: (JsonObject) -> JsonObject = { JsonObject(it + ("future" to JsonPrimitive("x"))) }
        val v2 = BackupCodec(currentVersion = 2, migrations = BackupMigrations(mapOf(1 to extra)))
        assertEquals(2, ok(v2.open(sealed(), PASS)).profiles.size)
    }
}
