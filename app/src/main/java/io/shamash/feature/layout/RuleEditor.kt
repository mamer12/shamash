package io.shamash.feature.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.Layout
import io.shamash.domain.MomentRule
import io.shamash.domain.MomentRules
import io.shamash.domain.RuleError
import io.shamash.domain.Shelf
import io.shamash.feature.settings.Explain
import io.shamash.ui.ChipPicker
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import io.shamash.ui.shelfTitle

private const val STEP_MIN = 30
private const val DAY_MIN = 24 * 60

/** Rules by D-pad: day chips, two steppers, the row to lift; invalid rules are refused with a message. SPEC: LAY-10..13 */
@Composable
internal fun RuleEditor(rows: List<Shelf>, layout: Layout, onChange: (Layout) -> Unit) {
    Explain(stringResource(R.string.layout_rules_text))
    layout.rules.forEach { rule ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(summary(rule), color = Palette.Ink, fontSize = 18.sp, modifier = Modifier.width(560.dp))
            OutlinedButton(onClick = { onChange(layout.copy(rules = layout.rules.filter { it.id != rule.id })) }) {
                Text(stringResource(R.string.rule_delete))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    NewRule(rows, layout, onChange)
}

@Composable
private fun summary(rule: MomentRule): String {
    val days = rule.days.sorted().map { stringResource(dayRes(it)) }.joinToString(" ")
    return stringResource(
        R.string.rule_summary, days, MomentRules.format(rule.fromMin), MomentRules.format(rule.toMin), rule.rowId,
    )
}

@Composable
private fun NewRule(rows: List<Shelf>, layout: Layout, onChange: (Layout) -> Unit) {
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var from by remember { mutableStateOf(18 * 60) }
    var to by remember { mutableStateOf(23 * 60) }
    var rowId by remember { mutableStateOf(rows.firstOrNull()?.id.orEmpty()) }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    ChipPicker((1..7).toList(), days, { days = it }) { stringResource(dayRes(it)) }
    Stepper(R.string.rule_from, from) { from = it }
    Stepper(R.string.rule_to, to) { to = it }
    Text(stringResource(R.string.rule_row), color = Palette.Dim, fontSize = 18.sp)
    OnePicker(rows.map { it.id }, rowId, { rowId = it }) { id ->
        rows.first { it.id == id }.let { it.title ?: shelfTitle(it.titleKey, it.titleArg) }
    }
    TextInput(stringResource(R.string.rule_label), label, { label = it.take(40) })
    error?.let { Text(stringResource(it), color = Palette.Live, fontSize = 18.sp) }
    Button(onClick = {
        val rule = MomentRule(
            id = "r${System.nanoTime()}", days = days, fromMin = from, toMin = to, rowId = rowId,
            label = label.trim().ifEmpty { null },
        )
        val added = MomentRules.add(layout.rules, rule)
        if (added != null) { onChange(layout.copy(rules = added)); error = null; days = emptySet(); label = "" }
        else error = errorText(rule, layout)
    }) { Text(stringResource(R.string.rule_add)) }
}

private fun errorText(rule: MomentRule, layout: Layout): Int = when (MomentRules.validate(rule)) {
    RuleError.NO_DAYS -> R.string.rule_err_days
    RuleError.SAME_TIME, RuleError.BAD_TIME -> R.string.rule_err_time
    RuleError.NO_ROW -> R.string.rule_err_row
    null -> if (layout.rules.size >= MomentRules.MAX) R.string.rule_err_full else R.string.rule_err_row
}

@Composable
private fun Stepper(label: Int, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), color = Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(80.dp))
        OutlinedButton(onClick = { onChange((minutes - STEP_MIN + DAY_MIN) % DAY_MIN) }) { Text("-") }
        Text(MomentRules.format(minutes), color = Palette.Ink, fontSize = 22.sp, modifier = Modifier.width(80.dp))
        OutlinedButton(onClick = { onChange((minutes + STEP_MIN) % DAY_MIN) }) { Text("+") }
    }
}

internal fun dayRes(iso: Int): Int = when (iso) {
    1 -> R.string.day_1
    2 -> R.string.day_2
    3 -> R.string.day_3
    4 -> R.string.day_4
    5 -> R.string.day_5
    6 -> R.string.day_6
    else -> R.string.day_7
}
