package io.shamash.feature.accessibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.Lite
import io.shamash.ui.LiteChoice
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette

/** Settings › Accessibility: text size, high contrast, reduce motion. Changes apply at once. SPEC: PLT-11, PLT-12, PLT-14 */
@Composable
fun A11yPanel() {
    val s = A11y.settings
    val now = A11y.effective
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier) {
        Text(stringResource(R.string.a11y_text_size), color = Palette.Dim, fontSize = 18.sp)
        OnePicker(TextScale.entries, s.textScale, { v -> A11y.update { it.copy(textScale = v) } }) { v ->
            stringResource(
                when (v) {
                    TextScale.NORMAL -> R.string.a11y_size_normal
                    TextScale.LARGE -> R.string.a11y_size_large
                    TextScale.LARGER -> R.string.a11y_size_larger
                },
            )
        }
        Toggle(R.string.a11y_high_contrast, now.highContrast) { v -> A11y.update { it.copy(highContrast = v) } }
        Toggle(R.string.a11y_reduce_motion, now.reduceMotion) { v -> A11y.update { it.copy(reduceMotion = v) } }
        Text(stringResource(R.string.a11y_follows_tv), color = Palette.Faint, fontSize = 15.sp)
        Text(stringResource(R.string.lite_drawing), color = Palette.Dim, fontSize = 18.sp)   // PERF-06
        OnePicker(LiteChoice.entries, Lite.choice, Lite::choose) { v ->
            stringResource(
                when (v) {
                    LiteChoice.AUTO -> R.string.lite_auto
                    LiteChoice.ON -> R.string.a11y_on
                    LiteChoice.OFF -> R.string.a11y_off
                },
            )
        }
    }
}

@Composable
private fun Toggle(label: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    OutlinedButton(onClick = { onChange(!on) }) {
        Text("${stringResource(label)}: ${stringResource(if (on) R.string.a11y_on else R.string.a11y_off)}")
    }
}
