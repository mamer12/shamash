package io.shamash.feature.matchcentre

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * A daily time range (it may cross midnight) in which overlays are held back, on the chosen weekdays (1 = Monday ..
 * 7 = Sunday; none chosen = every day). After midnight the day is the one the range started on. SPEC: MC-12
 */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
    val days: Set<Int> = emptySet(),
) {
    fun suppresses(now: Long, zone: ZoneId): Boolean {
        if (!enabled || startMinute == endMinute) return false
        val at = Instant.ofEpochMilli(now).atZone(zone)
        val minute = at.hour * 60 + at.minute
        val crosses = startMinute > endMinute
        val inside = if (crosses) minute >= startMinute || minute < endMinute else minute in startMinute until endMinute
        if (!inside) return false
        val startedYesterday = crosses && minute < endMinute
        val day = (if (startedYesterday) at.toLocalDate().minusDays(1) else at.toLocalDate()).dayOfWeek.value
        return days.isEmpty() || day in days
    }
}
