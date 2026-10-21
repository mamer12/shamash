package io.shamash.data.backup

/**
 * Wrong passphrases: three tries, then a one-minute wait (CMP-15). In memory only; a restart clears it, which is
 * fine for a lockout meant to slow guessing through the UI. SPEC: CMP-15
 */
class BackupAttempts(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxTries: Int = 3,
    private val waitMs: Long = 60_000,
) {
    private var failures = 0
    private var lockedUntil = 0L

    @Synchronized fun waitLeftMs(): Long = (lockedUntil - clock()).coerceAtLeast(0)

    @Synchronized fun failed() {
        if (waitLeftMs() > 0) return
        failures++
        if (failures >= maxTries) { lockedUntil = clock() + waitMs; failures = 0 }
    }

    @Synchronized fun succeeded() { failures = 0; lockedUntil = 0 }
}
