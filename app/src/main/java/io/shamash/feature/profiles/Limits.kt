package io.shamash.feature.profiles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/** A bedtime window in minutes of the day; `start > end` runs past midnight (21:00 to 06:30). */
@Serializable
data class Window(val startMin: Int, val endMin: Int) {
    val crossesMidnight get() = startMin > endMin
}

/**
 * What a profile may do and when: [dailyMinutes] (0 = none), [bedtime] per ISO day of week (1 = Monday) and, for
 * KIDS and FAMILY, the apps it may open ([allowedApps] null = the owner has not picked yet). SPEC: PRF-13..15
 */
@Serializable
data class ProfileLimits(
    val dailyMinutes: Int = 0,
    val bedtime: Map<Int, Window> = emptyMap(),
    val allowedApps: Set<String>? = null,
) {
    val isEmpty get() = dailyMinutes == 0 && bedtime.isEmpty()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(l: ProfileLimits): String = json.encodeToString(serializer(), l)

        /** A corrupt value reads as no limits restricted beyond defaults (and is never thrown). */
        fun decode(text: String?): ProfileLimits =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: ProfileLimits()
    }
}

/** Bedtime windows, across midnight too. Pure. SPEC: PRF-15 */
object Bedtime {
    fun isLocked(limits: ProfileLimits, at: LocalDateTime): Boolean = windowEnd(limits, at) != null

    /** When the window [at] is inside ends, or null when it is not in one. */
    fun windowEnd(limits: ProfileLimits, at: LocalDateTime): LocalDateTime? {
        val minute = at.hour * 60 + at.minute
        val today = limits.bedtime[at.dayOfWeek.value]
        if (today != null) {
            val end = at.toLocalDate().atStartOfDay()
            when {
                !today.crossesMidnight && minute >= today.startMin && minute < today.endMin ->
                    return end.plusMinutes(today.endMin.toLong())
                today.crossesMidnight && minute >= today.startMin ->
                    return end.plusDays(1).plusMinutes(today.endMin.toLong())
            }
        }
        val yesterday = limits.bedtime[at.toLocalDate().minusDays(1).dayOfWeek.value]
        if (yesterday != null && yesterday.crossesMidnight && minute < yesterday.endMin) {
            return at.toLocalDate().atStartOfDay().plusMinutes(yesterday.endMin.toLong())
        }
        return null
    }
}

/** Where a profile stands against its limits right now. */
sealed interface LockState {
    data object Open : LockState
    data class Warning(val minutesLeft: Int) : LockState
    data class Bedtime(val endsAt: LocalDateTime) : LockState
    data object DailyLimit : LockState
}

/** Combines bedtime and the daily budget; an owner unlock (an override until [overrideUntil]) wins. SPEC: PRF-14, PRF-15 */
object LockPolicy {
    const val WARN_MINUTES = 5

    fun evaluate(
        limits: ProfileLimits,
        usedMinutes: Int,
        at: LocalDateTime,
        overrideUntil: LocalDateTime? = null,
    ): LockState {
        if (overrideUntil != null && at.isBefore(overrideUntil)) return LockState.Open
        Bedtime.windowEnd(limits, at)?.let { return LockState.Bedtime(it) }
        if (limits.dailyMinutes > 0) {
            val left = limits.dailyMinutes - usedMinutes
            if (left <= 0) return LockState.DailyLimit
            if (left <= WARN_MINUTES) return LockState.Warning(left)
        }
        return LockState.Open
    }

    /** How long an owner unlock lasts: the bedtime window's end, else the end of the day. */
    fun overrideEnd(limits: ProfileLimits, at: LocalDateTime): LocalDateTime =
        Bedtime.windowEnd(limits, at) ?: at.toLocalDate().plusDays(1).atStartOfDay()
}
