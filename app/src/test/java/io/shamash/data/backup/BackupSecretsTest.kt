package io.shamash.data.backup

import io.shamash.data.backup.BackupFixtures.opened
import io.shamash.data.backup.BackupFixtures.sealed
import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// SPEC: CMP-12, CMP-14
class BackupSecretsTest {
    private fun back(options: BackupOptions, payload: BackupPayload = BackupFixtures.payload()) =
        (opened(sealed(options, payload)) as BackupResult.Ok).value

    @Test fun CMP_14_default_export_has_no_secrets_pins_or_keys() {
        val b = back(BackupOptions())
        assertTrue(b.secrets.isEmpty())
        val o = b.profiles.first { it.id == "p-owner" }
        assertNull(o.pinHash); assertNull(o.tmdbKey); assertNull(o.youtubeClientId); assertNull(o.youtubeClientSecret)
        assertEquals("Mustafa", o.name)
        assertEquals(BackupFixtures.owner.layout, o.layout)
    }

    @Test fun CMP_14_default_export_drops_feed_addresses_with_logins_or_tokens() {
        val feeds = back(BackupOptions()).profiles.first { it.id == "p-owner" }.feeds
        assertEquals(listOf("https://example.org/feed.xml"), feeds.map { it.url })
        assertEquals(2, back(BackupOptions(includeSecrets = true)).profiles.first { it.id == "p-owner" }.feeds.size)
    }

    @Test fun CMP_14_secrets_come_only_with_the_explicit_choice_and_pins_with_theirs() {
        val s = back(BackupOptions(includeSecrets = true))
        assertEquals("TMDB-SECRET-KEY", s.profiles.first { it.id == "p-owner" }.tmdbKey)
        assertEquals(2, s.secrets.size)
        assertNull(s.profiles.first { it.id == "p-owner" }.pinHash)
        val p = back(BackupOptions(includePins = true))
        assertEquals("pbkdf2\$hash", p.profiles.first { it.id == "p-owner" }.pinHash)
        assertTrue(p.secrets.isEmpty()); assertNull(p.profiles.first { it.id == "p-owner" }.tmdbKey)
    }

    @Test fun CMP_12_adb_keys_companion_pin_and_pin_hashes_never_travel_even_when_secrets_are_on() {
        val bad = mapOf("adb_private_key" to "K", "companion_pin" to "1234", "phone_pin" to "1", "pin" to "1",
            "p-owner_pin_hash" to "h", "ADB_KEY" to "k", "p-owner_plex_token" to "ok")
        val b = back(BackupOptions(includeSecrets = true), BackupFixtures.payload(bad))
        assertEquals(setOf("p-owner_plex_token"), b.secrets.keys)
    }

    @Test fun CMP_14_the_file_never_contains_secret_text_in_the_clear() {
        val text = sealed(BackupOptions(includeSecrets = true, includePins = true)).toString(Charsets.UTF_8)
        listOf("TMDB-SECRET-KEY", "PLEX-TOKEN", "YT-SECRET", "REFRESH").forEach { assertFalse(it in text) }
    }

    @Test fun CMP_12_profile_mapping_roundtrips_and_secret_fields_come_back_empty_when_absent() {
        val p = Profile(id = "a", name = "N", tmdbKey = "k", pinHash = "h", teams = setOf("t"))
        val snap = BackupSnapshot.of(p)
        assertEquals(p, BackupSnapshot.toProfile(snap))
        val stripped = BackupSnapshot.restrict(BackupPayload(profiles = listOf(snap), ownerId = "a"), BackupOptions()).profiles.single()
        val restored = BackupSnapshot.toProfile(stripped)
        assertEquals("", restored.tmdbKey); assertNull(restored.pinHash); assertEquals(setOf("t"), restored.teams)
    }

    @Test fun CMP_14_toString_free_of_passphrase_in_failures() {
        val r = opened(sealed(), "wrong-passphrase-123")
        assertFalse("wrong-passphrase-123" in r.toString())
    }
}
