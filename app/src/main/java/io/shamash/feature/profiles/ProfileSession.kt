package io.shamash.feature.profiles

import io.shamash.data.Logger
import io.shamash.data.Preferences
import io.shamash.data.Recording
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What stands between the person and the app right now. */
sealed interface Gate {
    data object Booting : Gate
    data object Picker : Gate
    data object Open : Gate
    data class Locked(val why: LockState) : Gate
}

/** What entering a profile needs first (PRF-05, PRF-10). */
enum class Requirement { NONE, TARGET_PIN, OWNER_PIN, HOLD_OK }

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val triesLeft: Int) : PinCheck
    data class Locked(val remainingMs: Long) : PinCheck
}

/** The few answers the Opener needs without suspending. */
interface OpenPolicy {
    fun allowsApp(packageName: String): Boolean
    fun recordsHistory(): Boolean
}

/*
 * Who is using the TV right now and what they may do: the launch picker, PIN checks with lockout, Kids and Guest
 * restrictions, Guest's short life, the daily time budget and bedtime lock. Time counted is Shamash's own foreground
 * time (Home and its screens): Shamash cannot see other apps without an overlay service, so Settings says so
 * ([USAGE_MEASURED] false). The clock never runs backwards: the latest time seen is kept. SPEC: PRF-04..16
 */
class ProfileSession(
    private val prefs: Preferences,
    val security: SecurityStore,
    private val scope: CoroutineScope,
    private val ownPackage: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : OpenPolicy {
    private val store get() = prefs.profiles
    private val _gate = MutableStateFlow<Gate>(Gate.Booting)
    val gate: StateFlow<Gate> = _gate.asStateFlow()

    /** False for the launch picker (Back there must not skip a PIN), true when opened from the top bar. */
    private val _pickerDismissable = MutableStateFlow(false)
    val pickerDismissable: StateFlow<Boolean> = _pickerDismissable.asStateFlow()

    val active: StateFlow<Profile?> = prefs.profile.stateIn(scope, SharingStarted.Eagerly, null)
    val profiles: StateFlow<List<Profile>> = store.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val guestEnabled: StateFlow<Boolean> = security.guestEnabled.stateIn(scope, SharingStarted.Eagerly, false)
    val limits: StateFlow<ProfileLimits> = active.flatMapLatest { p -> p?.let { security.limits(it.id) } ?: flowOf(ProfileLimits()) }
        .stateIn(scope, SharingStarted.Eagerly, ProfileLimits())

    /** Minutes left when at most 5 are (PRF-14 warning), else null. */
    private val _warning = MutableStateFlow<Int?>(null)
    val warning: StateFlow<Int?> = _warning.asStateFlow()

    val restrictions: Restrictions get() = Restrictions.of(active.value)

    @Volatile private var highWater = 0L
    @Volatile private var lastTouch = clock()
    @Volatile private var ownerVerifiedAt = 0L

    init {
        scope.launch { active.collect { Recording.enabled = Restrictions.of(it).savesHistory } }   // Guest saves nothing
        scope.launch { boot() }
    }

    /** Guest never survives a restart; then the picker shows when there is a choice, else Home opens. */
    private suspend fun boot() {
        highWater = security.highWater()
        val now = store.profiles.first()
        now.filter { it.kind == ProfileKind.GUEST }.forEach { removeGuest(it.id) }
        val list = store.profiles.first()
        val ask = security.askOnLaunch.first()
        val lastHasPin = active.first { it != null }?.pinHash != null && list.size >= 2
        val picker = PickerRule.needed(list.size, security.guestEnabled.first(), ask) || lastHasPin
        if (picker) _gate.value = Gate.Picker else open()
    }

    // ---- picker and switching ----

    fun openPicker() { _pickerDismissable.value = true; _gate.value = Gate.Picker }

    /** Back from the picker returns to what was there (only once a profile is in use). */
    suspend fun closePicker() { if (active.value != null) open() }

    /** What [target] needs before it opens; the owner's PIN also gates leaving a KIDS profile. SPEC: PRF-05, PRF-10 */
    suspend fun requirements(target: Profile): List<Requirement> {
        val current = active.value
        val out = mutableListOf<Requirement>()
        if (current != null && current.kind == ProfileKind.KIDS && target.kind != ProfileKind.KIDS) {
            out += if (ownerProfile()?.pinHash != null) Requirement.OWNER_PIN else Requirement.HOLD_OK
        }
        val ownerPinAlreadyAsked = Requirement.OWNER_PIN in out && target.id == ownerProfile()?.id
        if (target.pinHash != null && !ownerPinAlreadyAsked) out += Requirement.TARGET_PIN
        return out
    }

    suspend fun ownerProfile(): Profile? = profiles.value.firstOrNull() ?: store.profiles.first().firstOrNull()

    /** Checks a PIN against [profileId]'s hash with the five-tries lockout. SPEC: PRF-05 */
    suspend fun checkPin(profileId: String, pin: String): PinCheck {
        val now = guardedNow()
        val state = security.lockout(profileId)
        if (PinLockout.isLocked(state, now)) return PinCheck.Locked(PinLockout.remainingMs(state, now))
        val hash = store.profiles.first().firstOrNull { it.id == profileId }?.pinHash
        if (PinHasher.verify(pin, hash)) {
            security.setLockout(profileId, PinLockout.onSuccess())
            return PinCheck.Ok
        }
        val next = PinLockout.onFailure(state, now)
        security.setLockout(profileId, next)
        return if (PinLockout.isLocked(next, now)) PinCheck.Locked(PinLockout.remainingMs(next, now))
        else PinCheck.Wrong(PinLockout.triesLeft(next, now))
    }

    suspend fun lockRemainingMs(profileId: String): Long = PinLockout.remainingMs(security.lockout(profileId), guardedNow())

    /** Makes [id] the active profile and opens Home. A Guest in use is cleared on the way out. SPEC: PRF-06, PRF-11 */
    suspend fun switchTo(id: String) {
        val leaving = active.value
        if (!store.setActive(id)) return
        if (leaving != null && leaving.kind == ProfileKind.GUEST && leaving.id != id) removeGuest(leaving.id)
        lastTouch = clock()
        _warning.value = null
        open()
    }

    /** The Guest profile: nothing saved, level KIDS or FAMILY as the owner chose, cleared on leaving. SPEC: PRF-11 */
    suspend fun startGuest() {
        val level = security.guestLevel.first()
        val guest = store.add(Profile(kind = ProfileKind.GUEST, name = GUEST_NAME, onboarded = true, contentLevel = level,
            region = ownerProfile()?.region ?: Profile().region, avatarColor = GUEST_COLOR))
        if (guest == null) { Logger.w("guest profile not created (limit reached)"); return }
        switchTo(guest.id)
    }

    private suspend fun removeGuest(id: String) {
        store.delete(id)
        security.forget(id)
    }

    // ---- time limits and bedtime ----

    /** Key presses keep a Guest alive (4 h idle clears it, PRF-11). */
    fun touch() { lastTouch = clock() }

    /** Once a minute while Shamash is in front: counts the minute, ends an idle Guest, re-checks the lock. */
    suspend fun tick() {
        val p = active.value ?: return
        val now = guardedNow().also { if (it > highWater) { highWater = it; security.setHighWater(it) } }
        if (p.kind == ProfileKind.GUEST && clock() - lastTouch > GUEST_IDLE_MS) {
            val owner = ownerProfile() ?: return
            switchTo(owner.id); if (profiles.value.size >= 2 || guestEnabled.value) openPicker()
            return
        }
        if (_gate.value is Gate.Open || _gate.value is Gate.Locked) {
            if (_gate.value is Gate.Open) addMinute(p, now)
            evaluate(p, now)
        }
    }

    /** Re-checks the lock now (a profile switch, the screen coming back on). */
    suspend fun recheck() {
        val p = active.value ?: return
        if (_gate.value is Gate.Open || _gate.value is Gate.Locked) evaluate(p, guardedNow())
    }

    /** The owner's PIN lifts a lock until the bedtime window ends, or the day does. SPEC: PRF-14, PRF-15 */
    suspend fun unlockWithOwnerPin(pin: String): PinCheck {
        val owner = ownerProfile() ?: return PinCheck.Wrong(0)
        val check = checkPin(owner.id, pin)
        if (check is PinCheck.Ok) grantOverride()
        return check
    }

    /** With no owner PIN set the lock lifts after the confirmation hold of OK. */
    suspend fun unlockByHold() { if (ownerProfile()?.pinHash == null) grantOverride() }

    private suspend fun grantOverride() {
        val p = active.value ?: return
        val now = guardedNow()
        val until = LockPolicy.overrideEnd(limits.value, local(now)).atZone(zone()).toInstant().toEpochMilli()
        security.setOverrideUntil(p.id, until)
        _warning.value = null
        evaluate(p, now)
    }

    private suspend fun open() { _gate.value = Gate.Open; recheck() }

    private suspend fun evaluate(p: Profile, now: Long) {
        val l = security.limits(p.id).first()
        val at = local(now)
        val overrideMs = security.overrideUntil(p.id)
        val overrideUntil = if (overrideMs > 0L) LocalDateTime.ofInstant(Instant.ofEpochMilli(overrideMs), zone()) else null
        val state = LockPolicy.evaluate(l, usedToday(p.id, at), at, overrideUntil)
        _warning.value = (state as? LockState.Warning)?.minutesLeft
        when (state) {
            is LockState.Bedtime, LockState.DailyLimit -> _gate.value = Gate.Locked(state)
            else -> if (_gate.value is Gate.Locked) _gate.value = Gate.Open
        }
    }

    private suspend fun usedToday(id: String, at: LocalDateTime): Int {
        val u = security.usage(id)
        return if (u.day == at.toLocalDate().toString()) u.minutes else 0
    }

    private suspend fun addMinute(p: Profile, now: Long) {
        val at = local(now)
        security.setUsage(p.id, Usage(at.toLocalDate().toString(), usedToday(p.id, at) + 1))
    }

    private fun local(epochMs: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone())

    /** The TV clock, but never earlier than the latest time seen (a clock set back does not end a lock). SPEC: PRF-15 */
    private fun guardedNow(): Long = maxOf(clock(), highWater)

    // ---- owner PIN window for editing (PRF-16) ----

    fun ownerRecentlyVerified(): Boolean = clock() - ownerVerifiedAt < OWNER_WINDOW_MS

    suspend fun verifyOwner(pin: String): PinCheck {
        val owner = ownerProfile() ?: return PinCheck.Wrong(0)
        return checkPin(owner.id, pin).also { if (it is PinCheck.Ok) ownerVerifiedAt = clock() }
    }

    fun markOwnerVerified() { ownerVerifiedAt = clock() }

    // ---- what the Opener asks ----

    override fun allowsApp(packageName: String): Boolean {
        val p = active.value ?: return true
        return AppAccess.allows(p, limits.value, packageName, ownPackage)
    }

    override fun recordsHistory(): Boolean = restrictions.savesHistory

    /** The level the active profile browses at (for callers that only need it). */
    val level: ContentLevel get() = active.value?.contentLevel ?: ContentLevel.ALL

    companion object {
        const val USAGE_MEASURED = false
        const val GUEST_IDLE_MS = 4 * 3_600_000L
        const val HOLD_MS = 3_000L
        private const val OWNER_WINDOW_MS = 5 * 60_000L
        private const val GUEST_NAME = "Guest"
        private const val GUEST_COLOR = 0xFF8A9BB0.toInt()
    }
}
