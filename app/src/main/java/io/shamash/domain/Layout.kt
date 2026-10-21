package io.shamash.domain

import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * Layout logic, pure: row ordering and edits (LAY-01..06, LAY-09), moment rules (LAY-10..13), the look (LAY-14..17).
 * Public surface: LayoutOps, MomentRule, MomentRules, Look, Accents, Digits. SPEC: LAY-01..17
 */

/** One row after ordering: its id, its saved pref (if any) and whether it is hidden. */
data class Placed(val id: String, val pref: RowPref?, val hidden: Boolean) {
    val pinned get() = pref?.pinned == true
}

object LayoutOps {
    const val TITLE_MAX = 40

    /** The pref for [rowId]: the exact one, else a `prefix*` one (presets name a family of rows). SPEC: LAY-09 */
    fun prefFor(rowId: String, prefs: List<RowPref>): RowPref? =
        prefs.firstOrNull { it.rowId == rowId }
            ?: prefs.firstOrNull { it.rowId.endsWith("*") && rowId.startsWith(it.rowId.dropLast(1)) }

    /**
     * Every row of [ids] (the default HOME-04 order) in the layout's order: pinned first, then the others by `order`,
     * then rows with no pref in their default order. Saved prefs for ids not in [ids] are ignored. SPEC: LAY-03
     */
    fun arrange(ids: List<String>, layout: Layout): List<Placed> {
        val placed = ids.map { id ->
            val pref = prefFor(id, layout.rows)
            Placed(id, pref, hidden = pref?.hidden ?: layout.hideRest)
        }
        val index = ids.withIndex().associate { it.value to it.index }
        return placed.sortedWith(
            compareBy<Placed>(
                { if (it.pref == null) 2 else if (it.pinned) 0 else 1 },
                { it.pref?.order ?: 0 },
                { index.getValue(it.id) },
            ),
        )
    }

    /** The rows to draw, in order. SPEC: LAY-03, LAY-04 */
    fun visible(ids: List<String>, layout: Layout): List<String> =
        arrange(ids, layout).filterNot { it.hidden }.map { it.id }

    /** Writes the current order and effective flags as exact prefs (unknown prefs are kept). */
    fun materialize(ids: List<String>, layout: Layout): Layout = write(arrange(ids, layout), layout)

    private fun write(placed: List<Placed>, layout: Layout): Layout {
        val exact = placed.mapIndexed { i, p ->
            RowPref(p.id, p.hidden, p.pinned, p.pref?.title, i)
        }
        val ids = placed.map { it.id }.toSet()
        val kept = layout.rows.filter { it.rowId !in ids }
        return layout.copy(rows = exact + kept)
    }

    private fun edit(ids: List<String>, layout: Layout, id: String, change: (RowPref) -> RowPref): Layout {
        if (id !in ids) return layout
        val base = materialize(ids, layout)
        val edited = base.copy(rows = base.rows.map { if (it.rowId == id) change(it) else it })
        return materialize(ids, edited)
    }

    fun setHidden(ids: List<String>, layout: Layout, id: String, hidden: Boolean) =
        edit(ids, layout, id) { it.copy(hidden = hidden) }

    /** Pinning puts the row first among the pinned. SPEC: LAY-02 */
    fun setPinned(ids: List<String>, layout: Layout, id: String, pinned: Boolean) =
        edit(ids, layout, id) { it.copy(pinned = pinned, order = if (pinned) -1 else it.order + 1) }

    /** User text, at most [TITLE_MAX] characters; empty restores the default. SPEC: LAY-02, LAY-06 */
    fun rename(ids: List<String>, layout: Layout, id: String, text: String) =
        edit(ids, layout, id) { it.copy(title = text.trim().take(TITLE_MAX).ifEmpty { null }) }

    /** One step up (-1) or down (+1) among rows with the same pin state; no change at the edges. SPEC: LAY-02 */
    fun move(ids: List<String>, layout: Layout, id: String, delta: Int): Layout {
        val rows = arrange(ids, layout).toMutableList()
        val from = rows.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in rows.indices || rows[from].pinned != rows[to].pinned) return layout
        val moved = rows.removeAt(from)
        rows.add(to, moved)
        return write(rows, layout)
    }

    /** The preset's order, names and visibility again; keys and subscriptions are not in a layout. SPEC: LAY-05 */
    fun reset(layout: Layout): Layout {
        return LayoutPresets.apply(LayoutPresets.byId(layout.presetId), layout)
    }

    /** [rowId] moved to the top of the drawn rows. SPEC: LAY-11 */
    fun emphasize(visible: List<String>, rowId: String?): List<String> =
        if (rowId == null || rowId !in visible) visible else listOf(rowId) + (visible - rowId)
}

/** A user rule: on [days] (ISO 1 = Monday … 7 = Sunday) between two times, lift [rowId]. SPEC: LAY-10 */
@Serializable
data class MomentRule(
    val id: String,
    val days: Set<Int>,
    val fromMin: Int,                      // minutes after midnight
    val toMin: Int,
    val rowId: String,
    val label: String? = null,
    val mix: List<String> = emptyList(),   // carousel order for this time (LAY-12); empty = the profile's
)

enum class RuleError { NO_DAYS, SAME_TIME, BAD_TIME, NO_ROW }

object MomentRules {
    const val MAX = 8
    private const val DAY_MIN = 24 * 60

    /** Why a rule cannot be saved, or null. SPEC: LAY-13 */
    fun validate(r: MomentRule): RuleError? = when {
        r.days.isEmpty() || r.days.any { it !in 1..7 } -> RuleError.NO_DAYS
        r.fromMin !in 0 until DAY_MIN || r.toMin !in 0 until DAY_MIN -> RuleError.BAD_TIME
        r.fromMin == r.toMin -> RuleError.SAME_TIME
        r.rowId.isBlank() -> RuleError.NO_ROW
        else -> null
    }

    /** True when [minute] of ISO [day] is inside the rule; a range may cross midnight. SPEC: LAY-10 */
    fun matches(r: MomentRule, day: Int, minute: Int): Boolean {
        if (validate(r) != null) return false
        if (r.fromMin < r.toMin) return day in r.days && minute >= r.fromMin && minute < r.toMin
        val yesterday = if (day == 1) 7 else day - 1
        return (day in r.days && minute >= r.fromMin) || (yesterday in r.days && minute < r.toMin)
    }

    /** The first matching rule in list order. SPEC: LAY-10 */
    fun firstMatch(rules: List<MomentRule>, day: Int, minute: Int): MomentRule? =
        rules.firstOrNull { matches(it, day, minute) }

    /** [rules] plus [r]; null when it is invalid or the list is full. SPEC: LAY-11, LAY-13 */
    fun add(rules: List<MomentRule>, r: MomentRule): List<MomentRule>? =
        if (validate(r) != null || rules.size >= MAX) null else rules + r

    /** java.util.Calendar's day of week (Sunday = 1) as ISO (Monday = 1). */
    fun isoDay(calendarDay: Int): Int = (calendarDay + 5) % 7 + 1

    fun format(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    fun parse(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }
}

enum class ClockFormat { FOLLOW, H12, H24 }
enum class DateStyle { WEEKDAY, NUMERIC }
enum class TempUnit { AUTO, C, F }

/** The look of Home beyond rows. SPEC: LAY-14, LAY-16, LAY-17 */
@Serializable
data class Look(
    val accent: Int = Accents.GOLD,           // ARGB
    val clock: ClockFormat = ClockFormat.FOLLOW,
    val date: DateStyle = DateStyle.WEEKDAY,
    val temp: TempUnit = TempUnit.AUTO,
    val showWeather: Boolean = true,
    val arabicLayout: Boolean = false,        // mirror Home even when the TV is not in Arabic
    val arabicDigits: Boolean = false,        // Arabic-Indic digits; Western by default
)

val CardSize.factor: Float get() = when (this) { CardSize.S -> 0.85f; CardSize.M -> 1f; CardSize.L -> 1.2f }

val Profile.momentRules: List<MomentRule> get() = layout.rules

object Accents {
    val GOLD = 0xFFF4C450.toInt()
    /** Eight accents, all readable as text on the dark background. */
    val ALL = listOf(
        GOLD, 0xFF5AB0FF.toInt(), 0xFF4ADE9C.toInt(), 0xFFFF7A6B.toInt(),
        0xFFB48CFF.toInt(), 0xFFFF6FA8.toInt(), 0xFFFF9F45.toInt(), 0xFF2DD4D4.toInt(),
    )
    const val MIN_CONTRAST = 4.5
    private val NIGHT = 0xFF0A0C1E.toInt()
    private val INK = 0xFFF5F0E6.toInt()

    fun luminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Text on top of [accent]: the dark or the light ink, whichever reads better (always >= 4.5:1). SPEC: LAY-14 */
    fun textOn(accent: Int): Int = if (contrast(accent, NIGHT) >= contrast(accent, INK)) NIGHT else INK

    /** [accent] used as text on [background]: lightened until it reads at 4.5:1 (never darker). SPEC: LAY-14 */
    fun readable(accent: Int, background: Int = NIGHT): Int {
        var c = accent
        var step = 0
        while (contrast(c, background) < MIN_CONTRAST && step < 20) {
            val r = ((c shr 16) and 0xFF) + (255 - ((c shr 16) and 0xFF)) / 5
            val g = ((c shr 8) and 0xFF) + (255 - ((c shr 8) and 0xFF)) / 5
            val b = (c and 0xFF) + (255 - (c and 0xFF)) / 5
            c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            step++
        }
        return c
    }
}

object Digits {
    private const val ARABIC_ZERO = '٠'

    private const val PERSIAN_ZERO = '۰'

    /**
     * All digits to Arabic-Indic when [arabicIndic], else all to Western: the TV's own formatters (an Arabic locale's
     * clock and `%d`) bring Arabic-Indic digits that must not leak into a Western-digits Home. SPEC: LAY-17
     */
    fun localize(text: String, arabicIndic: Boolean): String = text.map { c ->
        val value = when (c) {
            in '0'..'9' -> c - '0'
            in ARABIC_ZERO..ARABIC_ZERO + 9 -> c - ARABIC_ZERO
            in PERSIAN_ZERO..PERSIAN_ZERO + 9 -> c - PERSIAN_ZERO
            else -> -1
        }
        when {
            value < 0 -> c
            arabicIndic -> ARABIC_ZERO + value
            else -> '0' + value
        }
    }.joinToString("")

    /** Visual left-to-right order of [items] given the layout direction (RTL puts the first item at the right). SPEC: LAY-17 */
    fun <T> visualOrder(items: List<T>, rtl: Boolean): List<T> = if (rtl) items.reversed() else items

    /** Whether Home mirrors: the TV language is Arabic or the user forces it. SPEC: LAY-17 */
    fun mirrored(language: String, look: Look): Boolean = look.arabicLayout || language.equals("ar", ignoreCase = true)
}
