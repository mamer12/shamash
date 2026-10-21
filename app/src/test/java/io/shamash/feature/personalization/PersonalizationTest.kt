package io.shamash.feature.personalization

import io.shamash.data.signals.PersonalStore
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.MyListEntry
import io.shamash.domain.OpenTarget
import io.shamash.domain.PersonalBridge
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.SignalKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersonalizationTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_700_000_000_000L

    @After fun tearDown() { scope.cancel() }

    private class FakeBridge(initial: List<MyListEntry> = emptyList()) : PersonalBridge {
        override val providerId = "cinemana"
        val list = MutableStateFlow(initial)
        var cleared = false
        override val listEntries: Flow<List<MyListEntry>> = list
        override val finished = MutableSharedFlow<MediaItem>()
        override suspend fun addToList(item: MediaItem, profile: Profile) { list.value = listOf(MyListEntry.of(item, 0)) + list.value }
        override suspend fun removeFromList(itemKey: String) { list.value = list.value.filterNot { it.itemKey == itemKey } }
        override suspend fun clearHistory() { cleared = true }
    }

    private fun item(id: String, provider: String = "tmdb", title: String = id) =
        MediaItem(id, provider, title, Kind.MOVIE, OpenTarget.App("com.netflix.ninja"), genres = listOf("drama"))

    private fun make(kind: ProfileKind = ProfileKind.ADULT, bridge: PersonalBridge? = null, recording: Boolean = true): Personalization {
        val profile = Profile(id = "p1", kind = kind, addons = setOf("cinemana"))
        return Personalization(
            PersonalStore(folder.newFolder()), flowOf(profile), { listOfNotNull(bridge) }, scope,
            recordingOn = { recording }, clock = { clock }, hourOf = { 9 },
        )
    }

    @Test fun PER_01_adult_open_is_recorded_with_hour_and_genres() = runBlocking {
        val p = make()
        p.record(item("a"), SignalKind.OPEN)
        val s = p.state.first { it.data.signals.isNotEmpty() }.data.signals.single()
        assertEquals(9, s.hourOfDay)
        assertEquals(listOf("drama"), s.genres)
        assertEquals("p1", s.profileId)
    }

    @Test fun PER_01_guest_and_kids_record_no_signals() = runBlocking {
        for (kind in listOf(ProfileKind.GUEST, ProfileKind.KIDS)) {
            val p = make(kind)
            p.record(item("a"), SignalKind.OPEN)
            p.mute(item("b"), lessLikeThis = true)
            assertTrue("$kind", p.state.value.data.signals.isEmpty())
        }
    }

    @Test fun PER_02_open_left_within_two_minutes_becomes_a_skip() = runBlocking {
        val p = make()
        p.noteOpened(item("a"))
        clock += 30_000
        p.settleOpen()
        val kinds = p.state.first { it.data.signals.size >= 2 }.data.signals.map { it.kind }
        assertEquals(listOf(SignalKind.OPEN, SignalKind.SKIP), kinds)
    }

    @Test fun PER_02_a_long_watch_is_not_a_skip() = runBlocking {
        val p = make()
        p.noteOpened(item("a"))
        clock += 10 * 60_000
        p.settleOpen()
        p.state.first { it.data.signals.isNotEmpty() }
        assertEquals(listOf(SignalKind.OPEN), p.state.value.data.signals.map { it.kind })
    }

    @Test fun PER_07_mute_hides_everywhere_and_can_be_undone() = runBlocking {
        val p = make()
        p.mute(item("a", title = "Hidden"), lessLikeThis = true)
        val data = p.state.first { it.data.mutes.isNotEmpty() }.data
        assertTrue(data.muteIndex.isMuted(item("a")))
        assertEquals(SignalKind.LESS_LIKE_THIS, data.signals.single().kind)
        p.unmute(data.mutes.single())
        assertTrue(p.state.first { it.data.mutes.isEmpty() }.data.mutes.isEmpty())
    }

    @Test fun PER_13_cinemana_list_is_migrated_and_stays_in_the_add_on() = runBlocking {
        val bridge = FakeBridge(listOf(MyListEntry.of(item("cinemana:1", "cinemana", "One"), 0)))
        val p = make(bridge = bridge)
        p.start()
        val data = p.state.first { it.data.myList.isNotEmpty() }.data
        assertEquals(listOf("cinemana:1"), data.myList.map { it.itemKey })
        assertEquals(1, bridge.list.value.size)   // nothing lost on the add-on's side
    }

    @Test fun PER_13_adding_a_cinemana_title_in_the_core_reaches_the_add_on() = runBlocking {
        val bridge = FakeBridge()
        val p = make(bridge = bridge)
        p.start()
        p.toggleMyList(item("cinemana:9", "cinemana", "Nine"))
        assertEquals(listOf("cinemana:9"), bridge.list.value.map { it.itemKey })
        p.toggleMyList(item("cinemana:9", "cinemana", "Nine"))
        assertTrue(bridge.list.value.isEmpty())
        assertTrue(p.state.first { it.data.myList.isEmpty() }.data.myList.isEmpty())
    }

    @Test fun PER_13_removing_in_the_add_on_removes_in_the_core() = runBlocking {
        val bridge = FakeBridge(listOf(MyListEntry.of(item("cinemana:1", "cinemana"), 0)))
        val p = make(bridge = bridge)
        p.start()
        p.state.first { it.data.myList.isNotEmpty() }
        bridge.list.value = emptyList()
        assertTrue(p.state.first { it.data.myList.isEmpty() }.data.myList.isEmpty())
    }

    @Test fun PER_04_clear_deletes_signals_mutes_and_the_add_ons_history_but_keeps_my_list() = runBlocking {
        val bridge = FakeBridge()
        val p = make(bridge = bridge)
        p.record(item("a"), SignalKind.OPEN)
        p.mute(item("b"), lessLikeThis = false)
        p.toggleMyList(item("c"))
        p.state.first { it.data.signals.size >= 2 && it.data.myList.isNotEmpty() }
        p.clear()
        val data = p.state.first { it.data.signals.isEmpty() }.data
        assertTrue(data.mutes.isEmpty())
        assertEquals(1, data.myList.size)
        assertTrue(bridge.cleared)
    }

    @Test fun PER_05_learn_my_order_can_be_switched_off() = runBlocking {
        val p = make()
        p.setLearnOrder(false)
        assertFalse(p.state.first { !it.data.learnOrder }.data.learnOrder)
    }
}
