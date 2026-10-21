package io.shamash.feature.home

import io.shamash.domain.LayoutOps
import io.shamash.domain.MomentRule
import io.shamash.domain.MomentRules
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.TempUnit
import io.shamash.domain.Weather
import io.shamash.ui.WeatherNow
import java.util.Calendar
import kotlin.math.roundToInt

/** Home's rows after the profile's layout: [shown] are drawn, [editable] is every row in order (hidden ones too). */
data class Laid(val shown: List<Shelf>, val editable: List<Shelf>)

/** Applies the profile's layout and moment rules to the default rows (pure). SPEC: LAY-01..04, LAY-09..11 */
object HomeLayout {
    fun lay(rows: List<Shelf>, p: Profile, rule: MomentRule?): Laid {
        val ids = rows.map { it.id }
        val byId = rows.associateBy { it.id }
        val placed = LayoutOps.arrange(ids, p.layout)
        val editable = placed.map { pl -> named(byId.getValue(pl.id), pl.pref?.title) }
        val shownIds = LayoutOps.emphasize(placed.filterNot { it.hidden }.map { it.id }, rule?.rowId)
        val shown = shownIds.map { id ->
            val shelf = named(byId.getValue(id), LayoutOps.prefFor(id, p.layout.rows)?.title)
            if (rule != null && rule.rowId == id && !rule.label.isNullOrBlank()) shelf.copy(title = rule.label) else shelf
        }
        return Laid(shown, editable)
    }

    private fun named(s: Shelf, title: String?) = if (title == null) s else s.copy(title = title)

    /** The rule that applies at [now] on the TV's clock, if any. SPEC: LAY-10 */
    fun ruleAt(p: Profile, now: Long): MomentRule? {
        if (p.layout.rules.isEmpty()) return null
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return MomentRules.firstMatch(p.layout.rules, MomentRules.isoDay(c.get(Calendar.DAY_OF_WEEK)), minute)
    }

    /** Degrees in the chosen unit; automatic means Fahrenheit in the US only. SPEC: LAY-16 */
    fun weather(w: Weather, p: Profile): WeatherNow? {
        if (!p.layout.look.showWeather) return null
        val fahrenheit = when (p.layout.look.temp) {
            TempUnit.F -> true
            TempUnit.C -> false
            TempUnit.AUTO -> p.region.equals("US", ignoreCase = true)
        }
        val degrees = if (fahrenheit) w.temperatureC * 9 / 5 + 32 else w.temperatureC
        return WeatherNow(degrees.roundToInt(), w.code, w.isDay)
    }
}
