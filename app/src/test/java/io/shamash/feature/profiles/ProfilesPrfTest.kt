package io.shamash.feature.profiles

import io.shamash.domain.ContentLevel
import io.shamash.domain.KeyMode
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProfilesPrfTest {
    // ---- PRF-05 PIN hash and lockout ----

    @Test fun PRF_05_pin_is_stored_as_a_salted_hash_never_the_digits() {
        val a = PinHasher.hash("1234", iterations = 1_000)
        val b = PinHasher.hash("1234", iterations = 1_000)
        assertFalse(a.contains("1234"))
        assertNotEquals("same PIN, different salt", a, b)
        assertTrue(PinHasher.verify("1234", a))
        assertFalse(PinHasher.verify("1235", a))
    }

    @Test fun PRF_05_bad_input_and_malformed_hashes_never_match() {
        assertFalse(PinHasher.verify("1234", null))
        assertFalse(PinHasher.verify("1234", "garbage"))
        assertFalse(PinHasher.verify("1234", "pbkdf2$0$00$00"))
        assertFalse(PinHasher.verify("12", PinHasher.hash("1234", 1_000)))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test fun PRF_05_five_wrong_entries_lock_for_five_minutes_then_double() {
        var s = LockoutState()
        val t0 = 1_000_000L
        repeat(4) { s = PinLockout.onFailure(s, t0) }
        assertFalse(PinLockout.isLocked(s, t0))
        assertEquals(1, PinLockout.triesLeft(s, t0))
        s = PinLockout.onFailure(s, t0)
        assertTrue(PinLockout.isLocked(s, t0))
        assertEquals(5 * 60_000L, PinLockout.remainingMs(s, t0))
        assertFalse(PinLockout.isLocked(s, t0 + 5 * 60_000L))
        val t1 = t0 + 5 * 60_000L
        repeat(5) { s = PinLockout.onFailure(s, t1) }
        assertEquals("second lock doubles", 10 * 60_000L, PinLockout.remainingMs(s, t1))
        assertEquals(LockoutState(), PinLockout.onSuccess())
    }

    @Test fun PRF_05_lockout_state_survives_a_restart() {
        val s = LockoutState(3, 99L, 2)
        assertEquals(s, PinLockout.decode(PinLockout.encode(s)))
        assertEquals(LockoutState(), PinLockout.decode("junk"))
    }

    // ---- PRF-04 picker ----

    @Test fun PRF_04_picker_shows_with_two_profiles_or_guest_and_can_be_turned_off() {
        assertTrue(PickerRule.needed(2, guestEnabled = false, askOnLaunch = true))
        assertFalse(PickerRule.needed(1, guestEnabled = false, askOnLaunch = true))
        assertTrue(PickerRule.needed(1, guestEnabled = true, askOnLaunch = true))
        assertFalse(PickerRule.needed(3, guestEnabled = true, askOnLaunch = false))
    }

    // ---- PRF-15 bedtime ----

    private val night = ProfileLimits(bedtime = Drafts.everyDay(21 * 60, 6 * 60 + 30))

    @Test fun PRF_15_bedtime_across_midnight() {
        fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, day, h, m)   // 2026-10-19 is a Monday
        assertFalse(Bedtime.isLocked(night, at(19, 20, 59)))
        assertTrue(Bedtime.isLocked(night, at(19, 21, 0)))
        assertTrue(Bedtime.isLocked(night, at(19, 23, 59)))
        assertTrue("after midnight, the window that began yesterday", Bedtime.isLocked(night, at(20, 0, 1)))
        assertTrue(Bedtime.isLocked(night, at(20, 6, 29)))
        assertFalse(Bedtime.isLocked(night, at(20, 6, 30)))
        assertEquals(at(20, 6, 30), Bedtime.windowEnd(night, at(19, 22, 0)))
        assertEquals(at(20, 6, 30), Bedtime.windowEnd(night, at(20, 1, 0)))
    }

    @Test fun PRF_15_a_window_inside_one_day_and_days_without_one() {
        val limits = ProfileLimits(bedtime = mapOf(1 to Window(13 * 60, 15 * 60)))   // Mondays 13:00-15:00
        assertTrue(Bedtime.isLocked(limits, LocalDateTime.of(2026, 10, 19, 14, 0)))
        assertFalse(Bedtime.isLocked(limits, LocalDateTime.of(2026, 10, 19, 15, 0)))
        assertFalse(Bedtime.isLocked(limits, LocalDateTime.of(2026, 10, 20, 14, 0)))
    }

    // ---- PRF-14 daily limit ----

    @Test fun PRF_14_warns_at_five_minutes_then_locks_and_an_owner_unlock_lifts_it() {
        val limits = ProfileLimits(dailyMinutes = 60)
        val noon = LocalDateTime.of(2026, 10, 19, 12, 0)
        assertEquals(LockState.Open, LockPolicy.evaluate(limits, 54, noon))
        assertEquals(LockState.Warning(5), LockPolicy.evaluate(limits, 55, noon))
        assertEquals(LockState.DailyLimit, LockPolicy.evaluate(limits, 60, noon))
        assertEquals(LockState.Open, LockPolicy.evaluate(limits, 90, noon, overrideUntil = noon.plusHours(5)))
        assertEquals(LockState.DailyLimit, LockPolicy.evaluate(limits, 90, noon, overrideUntil = noon.minusMinutes(1)))
        assertEquals(LockState.Open, LockPolicy.evaluate(ProfileLimits(), 10_000, noon))
    }

    @Test fun PRF_14_an_owner_unlock_lasts_to_the_end_of_the_window_or_the_day() {
        val limits = ProfileLimits(dailyMinutes = 30, bedtime = night.bedtime)
        assertEquals(LocalDateTime.of(2026, 10, 20, 6, 30), LockPolicy.overrideEnd(limits, LocalDateTime.of(2026, 10, 19, 22, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 20, 0, 0), LockPolicy.overrideEnd(limits, LocalDateTime.of(2026, 10, 19, 12, 0)))
    }

    @Test fun PRF_14_limits_round_trip_and_a_corrupt_value_reads_as_defaults() {
        val l = ProfileLimits(45, night.bedtime, setOf("a.b"))
        assertEquals(l, ProfileLimits.decode(ProfileLimits.encode(l)))
        assertEquals(ProfileLimits(), ProfileLimits.decode("{not json"))
    }

    // ---- PRF-10, PRF-11, PRF-13 ----

    @Test fun PRF_10_kids_reach_neither_settings_nor_inputs_nor_the_phone_remote() {
        val kids = Restrictions.of(ProfileKind.KIDS)
        assertFalse(kids.settings); assertFalse(kids.tvSettings); assertFalse(kids.inputs); assertFalse(kids.phoneRemote)
        assertTrue(Restrictions.of(ProfileKind.ADULT).settings)
    }

    @Test fun PRF_11_guest_saves_nothing_and_has_no_settings() {
        val guest = Restrictions.of(ProfileKind.GUEST)
        assertFalse(guest.savesHistory); assertFalse(guest.settings)
        assertTrue(Restrictions.of(ProfileKind.KIDS).savesHistory)
    }

    @Test fun PRF_13_kids_open_only_picked_apps_family_open_all_until_picked() {
        val kids = Profile(kind = ProfileKind.KIDS, contentLevel = ContentLevel.KIDS)
        assertFalse(AppAccess.allows(kids, ProfileLimits(), "com.netflix", "io.shamash"))
        assertTrue(AppAccess.allows(kids, ProfileLimits(allowedApps = setOf("com.netflix")), "com.netflix", "io.shamash"))
        assertTrue("Shamash itself", AppAccess.allows(kids, ProfileLimits(), "io.shamash", "io.shamash"))
        val family = Profile(contentLevel = ContentLevel.FAMILY)
        assertTrue(AppAccess.allows(family, ProfileLimits(), "com.netflix", "io.shamash"))
        assertFalse(AppAccess.allows(family, ProfileLimits(allowedApps = setOf("x")), "com.netflix", "io.shamash"))
        assertTrue(AppAccess.allows(Profile(), ProfileLimits(allowedApps = setOf("x")), "com.netflix", "io.shamash"))
    }

    // ---- PRF-09 keys, PRF-08 add profile ----

    @Test fun PRF_09_shared_keys_come_from_the_owner_own_keys_stay_own() {
        val owner = Profile(id = "o", tmdbKey = "OWN-KEY", youtubeClientId = "cid", youtubeClientSecret = "sec")
        val shared = Profile(id = "k", keyMode = KeyMode.SHARED)
        assertEquals("OWN-KEY", ProfileKeys.effective(shared, owner).tmdbKey)
        val own = Profile(id = "k", keyMode = KeyMode.OWN)
        assertEquals("", ProfileKeys.effective(own, owner).tmdbKey)
        assertEquals("mine", ProfileKeys.effective(shared.copy(tmdbKey = "mine"), owner).tmdbKey)
    }

    @Test fun PRF_09_a_borrowed_key_is_not_copied_into_the_profile_on_save() {
        val owner = Profile(id = "o", tmdbKey = "K")
        val saved = ProfileKeys.stripShared(Profile(id = "k", tmdbKey = "K"), owner)
        assertEquals("", saved.tmdbKey)
        assertEquals("K", ProfileKeys.stripShared(Profile(id = "k", tmdbKey = "K", keyMode = KeyMode.OWN), owner).tmdbKey)
    }

    @Test fun PRF_08_a_new_profile_can_copy_the_setup_and_kids_start_at_the_kids_level() {
        val from = Profile(city = "Baghdad", region = "IQ", teams = setOf("Zawraa"), onboarded = true)
        val copy = Drafts.create("Sara ", ProfileKind.ADULT, 1, from)
        assertEquals("Sara", copy.name)
        assertTrue(copy.onboarded); assertEquals("Baghdad", copy.city); assertEquals(setOf("Zawraa"), copy.teams)
        val fresh = Drafts.create("Omar", ProfileKind.KIDS, 2, null)
        assertFalse("runs setup", fresh.onboarded)
        assertEquals(ContentLevel.KIDS, fresh.contentLevel)
        assertNull(fresh.pinHash)
        assertFalse(Drafts.isValidName("  "))
    }

    @Test fun PRF_15_bedtime_presets_round_trip_through_the_editor_index() {
        Drafts.BEDTIMES.forEachIndexed { i, (s, e) ->
            assertEquals(i, Drafts.bedtimeIndex(ProfileLimits(bedtime = Drafts.everyDay(s, e))))
        }
        assertEquals(-1, Drafts.bedtimeIndex(ProfileLimits()))
        assertEquals("21:00", Drafts.clock(21 * 60))
    }
}
