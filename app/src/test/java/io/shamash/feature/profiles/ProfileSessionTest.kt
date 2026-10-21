package io.shamash.feature.profiles

import io.shamash.data.DataStores
import io.shamash.data.Preferences
import io.shamash.data.Recording
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicLong

class ProfileSessionTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val now = AtomicLong(1_000_000_000_000L)
    private lateinit var prefs: Preferences
    private lateinit var security: SecurityStore

    @Before fun setUp() {
        val stores = DataStores(folder.newFolder("ds"), scope)
        prefs = Preferences(stores)
        security = SecurityStore(stores)
    }

    @After fun tearDown() { scope.cancel(); Recording.enabled = true }

    private fun session() = ProfileSession(prefs, security, scope, "io.shamash", { now.get() }) .also { s ->
        runBlocking { withTimeout(10_000) { while (s.gate.value == Gate.Booting) delay(20) } }
    }

    /** The migrated owner (named Dad) plus a KIDS profile; pins are cheap hashes. */
    private fun twoProfiles(kidsPin: String? = null, ownerPin: String? = null): Pair<Profile, Profile> = runBlocking {
        val owner = prefs.profiles.current().copy(name = "Dad", onboarded = true, pinHash = ownerPin?.let { PinHasher.hash(it, 1_000) })
        prefs.profiles.save(owner)
        val kid = prefs.profiles.add(
            Profile(kind = ProfileKind.KIDS, name = "Sara", contentLevel = ContentLevel.KIDS, onboarded = true,
                pinHash = kidsPin?.let { PinHasher.hash(it, 1_000) }),
        )!!
        owner to kid
    }

    @Test fun PRF_04_one_profile_skips_the_picker_two_show_it() {
        assertEquals(Gate.Open, session().gate.value)
        twoProfiles()
        // A new session (a new launch) now has a choice to make.
        val s2 = ProfileSession(prefs, security, scope, "io.shamash", { now.get() })
        runBlocking { withTimeout(10_000) { while (s2.gate.value == Gate.Booting) delay(20) } }
        assertEquals(Gate.Picker, s2.gate.value)
    }

    @Test fun PRF_05_a_wrong_pin_counts_down_then_locks_and_the_lock_is_kept() {
        val (_, kid) = twoProfiles(kidsPin = "4321")
        val s = session()
        runBlocking {
            assertEquals(PinCheck.Wrong(4), s.checkPin(kid.id, "0000"))
            repeat(3) { s.checkPin(kid.id, "0000") }
            val locked = s.checkPin(kid.id, "0000")
            assertTrue(locked is PinCheck.Locked)
            assertTrue("even the right PIN waits", s.checkPin(kid.id, "4321") is PinCheck.Locked)
            now.addAndGet(PinLockout.BASE_MS + 1)
            assertEquals(PinCheck.Ok, s.checkPin(kid.id, "4321"))
        }
    }

    @Test fun PRF_10_leaving_kids_needs_the_owner_pin_or_a_held_ok() {
        val (owner, kid) = twoProfiles()
        val s = session()
        runBlocking {
            s.switchTo(kid.id)
            withTimeout(5_000) { while (s.active.value?.id != kid.id) delay(20) }
            assertEquals(listOf(Requirement.HOLD_OK), s.requirements(owner))
            assertEquals(emptyList<Requirement>(), s.requirements(kid))
            prefs.profiles.save(owner.copy(pinHash = PinHasher.hash("1111", 1_000)))
            withTimeout(5_000) { while (s.profiles.value.first().pinHash == null) delay(20) }
            assertEquals(listOf(Requirement.OWNER_PIN), s.requirements(s.profiles.value.first()))
        }
    }

    @Test fun PRF_11_guest_saves_nothing_and_is_gone_after_leaving() {
        val (owner, _) = twoProfiles()
        val s = session()
        runBlocking {
            s.startGuest()
            withTimeout(5_000) { while (s.active.value?.kind != ProfileKind.GUEST) delay(20) }
            assertEquals(ContentLevel.FAMILY, s.active.value?.contentLevel)
            assertFalse(s.recordsHistory())
            withTimeout(5_000) { while (Recording.enabled) delay(20) }
            s.switchTo(owner.id)
            withTimeout(5_000) { while (s.active.value?.kind == ProfileKind.GUEST) delay(20) }
            assertTrue(s.recordsHistory())
            withTimeout(5_000) { while (!Recording.enabled) delay(20) }
            assertTrue(prefs.profiles.profiles.first().none { it.kind == ProfileKind.GUEST })
        }
    }

    @Test fun PRF_11_guest_uses_the_level_the_owner_chose() {
        twoProfiles()
        val s = session()
        runBlocking {
            security.setGuestLevel(ContentLevel.KIDS)
            s.startGuest()
            withTimeout(5_000) { while (s.active.value?.kind != ProfileKind.GUEST) delay(20) }
            assertEquals(ContentLevel.KIDS, s.active.value?.contentLevel)
        }
    }

    @Test fun PRF_13_a_blocked_app_is_refused_for_kids() {
        val (_, kid) = twoProfiles()
        val s = session()
        runBlocking {
            s.switchTo(kid.id)
            withTimeout(5_000) { while (s.active.value?.id != kid.id) delay(20) }
            withTimeout(5_000) { while (s.limits.value.allowedApps?.contains("com.ok") != true) { security.setLimits(kid.id, ProfileLimits(allowedApps = setOf("com.ok"))); delay(50) } }
            assertTrue(s.allowsApp("com.ok"))
            assertFalse(s.allowsApp("com.netflix.ninja"))
        }
    }

    @Test fun PRF_14_the_daily_budget_counts_minutes_and_locks_then_the_owner_unlocks() {
        val (owner, kid) = twoProfiles(ownerPin = "9999")
        val s = session()
        runBlocking {
            security.setLimits(kid.id, ProfileLimits(dailyMinutes = 2))
            s.switchTo(kid.id)
            withTimeout(5_000) { while (s.active.value?.id != kid.id) delay(20) }
            s.tick(); s.tick()
            assertTrue(s.gate.value is Gate.Locked)
            assertTrue(s.unlockWithOwnerPin("0000") is PinCheck.Wrong)
            assertTrue(s.gate.value is Gate.Locked)
            assertEquals(PinCheck.Ok, s.unlockWithOwnerPin("9999"))
            assertEquals(Gate.Open, s.gate.value)
            assertNull(s.warning.value)
            assertEquals(owner.id, s.ownerProfile()?.id)
        }
    }

    @Test fun PRF_15_a_clock_set_back_does_not_end_a_bedtime_lock() {
        val (_, kid) = twoProfiles()
        val zone = java.time.ZoneId.of("UTC")
        val night = java.time.LocalDateTime.of(2026, 10, 19, 22, 0).atZone(zone).toInstant().toEpochMilli()
        now.set(night)
        val s = ProfileSession(prefs, security, scope, "io.shamash", { now.get() }, { zone })
        runBlocking {
            withTimeout(10_000) { while (s.gate.value == Gate.Booting) delay(20) }
            security.setLimits(kid.id, ProfileLimits(bedtime = Drafts.everyDay(21 * 60, 6 * 60)))
            s.switchTo(kid.id)
            withTimeout(5_000) { while (s.active.value?.id != kid.id) delay(20) }
            s.tick()
            assertTrue(s.gate.value is Gate.Locked)
            now.set(night - 8 * 3_600_000L)   // the child sets the TV clock back to the afternoon
            s.tick()
            assertTrue(s.gate.value is Gate.Locked)
        }
    }
}
