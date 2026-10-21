package io.shamash.data.signals

import io.shamash.domain.Kind
import io.shamash.domain.MyListEntry
import io.shamash.domain.OpenTarget
import io.shamash.domain.PersonalData
import io.shamash.domain.Signal
import io.shamash.domain.SignalKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersonalStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun signal(i: Int) = Signal("p", "tmdb:$i", "tmdb", SignalKind.OPEN, i.toLong(), 9, listOf("drama"), "Title $i")

    @Test fun PER_17_round_trips_including_my_list_targets() = runBlocking {
        val dir = folder.newFolder("personal")
        val entry = MyListEntry("tmdb:1", "tmdb", "T", Kind.MOVIE, "http://x/p.jpg", OpenTarget.Lookup(5, Kind.MOVIE, "T", "com.netflix.ninja"), 9)
        PersonalStore(dir).update("p1") { it.copy(signals = listOf(signal(1)), myList = listOf(entry), learnOrder = false) }
        val back = PersonalStore(dir).read("p1")
        assertEquals(listOf(signal(1)), back.signals)
        assertEquals(entry, back.myList.single())
        assertFalse(back.learnOrder)
    }

    @Test fun PER_17_a_corrupt_file_starts_empty() = runBlocking {
        val dir = folder.newFolder("personal")
        java.io.File(dir, "p1.json").writeText("{ not json")
        assertEquals(PersonalData(), PersonalStore(dir).read("p1"))
        // and the store keeps working
        PersonalStore(dir).update("p1") { it.copy(signals = listOf(signal(2))) }
        assertEquals(1, PersonalStore(dir).read("p1").signals.size)
    }

    @Test fun PER_17_writes_leave_no_temp_file_and_the_file_stays_under_300_kb() = runBlocking {
        val dir = folder.newFolder("personal")
        val store = PersonalStore(dir)
        store.update("p1") { it.copy(signals = List(2000) { i -> signal(i).copy(title = "x".repeat(400)) }) }
        assertTrue(java.io.File(dir, "p1.json").length() <= PersonalStore.MAX_BYTES)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        assertTrue(store.read("p1").signals.isNotEmpty())
    }

    @Test fun PER_17_profiles_do_not_share_data() = runBlocking {
        val store = PersonalStore(folder.newFolder("personal"))
        store.update("a") { it.copy(signals = listOf(signal(1))) }
        assertTrue(store.read("b").signals.isEmpty())
    }

    @Test fun PER_01_guest_writes_nothing() = runBlocking {
        val dir = folder.newFolder("personal")
        val store = PersonalStore(dir) { false }
        store.update("g") { it.copy(signals = listOf(signal(1))) }
        assertTrue(dir.listFiles()!!.isEmpty())
        assertTrue(store.read("g").signals.isEmpty())
    }

    @Test fun PER_17_ids_cannot_escape_the_folder() = runBlocking {
        val dir = folder.newFolder("personal")
        PersonalStore(dir).update("../evil") { it.copy(learnOrder = false) }
        assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
        assertTrue(folder.root.listFiles()!!.none { it.name.contains("evil") })
    }
}
