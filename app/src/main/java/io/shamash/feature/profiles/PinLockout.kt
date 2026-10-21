package io.shamash.feature.profiles

/** Wrong PINs so far and until when the profile is locked (epoch ms). */
data class LockoutState(val failures: Int = 0, val lockedUntil: Long = 0L, val lockouts: Int = 0)

/*
 * Five wrong entries lock that profile for 5 minutes; each repeat doubles it (10, 20, ...) until a right PIN clears
 * the count. Pure: the clock comes in. SPEC: PRF-05
 */
object PinLockout {
    const val MAX_TRIES = 5
    const val BASE_MS = 5 * 60_000L
    private const val MAX_DOUBLINGS = 8

    fun remainingMs(state: LockoutState, now: Long): Long = (state.lockedUntil - now).coerceAtLeast(0L)

    fun isLocked(state: LockoutState, now: Long): Boolean = remainingMs(state, now) > 0L

    /** Tries left before a lock (0 while locked). */
    fun triesLeft(state: LockoutState, now: Long): Int = if (isLocked(state, now)) 0 else MAX_TRIES - state.failures

    fun onFailure(state: LockoutState, now: Long): LockoutState {
        if (isLocked(state, now)) return state
        val failures = state.failures + 1
        if (failures < MAX_TRIES) return state.copy(failures = failures)
        val span = BASE_MS shl state.lockouts.coerceAtMost(MAX_DOUBLINGS)
        return LockoutState(failures = 0, lockedUntil = now + span, lockouts = state.lockouts + 1)
    }

    fun onSuccess(): LockoutState = LockoutState()

    fun encode(s: LockoutState) = "${s.failures},${s.lockedUntil},${s.lockouts}"

    fun decode(text: String?): LockoutState {
        val p = text?.split(',')?.mapNotNull { it.toLongOrNull() } ?: return LockoutState()
        return if (p.size == 3) LockoutState(p[0].toInt(), p[1], p[2].toInt()) else LockoutState()
    }
}
