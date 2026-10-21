package io.shamash.feature.companion

import java.security.MessageDigest

/** Where the companion PIN lives: read it, or replace it after the third lockout. */
interface PinSource {
    fun current(): String
    fun rotate()
}

/**
 * The phone PIN check every request goes through, `/send` and the API alike: one [PinGuard] for all clients, so
 * five wrong PINs anywhere lock everything, even the right PIN, until the wait is over; the third lockout rotates
 * the PIN. A wrong PIN costs [wrongDelayMs] to slow guessing. SPEC: CMP-03, CMP-06
 */
class CompanionAuth(
    private val pins: PinSource,
    private val guard: PinGuard = PinGuard(),
    private val wrongDelayMs: Long = WRONG_PIN_DELAY_MS,
    private val pause: (Long) -> Unit = Thread::sleep,
) {
    enum class Verdict { OK, WRONG, LOCKED }

    fun check(given: String?): Verdict {
        // Authentication and rotation share one lock: concurrent workers cannot bypass a newly started lockout.
        val ok = synchronized(guard) {
            if (guard.lockedForMs() > 0) return Verdict.LOCKED
            if (given != null && same(given, pins.current())) {
                guard.succeeded()
                true
            } else {
                if (guard.failed() == PinGuard.Verdict.ROTATE) pins.rotate()
                false
            }
        }
        if (ok) return Verdict.OK
        if (wrongDelayMs > 0) pause(wrongDelayMs)
        return if (guard.lockedForMs() > 0) Verdict.LOCKED else Verdict.WRONG
    }

    private fun same(given: String, expected: String) =
        MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

    companion object {
        const val WRONG_PIN_DELAY_MS = 1_000L
    }
}
