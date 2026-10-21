package io.shamash.data

/**
 * Whether what the person watches and rates may be saved now. Off while a Guest profile is active: Guest leaves
 * nothing behind (no history, progress, ratings or lists). Set by the profile session. SPEC: PRF-11
 */
object Recording {
    @Volatile var enabled = true
}
