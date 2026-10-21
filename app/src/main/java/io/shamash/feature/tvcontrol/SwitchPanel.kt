package io.shamash.feature.tvcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.Palette

/** A [SwitchModel] bound to this TV, kept for the composition. */
@Composable
fun rememberSwitchModel(): SwitchModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { SwitchModel(TvControl.get(context).asModeSwitcher(), scope) }
    LaunchedEffect(model) { model.refresh() }
    return model
}

/**
 * The home-screen switch: where the TV is, the buttons, the live progress and what changed. Used by setup's last step
 * and Settings › Home screen. SPEC: TVC-05, TVC-08
 */
@Composable
fun SwitchPanel(model: SwitchModel, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    val state by model.state.collectAsState()
    Column(modifier) {
        Text(stringResource(modeText(state)), color = Palette.Ink, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val busy = state is SwitchState.Running
            Button(onClick = model::switch, enabled = !busy) { Text(stringResource(R.string.settings_switch)) }
            OutlinedButton(onClick = model::restore, enabled = !busy) { Text(stringResource(R.string.settings_original)) }
            extra()
        }
        Spacer(Modifier.height(24.dp))
        when (val s = state) {
            is SwitchState.Running -> Text(stepText(s.step), color = Palette.Gold, fontSize = 20.sp)
            is SwitchState.Finished -> resultLines(s.result).forEach {
                Text(it, color = Palette.Dim, fontSize = 18.sp, modifier = Modifier.width(900.dp))
                Spacer(Modifier.height(6.dp))
            }
            is SwitchState.Idle -> Unit
        }
    }
}

private fun modeText(state: SwitchState): Int {
    val shamash = when (state) {
        is SwitchState.Idle -> state.shamashMode
        is SwitchState.Finished -> state.shamashMode
        is SwitchState.Running -> return R.string.phase_connecting
    }
    return if (shamash) R.string.settings_mode_shamash else R.string.settings_mode_original
}

@Composable
private fun stepText(step: Step): String = when (step.phase) {
    Phase.TURNING_OFF_APPS -> stringResource(R.string.phase_apps_off, step.done, step.total)
    Phase.TURNING_ON_APPS -> stringResource(R.string.phase_apps_on, step.done, step.total)
    else -> stringResource(SwitchText.phase(step.phase))
}

@Composable
private fun resultLines(result: SwitchResult): List<String> = when (result) {
    is SwitchResult.Switched -> listOfNotNull(
        stringResource(R.string.result_switched, result.turnedOff.size),
        result.notTurnedOff.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.result_not_off, it.joinToString()) },
        result.keptOnForWifi.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.result_wifi_kept, it.joinToString()) },
    )
    is SwitchResult.Restored -> listOfNotNull(
        stringResource(R.string.result_restored, result.turnedOn.size),
        result.stillOff.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.result_still_off, it.joinToString()) },
    )
    is SwitchResult.WifiOwned -> listOf(stringResource(R.string.result_wifi))
    is SwitchResult.Failed -> listOf(stringResource(SwitchText.failure(result.failure)))
}
