package io.shamash.data.backup

import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// SPEC: CMP-15, PLT-03
class BackupPlanTest {
    private val payload = BackupFixtures.payload(emptyMap())
    private val here = listOf(Profile(id = "mine", name = "Owner"), Profile(id = "x", name = "Lina", kind = ProfileKind.KIDS))

    @Test fun CMP_15_preview_counts_profiles_and_marks_existing_ones() {
        val pv = BackupPlanner.preview(payload, here + Profile(id = "p-kid", name = "Other"))
        assertEquals(listOf(false, true), pv.profiles.map { it.exists })
        assertEquals(2, pv.profiles[0].rows)
        assertTrue(pv.hasSecrets)
    }

    @Test fun CMP_15_merge_adds_only_profiles_that_do_not_exist() {
        val plan = BackupPlanner.plan(payload, here, "mine", ImportMode.MERGE)
        assertEquals(listOf("p-owner"), plan.add.map { it.id })  // Lina exists by name and kind
        assertTrue(plan.replace.isEmpty() && plan.remove.isEmpty())
    }

    @Test fun CMP_15_merge_respects_the_profile_limit() {
        val full = List(6) { Profile(id = "e$it", name = "E$it") }
        assertTrue(BackupPlanner.plan(payload, full, "e0", ImportMode.MERGE).add.isEmpty())
    }

    @Test fun CMP_15_replace_keeps_the_owner_id_and_swaps_the_rest() {
        val plan = BackupPlanner.plan(payload, here, "mine", ImportMode.REPLACE)
        assertEquals("mine", plan.replace.single().id)
        assertEquals("Mustafa", plan.replace.single().name)
        assertEquals(ProfileKind.ADULT, plan.replace.single().kind)
        assertEquals(listOf("p-kid"), plan.add.map { it.id })
        assertEquals(listOf("x"), plan.remove)
    }

    @Test fun PLT_03_local_backup_keeps_the_last_three_without_secrets() {
        val dir = File.createTempFile("bkp", "").also { it.delete(); it.mkdirs() }
        try {
            val lb = LocalBackups(dir)
            val full = BackupFixtures.payload()
            (1L..5L).forEach { assertTrue(lb.save(full, it)) }
            assertEquals(3, lb.list().size)
            assertTrue(lb.list().first().name.endsWith("000000000000005.json"))
            val text = lb.list().joinToString { it.readText() }
            listOf("TMDB-SECRET-KEY", "PLEX-TOKEN", "pbkdf2", "token=abc").forEach { assertTrue(it, it !in text) }
            assertTrue("Mustafa" in text)
        } finally { dir.deleteRecursively() }
    }

    @Test fun PLT_03_local_backup_fails_soft() {
        val file = File.createTempFile("bkp", "")
        try { assertEquals(false, LocalBackups(File(file, "sub")).save(BackupFixtures.payload(), 1)) } finally { file.delete() }
    }

    @Test fun CMP_15_three_wrong_tries_then_a_minute_wait() {
        var now = 0L
        val a = BackupAttempts({ now })
        repeat(2) { a.failed() }
        assertEquals(0, a.waitLeftMs())
        a.failed()
        assertEquals(60_000, a.waitLeftMs())
        now = 59_999; assertEquals(1, a.waitLeftMs())
        now = 60_000; assertEquals(0, a.waitLeftMs())
        a.failed(); a.succeeded(); repeat(2) { a.failed() }
        assertEquals(0, a.waitLeftMs())
    }
}
